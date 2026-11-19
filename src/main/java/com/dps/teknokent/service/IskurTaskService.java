package com.dps.teknokent.service;

import java.text.Collator;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.IskurTaskStatus;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.IskurWeeklySplit;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.dto.requests.IskurTaskRequest;
import com.dps.teknokent.dto.requests.IskurTaskUpdateRequest;
import com.dps.teknokent.dto.responses.IskurTaskResponse;
import com.dps.teknokent.dto.responses.IskurTeamTasksResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTask;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTaskRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.MeetingSlotTimes;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

/**
 * Team tasks (Figma 2.1 "Ekibim / görev atama" and the proposed "Görev detayı"): a leader assigns work to their own
 * sub-group or to themselves, the assignee records the actual time and the output, then completes the task or hands it
 * over to next week. A leader sees the whole team's tasks; a member sees only their own.
 */
@Service
public class IskurTaskService {

	/** A task is due within the next eight weeks: the trial weeks plus a margin. */
	static final int MAX_DAYS_AHEAD = 56;
	private static final Pattern HTTPS_LINK = Pattern.compile("^https://\\S+$");
	private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final Locale TR = Locale.forLanguageTag("tr");

	private final IskurInternService interns;
	private final IskurPlacementRepository placements;
	private final IskurTeamRepository teams;
	private final IskurUnitRepository units;
	private final IskurTaskRepository tasks;
	private final AppUserRepository users;
	private final ProgramRepository programs;
	private final NotificationService notifications;
	private final IskurOnboardingService onboarding;
	private final Clock clock;

	@Autowired
	public IskurTaskService(IskurInternService interns, IskurPlacementRepository placements,
			IskurTeamRepository teams, IskurUnitRepository units, IskurTaskRepository tasks, AppUserRepository users,
			ProgramRepository programs, NotificationService notifications, IskurOnboardingService onboarding) {
		this(interns, placements, teams, units, tasks, users, programs, notifications, onboarding,
				Clock.systemUTC());
	}

	IskurTaskService(IskurInternService interns, IskurPlacementRepository placements, IskurTeamRepository teams,
			IskurUnitRepository units, IskurTaskRepository tasks, AppUserRepository users, ProgramRepository programs,
			NotificationService notifications, IskurOnboardingService onboarding, Clock clock) {
		this.interns = interns;
		this.placements = placements;
		this.teams = teams;
		this.units = units;
		this.tasks = tasks;
		this.users = users;
		this.programs = programs;
		this.notifications = notifications;
		this.onboarding = onboarding;
		this.clock = clock;
	}

	/** The caller's team and the week's tasks; {@code weekStart} null means this week. */
	@Transactional(readOnly = true)
	public IskurTeamTasksResponse teamTasks(UUID actorId, @Nullable LocalDate weekStart) {
		IskurPlacement me = placementOf(actorId);
		return build(me, weekStart == null ? mondayOf(today()) : requireMonday(weekStart));
	}

