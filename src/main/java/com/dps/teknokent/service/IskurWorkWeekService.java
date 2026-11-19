package com.dps.teknokent.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IskurWeekStatus;
import com.dps.teknokent.domain.IskurWeeklySplit;
import com.dps.teknokent.domain.IskurWorkEntryKind;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.dto.requests.IskurSupportEntryRequest;
import com.dps.teknokent.dto.requests.IskurWeekObjectionRequest;
import com.dps.teknokent.dto.requests.IskurWorkEntriesRequest;
import com.dps.teknokent.dto.responses.CoordinatorProgramResponse;
import com.dps.teknokent.dto.responses.IskurWeekResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurSupportActivity;
import com.dps.teknokent.entity.IskurTask;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.IskurWorkEntry;
import com.dps.teknokent.entity.IskurWorkWeek;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurSupportActivityRepository;
import com.dps.teknokent.repository.IskurTaskRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.IskurWorkEntryRepository;
import com.dps.teknokent.repository.IskurWorkWeekRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;

/**
 * An intern's weekly log (Figma 1.1 Bu Haftam, 3.1 Çalışma kaydı, 3.2 Haftalık özet): team work and peer-mentoring
 * support activities as lines of one week, sent to their leader. Peer meetings and the other mentoring work stay in
 * the peer reports and are only counted here. The rules frame's "no meeting planned from Monday" prompt is
 * {@code needsSupportActivity}.
 */
@Service
@RequiredArgsConstructor
public class IskurWorkWeekService {

	/** A day's lines may fill one work day. */
	static final int DAY_MAX_MINUTES = IskurWeeklySplit.MINUTES_PER_DAY;

	private final IskurInternService interns;
	private final IskurWorkWeekRepository weeks;
	private final IskurWorkEntryRepository entries;
	private final IskurTaskRepository tasks;
	private final IskurSupportActivityRepository activities;
	private final IskurPlacementRepository placements;
	private final IskurTeamRepository teams;
	private final IskurUnitRepository units;
	private final AppUserRepository users;
	private final ProgramRepository programs;
	private final CoordinatorProgramService coordinatorProgramService;
	private final NotificationService notifications;
	private final IskurMentoringTally tally;
	private final PeerReportCalendar calendar;
	private final IskurWeekHistoryService history;

	/** The caller's week; {@code weekStart} null means this week. Reading never creates the week. */
	@Transactional(readOnly = true)
	public IskurWeekResponse week(UUID actorId, @Nullable LocalDate weekStart) {
		IskurPlacement me = placementOf(actorId);
		LocalDate monday = weekStart == null ? calendar.weekStartOf(calendar.today())
				: IskurTaskService.requireMonday(PeerReportCalendar.requirePlausible(weekStart));
		return response(me, monday,
				weeks.findByProgramIdAndUserIdAndWeekStart(me.getProgramId(), actorId, monday).orElse(null));
	}

	/** Replaces the draft's lines. Only this week and earlier ones, only days up to today, a work day per day. */
	@Transactional
	public IskurWeekResponse saveEntries(UUID actorId, LocalDate weekStart, IskurWorkEntriesRequest request) {
		IskurPlacement me = placementOf(actorId);
		LocalDate monday = requireReachable(me, weekStart);
		requireOpenProgram(me.getProgramId());
		IskurWorkWeek week = editableWeek(me, monday);
		Map<UUID, IskurTask> ownTasks = new HashMap<>();
		List<IskurWorkEntry> saved = entries.findByWeekIdOrderByEntryDateAscSortOrderAsc(week.getId());
		Map<UUID, IskurSupportActivity> pool = poolOf(me.getProgramId(), saved);
		List<IskurWorkEntry> lines = new ArrayList<>();
		Instant now = calendar.now();
		int order = 0;
		for (IskurWorkEntriesRequest.Entry line : request.entries()) {
			lines.add(line(week, me, monday, line, order++, ownTasks, pool, now));
		}
		requireDaysFit(lines);
		// The week's version is checked first, so a second tab saving at the same time gets a 409 before it
		// touches the lines the first one is replacing.
		IskurWorkWeek stored = savedOrConflict(() -> {
			week.setUpdatedAt(now);
			IskurWorkWeek checked = weeks.saveAndFlush(week);
			entries.deleteAll(saved);
			entries.flush();
			entries.saveAll(lines);
			entries.flush();
			return checked;
		});
		return response(me, monday, stored);
	}