	/** "Görevi ata": only a leader, only to themselves or their own sub-group; the assignee is notified. */
	@Transactional
	public IskurTeamTasksResponse create(UUID actorId, IskurTaskRequest request) {
		IskurPlacement me = placementOf(actorId);
		if (me.getTeamRole() != IskurTeamRole.LEADER) {
			throw forbidden("error.iskurLeadersOnly");
		}
		requireOpenProgram(me.getProgramId());
		IskurPlacement assignee = placements.findByProgramIdAndUserId(me.getProgramId(), request.assigneeId())
				.filter(placement -> placement.getTeamId().equals(me.getTeamId()))
				.filter(placement -> placement.getUserId().equals(me.getUserId())
						|| me.getUserId().equals(placement.getLeaderId()))
				.orElseThrow(() -> new InvalidRequestException(
						Texts.forRequest("error.iskurTaskAssigneeNotInSubgroup")));
		int estimate = request.estimatedMinutes();
		if (estimate % IskurWeeklySplit.STEP_MINUTES != 0) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurTaskEstimateStep"));
		}
		LocalDate today = today();
		if (request.dueDate().isBefore(today) || request.dueDate().isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurTaskDueDate", MAX_DAYS_AHEAD));
		}
		// Due on one of the assignee's work days, once they have given them (onboarding); before that, any day.
		Set<DayOfWeek> workDays = onboarding.workDays(me.getProgramId(), assignee.getUserId());
		if (!workDays.isEmpty() && !workDays.contains(request.dueDate().getDayOfWeek())) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurTaskNotWorkDay"));
		}

		IskurTask task = new IskurTask(me.getProgramId(), me.getTeamId(), assignee.getUserId(), actorId,
				clock.instant());
		task.setTitle(request.title().strip());
		task.setKind(request.kind());
		task.setEstimatedMinutes(estimate);
		task.setDueDate(request.dueDate());
		task.setNeedsOutput(request.needsOutput());
		task.setDescription(TextUtils.normalizeOptional(request.description()));
		tasks.save(task);
		if (!assignee.getUserId().equals(actorId)) {
			notifyAssigned(task, actorId);
		}
		return build(me, mondayOf(task.getDueDate()));
	}

	@Transactional(readOnly = true)
	public IskurTaskResponse task(UUID actorId, UUID taskId) {
		IskurTask task = requireTask(taskId);
		Access access = accessOf(actorId, task);
		return response(task, access, teamName(task.getTeamId()), names(Set.of(task.getAssigneeId()),
				task.getCreatedBy()));
	}

	/**
	 * The assignee's time and output are replaced as sent; {@code status} then completes the task (time required, and
	 * an output when the task asks for one), hands it over (reason required) or reopens it. A done task is locked until
	 * it is reopened, by its assignee (a completion by mistake) or a leader of the team; a leader may only reopen.
	 */
	@Transactional
	public IskurTaskResponse update(UUID actorId, UUID taskId, IskurTaskUpdateRequest request) {
		IskurTask task = requireTask(taskId);
		Access access = accessOf(actorId, task);
		requireOpenProgram(task.getProgramId());
		IskurTaskStatus target = request.status();
		if (!access.works() && target != IskurTaskStatus.OPEN) {
			throw forbidden("error.iskurTaskAssigneeOnly");
		}
		if (task.getStatus() == IskurTaskStatus.DONE && target != IskurTaskStatus.OPEN) {
			throw new ConflictException(Texts.forRequest("error.iskurTaskDoneLocked"));
		}
		Instant now = clock.instant();
		if (target == IskurTaskStatus.OPEN) {
			if (task.getStatus() != IskurTaskStatus.OPEN) {
				task.reopen(now);
			}
		} else if (access.works()) {
			task.setActualMinutes(request.actualMinutes());
			task.setOutputLink(link(request.outputLink()));
			task.setOutputNote(TextUtils.normalizeOptional(request.outputNote()));
			task.setUpdatedAt(now);
			if (target == IskurTaskStatus.DONE) {
				requireCompletable(task);
				task.complete(now);
			} else if (target == IskurTaskStatus.HANDED_OVER) {
				String reason = TextUtils.normalizeOptional(request.handoverReason());
				if (reason == null) {
					throw new InvalidRequestException(Texts.forRequest("error.iskurTaskHandoverReason"));
				}
				task.handOver(reason, now);
			}
		}
		IskurTask saved = savedOrConflict(() -> tasks.saveAndFlush(task));
		return response(saved, access, teamName(saved.getTeamId()), names(Set.of(saved.getAssigneeId()),
				saved.getCreatedBy()));
	}

	private static void requireCompletable(IskurTask task) {
		if (task.getActualMinutes() == null) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurTaskActualRequired"));
		}
		if (task.isNeedsOutput() && task.getOutputLink() == null && task.getOutputNote() == null) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurTaskOutputRequired"));
		}
	}

	/** A leader of the team takes a task back; the log lines that used it keep their own title and minutes. */
	@Transactional
	public IskurTeamTasksResponse delete(UUID actorId, UUID taskId) {
		IskurTask task = requireTask(taskId);
		Access access = accessOf(actorId, task);
		if (!access.manages()) {
			throw forbidden("error.iskurLeadersOnly");
		}
		requireOpenProgram(task.getProgramId());
		savedOrConflict(() -> {
			tasks.delete(task);
			tasks.flush();
			return task;
		});
		return build(access.placement(), mondayOf(task.getDueDate()));
	}

	private IskurTeamTasksResponse build(IskurPlacement me, LocalDate monday) {
		IskurTeam team = teams.findById(me.getTeamId())
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurTeamNotFound")));
		String unitName = units.findById(team.getUnitId()).map(unit -> unit.getName()).orElse("");
		List<IskurPlacement> teammates = placements.findByTeamId(team.getId());
		boolean leader = me.getTeamRole() == IskurTeamRole.LEADER;
		LocalDate sunday = monday.plusDays(6);
		List<IskurTask> weekTasks = leader
				? tasks.findByTeamIdAndDueDateBetweenOrderByDueDateAscCreatedAtAsc(team.getId(), monday, sunday)
				: tasks.findByAssigneeIdAndTeamIdAndDueDateBetweenOrderByDueDateAscCreatedAtAsc(me.getUserId(),
						team.getId(), monday, sunday);

		Set<UUID> ids = new HashSet<>();
		teammates.forEach(placement -> ids.add(placement.getUserId()));
		weekTasks.forEach(task -> ids.add(task.getAssigneeId()));
		weekTasks.stream().map(IskurTask::getCreatedBy).filter(Objects::nonNull).forEach(ids::add);
		Map<UUID, String> people = names(ids, null);

		Collator collator = Collator.getInstance(TR);
		Comparator<IskurPlacement> byName = Comparator
				.comparing(placement -> people.getOrDefault(placement.getUserId(), ""), collator);
		List<IskurPlacement> ordered = ordered(me, teammates, byName);
		Map<UUID, List<IskurTask>> byAssignee = weekTasks.stream()
				.collect(Collectors.groupingBy(IskurTask::getAssigneeId));
		List<IskurPlacement> shown = leader ? ordered : List.of(me);

		List<IskurTeamTasksResponse.Member> members = shown.stream()
				.map(placement -> member(placement, me, leader, people, team.getName(),
						byAssignee.getOrDefault(placement.getUserId(), List.of())))
				.toList();
		List<IskurTeamTasksResponse.RosterPerson> roster = ordered.stream()
				.map(placement -> new IskurTeamTasksResponse.RosterPerson(placement.getUserId(),
						people.getOrDefault(placement.getUserId(), ""), placement.getTeamRole(),
						placement.getLeaderId()))
				.toList();
		return new IskurTeamTasksResponse(monday, new IskurTeamTasksResponse.Team(team.getId(), team.getName(),
				unitName), me.getTeamRole(), leader, members, roster);
	}

	/**
	 * The caller first, then their own sub-group, then the other leader and their sub-group, then members without a
	 * leader; names in Turkish order within each group.
	 */
	private static List<IskurPlacement> ordered(IskurPlacement me, List<IskurPlacement> teammates,
			Comparator<IskurPlacement> byName) {
		Set<IskurPlacement> result = new LinkedHashSet<>();
		result.add(me);
		UUID myLeader = me.getTeamRole() == IskurTeamRole.LEADER ? me.getUserId() : me.getLeaderId();
		List<IskurPlacement> leaders = teammates.stream()
				.filter(placement -> placement.getTeamRole() == IskurTeamRole.LEADER)
				.sorted(Comparator.comparing((IskurPlacement placement) -> !placement.getUserId().equals(myLeader))
						.thenComparing(byName))
				.toList();
		for (IskurPlacement leaderPlacement : leaders) {
			result.add(leaderPlacement);
			teammates.stream()
					.filter(placement -> leaderPlacement.getUserId().equals(placement.getLeaderId()))
					.sorted(byName)
					.forEach(result::add);
		}
		teammates.stream().sorted(byName).forEach(result::add);
		return new ArrayList<>(result);
	}

	private IskurTeamTasksResponse.Member member(IskurPlacement placement, IskurPlacement me, boolean leader,
			Map<UUID, String> people, String teamName, List<IskurTask> own) {
		Integer days = placement.getWeeklyDays() == null ? null : placement.getWeeklyDays().intValue();
		Integer target = days == null ? null : IskurWeeklySplit.of(days, placement.getMentoringMinutes()).teamMinutes();
		int planned = own.stream()
				.filter(task -> task.getStatus() != IskurTaskStatus.HANDED_OVER)
				.mapToInt(IskurTask::getEstimatedMinutes)
				.sum();
		boolean assignable = leader && (placement.getUserId().equals(me.getUserId())
				|| me.getUserId().equals(placement.getLeaderId()));
		List<IskurTaskResponse> rows = own.stream()
				.map(task -> response(task, new Access(me, task.getAssigneeId().equals(me.getUserId()), leader),
						teamName, people))
				.toList();
		List<DayOfWeek> workDays = onboarding.workDays(placement.getProgramId(), placement.getUserId()).stream()
				.sorted()
				.toList();
		return new IskurTeamTasksResponse.Member(placement.getUserId(),
				people.getOrDefault(placement.getUserId(), ""), placement.getTeamRole(), days, target, planned,
				target != null && planned > target, assignable, workDays, rows);
	}

	private static IskurTaskResponse response(IskurTask task, Access access, String teamName,
			Map<UUID, String> people) {
		UUID by = task.getCreatedBy();
		return new IskurTaskResponse(task.getId(), task.getTeamId(), teamName,
				new IskurTaskResponse.Person(task.getAssigneeId(), people.getOrDefault(task.getAssigneeId(), "")),
				by == null ? null : new IskurTaskResponse.Person(by, people.getOrDefault(by, "")),
				task.getTitle(), task.getKind(), task.getEstimatedMinutes(), task.getDueDate(), task.isNeedsOutput(),
				task.getDescription(), task.getStatus(), task.getActualMinutes(), task.getOutputLink(),
				task.getOutputNote(), task.getHandoverReason(), task.getCompletedAt(), task.getCreatedAt(),
				access.works(), access.manages());
	}

	/** Who may open the task: its assignee, or a leader of its team. Anyone else is told it does not exist. */
	private Access accessOf(UUID actorId, IskurTask task) {
		IskurPlacement mine = placements.findByProgramIdAndUserId(task.getProgramId(), actorId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurTaskNotFound")));
		// Both need the caller to be on the task's team now: someone moved to another team hands their old tasks to
		// that team's leaders.
		boolean onTeam = mine.getTeamId().equals(task.getTeamId());
		boolean works = onTeam && task.getAssigneeId().equals(actorId);
		boolean manages = onTeam && mine.getTeamRole() == IskurTeamRole.LEADER;
		if (!works && !manages) {
			throw new ResourceNotFoundException(Texts.forRequest("error.iskurTaskNotFound"));
		}
		return new Access(mine, works, manages);
	}

	private IskurTask requireTask(UUID taskId) {
		return tasks.findById(taskId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurTaskNotFound")));
	}

	private IskurPlacement placementOf(UUID actorId) {
		return interns.currentPlacement(actorId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurNotPlaced")));
	}

	private void requireOpenProgram(UUID programId) {
		programs.findById(programId)
				.filter(program -> program.getStatus() == ProgramStatus.COMPLETED)
				.ifPresent(program -> {
					throw new InvalidRequestException(Texts.forRequest("error.iskurProgramArchived"));
				});
	}

	private void notifyAssigned(IskurTask task, UUID leaderId) {
		Optional<AppUser> recipient = users.findById(task.getAssigneeId());
		if (recipient.isEmpty()) {
			return;
		}
		String leaderName = users.findById(leaderId).map(AppUser::getFullName).orElse("");
		AppUser reader = recipient.get();
		notifications.create(reader, NotificationCategory.TASK_ASSIGNED,
				Texts.get(reader, "notification.iskurTaskAssigned.title"),
				Texts.get(reader, "notification.iskurTaskAssigned.body", leaderName, task.getTitle(),
						DUE_FORMAT.format(task.getDueDate())),
				"/student/team/tasks/" + task.getId());
	}

	private Map<UUID, String> names(Set<UUID> ids, @Nullable UUID also) {
		Set<UUID> wanted = new HashSet<>(ids);
		if (also != null) {
			wanted.add(also);
		}
		Map<UUID, String> result = new HashMap<>();
		users.findAllById(wanted).forEach(user -> result.put(user.getId(), user.getFullName()));
		return result;
	}

	private String teamName(UUID teamId) {
		return teams.findById(teamId).map(IskurTeam::getName).orElse("");
	}

	private static @Nullable String link(@Nullable String value) {
		String link = TextUtils.normalizeOptional(value);
		if (link != null && !HTTPS_LINK.matcher(link).matches()) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurTaskOutputLink"));
		}
		return link;
	}

	/** A change that lost a race with another one (two tabs, a leader deleting while the assignee saves) is a 409. */
	private static IskurTask savedOrConflict(Supplier<IskurTask> write) {
		try {
			return write.get();
		} catch (ObjectOptimisticLockingFailureException concurrent) {
			throw new ConflictException(Texts.forRequest("error.iskurTaskConcurrent"));
		}
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(MeetingSlotTimes.APP_ZONE));
	}

	static LocalDate mondayOf(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	static LocalDate requireMonday(LocalDate weekStart) {
		if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurWeekNotMonday"));
		}
		return weekStart;
	}

	private static ResponseStatusException forbidden(String key) {
		return new ResponseStatusException(HttpStatus.FORBIDDEN, Texts.forRequest(key));
	}

	/** The caller's placement, whether they are the task's assignee, and whether they lead its team. */
	private record Access(IskurPlacement placement, boolean works, boolean manages) {
	}
}