	/** "Mentörlük destek faaliyeti seç": adds one support line to the draft. */
	@Transactional
	public IskurWeekResponse addSupportActivity(UUID actorId, LocalDate weekStart, IskurSupportEntryRequest request) {
		IskurPlacement me = placementOf(actorId);
		LocalDate monday = requireReachable(me, weekStart);
		requireOpenProgram(me.getProgramId());
		IskurWorkWeek week = editableWeek(me, monday);
		List<IskurWorkEntry> lines = new ArrayList<>(
				entries.findByWeekIdOrderByEntryDateAscSortOrderAsc(week.getId()));
		IskurWorkEntriesRequest.Entry added = new IskurWorkEntriesRequest.Entry(request.date(),
				IskurWorkEntryKind.SUPPORT_ACTIVITY, null, request.activityId(), null, request.minutes(), null, null);
		int order = lines.stream().mapToInt(IskurWorkEntry::getSortOrder).max().orElse(-1) + 1;
		Instant now = calendar.now();
		IskurWorkEntry line = line(week, me, monday, added, order, new HashMap<>(),
				poolOf(me.getProgramId(), List.of()), now);
		lines.add(line);
		requireDaysFit(lines);
		IskurWorkWeek stored = savedOrConflict(() -> {
			week.setUpdatedAt(now);
			IskurWorkWeek checked = weeks.saveAndFlush(week);
			entries.saveAndFlush(line);
			return checked;
		});
		return response(me, monday, stored);
	}

	/** Sends the week to the leader (or sends it again after a revision); needs something logged. */
	@Transactional
	public IskurWeekResponse submit(UUID actorId, LocalDate weekStart) {
		IskurPlacement me = placementOf(actorId);
		LocalDate monday = requireReachable(me, weekStart);
		requireOpenProgram(me.getProgramId());
		IskurWorkWeek week = editableWeek(me, monday);
		AppUser intern = requireUser(actorId);
		List<IskurWorkEntry> lines = entries.findByWeekIdOrderByEntryDateAscSortOrderAsc(week.getId());
		if (lines.isEmpty() && tally.of(intern, monday).minutes() == 0) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurWeekEmpty"));
		}
		Instant now = calendar.now();
		week.submit(now);
		IskurWorkWeek saved = savedOrConflict(() -> weeks.saveAndFlush(week));
		history.submitted(saved, now);
		Set<UUID> reviewers = IskurLeaderReviewService.reviewersOf(me, placements.findByTeamId(me.getTeamId()));
		String href = "/student/team/weeks/" + saved.getId();
		if (reviewers.isEmpty()) {
			// No leader can review it: the unit's coordinator, or every coordinator of the program.
			reviewers = fallbackReviewers(me);
			href = "/coordinator/programs/" + me.getProgramId() + "/iskur/weeks/" + saved.getId();
		}
		String link = href;
		users.findAllById(reviewers).forEach(reviewer -> notifications.create(reviewer, NotificationCategory.GENERAL,
				Texts.get(reviewer, "notification.iskurWeekSubmitted.title"),
				Texts.get(reviewer, "notification.iskurWeekSubmitted.body", intern.getFullName()), link));
		return response(me, monday, saved);
	}

	/** Answers a revision or a rejection once; the program's coordinators are told. */
	@Transactional
	public IskurWeekResponse object(UUID actorId, LocalDate weekStart, IskurWeekObjectionRequest request) {
		IskurPlacement me = placementOf(actorId);
		LocalDate monday = IskurTaskService.requireMonday(PeerReportCalendar.requirePlausible(weekStart));
		requireOpenProgram(me.getProgramId());
		IskurWorkWeek week = weeks.findByProgramIdAndUserIdAndWeekStart(me.getProgramId(), actorId, monday)
				.orElseThrow(() -> new ConflictException(Texts.forRequest("error.iskurWeekNotDecided")));
		if (week.getStatus() != IskurWeekStatus.REVISION_REQUESTED && week.getStatus() != IskurWeekStatus.REJECTED) {
			throw new ConflictException(Texts.forRequest("error.iskurWeekNotDecided"));
		}
		if (week.getObjection() != null) {
			throw new ConflictException(Texts.forRequest("error.iskurWeekObjected"));
		}
		Instant now = calendar.now();
		week.setObjection(request.objection().strip());
		week.setObjectedAt(now);
		week.setUpdatedAt(now);
		IskurWorkWeek saved = savedOrConflict(() -> weeks.saveAndFlush(week));
		history.objected(saved, request.objection().strip(), now);
		AppUser intern = requireUser(actorId);
		programs.findById(me.getProgramId()).ifPresent(program -> notifyCoordinators(program, intern, monday));
		return response(me, monday, saved);
	}

	IskurWeekResponse response(IskurPlacement me, LocalDate monday, @Nullable IskurWorkWeek week) {
		AppUser intern = requireUser(me.getUserId());
		List<IskurWorkEntry> lines = week == null ? List.of()
				: entries.findByWeekIdOrderByEntryDateAscSortOrderAsc(week.getId());
		IskurMentoringTally.Week mentoring = tally.of(intern, monday);
		IskurWeekResponse.Split split = split(me);
		int support = supportMinutes(lines);
		IskurWeekStatus status = week == null ? IskurWeekStatus.DRAFT : week.getStatus();
		boolean reachable = !monday.isAfter(calendar.weekStartOf(calendar.today()));
		boolean editable = status.editable() && reachable;
		boolean expectsMentoring = split == null || split.mentoringMinutes() > 0;
		boolean needsSupport = expectsMentoring && reachable && mentoring.meetingCount() == 0
				&& !mentoring.meetingsAhead() && support == 0;
		List<IskurWeekResponse.Task> weekTasks = tasks
				.findByAssigneeIdAndTeamIdAndDueDateBetweenOrderByDueDateAscCreatedAtAsc(me.getUserId(),
						me.getTeamId(), monday, monday.plusDays(6))
				.stream()
				.map(task -> new IskurWeekResponse.Task(task.getId(), task.getTitle(), task.getKind(),
						task.getEstimatedMinutes(), task.getDueDate(), task.getStatus(), task.isNeedsOutput(),
						task.getActualMinutes()))
				.toList();
		List<IskurWeekResponse.SupportOption> options = activities
				.findByProgramIdOrderBySortOrderAscCreatedAtAsc(me.getProgramId()).stream()
				.filter(IskurSupportActivity::isActive)
				.map(activity -> new IskurWeekResponse.SupportOption(activity.getId(), activity.getTitle(),
						activity.getDescription()))
				.toList();
		// The one leader who reviews the week; none when it is several co-leaders or the coordinators.
		Set<UUID> reviewers = IskurLeaderReviewService.reviewersOf(me, placements.findByTeamId(me.getTeamId()));
		IskurWeekResponse.Person reviewer = reviewers.size() != 1 ? null
				: users.findById(reviewers.iterator().next())
						.map(user -> new IskurWeekResponse.Person(user.getId(), user.getFullName()))
						.orElse(null);
		return new IskurWeekResponse(monday, week == null ? null : week.getId(), status, editable,
				editable && (!lines.isEmpty() || mentoring.minutes() > 0),
				week == null ? null : week.getSubmittedAt(), week == null ? null : decision(week),
				week == null ? null : objection(week), reviewer, split,
				totals(lines, mentoring), new IskurWeekResponse.Mentoring(mentoring.meetingCount(),
						mentoring.meetingMinutes(), mentoring.activityMinutes(), support,
						mentoring.reportsSubmitted(), mentoring.meetingCount(), mentoring.meetingsAhead()),
				needsSupport, lines.stream().map(IskurWorkWeekService::entry).toList(), weekTasks, options,
				history.of(week));
	}

	static IskurWeekResponse.@Nullable Split split(IskurPlacement placement) {
		Short days = placement.getWeeklyDays();
		if (days == null) {
			return null;
		}
		IskurWeeklySplit split = IskurWeeklySplit.of(days, placement.getMentoringMinutes());
		return new IskurWeekResponse.Split(split.totalMinutes(), split.mentoringMinutes(), split.teamMinutes());
	}

	static IskurWeekResponse.Totals totals(List<IskurWorkEntry> lines, IskurMentoringTally.Week mentoring) {
		int team = lines.stream()
				.filter(line -> line.getKind() == IskurWorkEntryKind.TEAM)
				.mapToInt(IskurWorkEntry::getMinutes)
				.sum();
		return new IskurWeekResponse.Totals(team, mentoring.minutes() + supportMinutes(lines));
	}

	static int supportMinutes(List<IskurWorkEntry> lines) {
		return lines.stream()
				.filter(line -> line.getKind() == IskurWorkEntryKind.SUPPORT_ACTIVITY)
				.mapToInt(IskurWorkEntry::getMinutes)
				.sum();
	}

	IskurWeekResponse.@Nullable Decision decision(IskurWorkWeek week) {
		Instant decidedAt = week.getDecidedAt();
		if (week.getDecision() == null || decidedAt == null) {
			return null;
		}
		UUID by = week.getDecidedBy();
		IskurWeekResponse.Person decidedBy = by == null ? null
				: users.findById(by).map(user -> new IskurWeekResponse.Person(user.getId(), user.getFullName()))
						.orElse(null);
		return new IskurWeekResponse.Decision(week.getDecision(), week.getDecisionReason(),
				Boolean.TRUE.equals(week.getCheckTimeMatchesWork()),
				Boolean.TRUE.equals(week.getCheckEffortDocumented()),
				Boolean.TRUE.equals(week.getCheckOutputsSufficient()), decidedAt, decidedBy);
	}

	static IskurWeekResponse.@Nullable Objection objection(IskurWorkWeek week) {
		String text = week.getObjection();
		Instant at = week.getObjectedAt();
		return text == null || at == null ? null : new IskurWeekResponse.Objection(text, at);
	}

	private static IskurWeekResponse.Entry entry(IskurWorkEntry line) {
		return new IskurWeekResponse.Entry(line.getId(), line.getEntryDate(), line.getKind(), line.getTaskId(),
				line.getSupportActivityId(), line.getTitle(), line.getMinutes(), line.getOutput(), line.getNote());
	}

	private IskurWorkEntry line(IskurWorkWeek week, IskurPlacement me, LocalDate monday,
			IskurWorkEntriesRequest.Entry line, int order, Map<UUID, IskurTask> ownTasks,
			Map<UUID, IskurSupportActivity> pool, Instant now) {
		LocalDate date = line.date();
		if (date.isBefore(monday) || date.isAfter(monday.plusDays(6)) || date.isAfter(calendar.today())) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurWeekEntryDate"));
		}
		IskurWorkEntry entry = new IskurWorkEntry(week.getId(), line.kind(), order, now);
		entry.setEntryDate(date);
		entry.setMinutes(line.minutes());
		if (line.kind() == IskurWorkEntryKind.SUPPORT_ACTIVITY) {
			UUID activityId = line.supportActivityId();
			IskurSupportActivity activity = activityId == null ? null : pool.get(activityId);
			if (activity == null || line.taskId() != null) {
				throw new InvalidRequestException(Texts.forRequest("error.iskurWeekSupportActivity"));
			}
			// The pool's title only, and no free text: a mentoring line reaches the leader as a title and minutes.
			entry.setSupportActivityId(activity.getId());
			entry.setTitle(activity.getTitle());
			return entry;
		}
		if (line.supportActivityId() != null) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurWeekSupportActivity"));
		}
		String title = TextUtils.normalizeOptional(line.title());
		UUID taskId = line.taskId();
		if (taskId != null) {
			IskurTask task = ownTasks.computeIfAbsent(taskId, id -> tasks.findById(id)
					.filter(found -> found.getAssigneeId().equals(me.getUserId()))
					.filter(found -> found.getProgramId().equals(me.getProgramId()))
					.orElseThrow(() -> new InvalidRequestException(Texts.forRequest("error.iskurWeekEntryTask"))));
			entry.setTaskId(task.getId());
			if (title == null) {
				title = task.getTitle();
			}
		}
		if (title == null) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurWeekEntryTitle"));
		}
		entry.setTitle(title);
		entry.setOutput(TextUtils.normalizeOptional(line.output()));
		entry.setNote(TextUtils.normalizeOptional(line.note()));
		return entry;
	}

	private static void requireDaysFit(List<IskurWorkEntry> lines) {
		Map<LocalDate, Integer> byDay = lines.stream()
				.collect(Collectors.groupingBy(IskurWorkEntry::getEntryDate,
						Collectors.summingInt(IskurWorkEntry::getMinutes)));
		if (byDay.values().stream().anyMatch(minutes -> minutes > DAY_MAX_MINUTES)) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurWeekDayOver"));
		}
	}

	/**
	 * The activities a week may use: the pool's active ones, and any the week already has, so retiring an activity
	 * does not block saving a draft that picked it earlier.
	 */
	private Map<UUID, IskurSupportActivity> poolOf(UUID programId, List<IskurWorkEntry> saved) {
		Set<UUID> kept = saved.stream()
				.map(IskurWorkEntry::getSupportActivityId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		return activities.findByProgramIdOrderBySortOrderAscCreatedAtAsc(programId).stream()
				.filter(activity -> activity.isActive() || kept.contains(activity.getId()))
				.collect(Collectors.toMap(IskurSupportActivity::getId, activity -> activity));
	}

	/** The week to change, created on the first save; a sent or decided week is locked. */
	private IskurWorkWeek editableWeek(IskurPlacement me, LocalDate monday) {
		IskurWorkWeek week = weeks.findByProgramIdAndUserIdAndWeekStart(me.getProgramId(), me.getUserId(), monday)
				.orElseGet(() -> savedOrConflict(() -> weeks.saveAndFlush(
						new IskurWorkWeek(me.getProgramId(), me.getUserId(), monday, calendar.now()))));
		if (!week.getStatus().editable()) {
			throw new ConflictException(Texts.forRequest("error.iskurWeekLocked"));
		}
		return week;
	}

	/** A Monday no later than this week's (next week cannot be logged yet) and not before the placement's week. */
	private LocalDate requireReachable(IskurPlacement me, LocalDate weekStart) {
		LocalDate monday = IskurTaskService.requireMonday(PeerReportCalendar.requirePlausible(weekStart));
		if (monday.isAfter(calendar.weekStartOf(calendar.today()))) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurWeekFuture"));
		}
		if (monday.isBefore(calendar.weekStartOf(calendar.dateOf(me.getPlacedAt())))) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurWeekBeforePlacement"));
		}
		return monday;
	}

	private void requireOpenProgram(UUID programId) {
		Optional<Program> program = programs.findById(programId);
		if (program.isPresent() && program.get().getStatus() == ProgramStatus.COMPLETED) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurProgramArchived"));
		}
	}

	private Set<UUID> fallbackReviewers(IskurPlacement me) {
		UUID unitCoordinator = teams.findById(me.getTeamId())
				.flatMap(team -> units.findById(team.getUnitId()))
				.map(IskurUnit::getCoordinatorId)
				.orElse(null);
		if (unitCoordinator != null) {
			return Set.of(unitCoordinator);
		}
		return programs.findById(me.getProgramId())
				.map(program -> coordinatorProgramService.coordinatorsOf(program).stream()
						.map(CoordinatorProgramResponse.CoordinatorRef::id)
						.collect(Collectors.toSet()))
				.orElse(Set.of());
	}

	private void notifyCoordinators(Program program, AppUser intern, LocalDate monday) {
		Set<UUID> ids = coordinatorProgramService.coordinatorsOf(program).stream()
				.map(CoordinatorProgramResponse.CoordinatorRef::id)
				.collect(Collectors.toSet());
		users.findAllById(ids).forEach(coordinator -> notifications.create(coordinator, NotificationCategory.GENERAL,
				Texts.get(coordinator, "notification.iskurWeekObjection.title"),
				Texts.get(coordinator, "notification.iskurWeekObjection.body", intern.getFullName(),
						monday.toString()),
				"/coordinator/programs/" + program.getId() + "/iskur"));
	}

	private IskurPlacement placementOf(UUID actorId) {
		return interns.currentPlacement(actorId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurNotPlaced")));
	}

	private AppUser requireUser(UUID userId) {
		return users.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurNotPlaced")));
	}

	/** Two first saves (two tabs) hit the unique week, and two edits the version check: a 409 either way. */
	static <T> T savedOrConflict(Supplier<T> save) {
		try {
			return save.get();
		} catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
			throw new ConflictException(Texts.forRequest("error.iskurWeekConcurrent"));
		}
	}
}
