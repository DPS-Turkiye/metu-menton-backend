package com.dps.teknokent.service;

import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.IskurWeekDecision;
import com.dps.teknokent.domain.IskurWeekStatus;
import com.dps.teknokent.domain.IskurWorkEntryKind;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.dto.requests.IskurWeekDecisionRequest;
import com.dps.teknokent.dto.responses.IskurLeaderQueueResponse;
import com.dps.teknokent.dto.responses.IskurLeaderWeekResponse;
import com.dps.teknokent.dto.responses.IskurWeekResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTask;
import com.dps.teknokent.entity.IskurWorkEntry;
import com.dps.teknokent.entity.IskurWorkWeek;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTaskRepository;
import com.dps.teknokent.repository.IskurWorkEntryRepository;
import com.dps.teknokent.repository.IskurWorkWeekRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;

/**
 * The leader's weekly review (Figma 4.1). A member's week goes to the leader of their sub-group; a leader's own week,
 * and a member's without a leader, go to the team's other leaders. The leader sees team work in full and peer
 * mentoring as numbers only (rules frame 322:3413): the week is built from {@link IskurMentoringTally}, never from a
 * meeting report.
 */
@Service
@RequiredArgsConstructor
public class IskurLeaderReviewService {

	private static final Locale TR = Locale.forLanguageTag("tr");

	private final IskurInternService interns;
	private final IskurPlacementRepository placements;
	private final IskurWorkWeekRepository weeks;
	private final IskurWorkEntryRepository entries;
	private final IskurTaskRepository tasks;
	private final AppUserRepository users;
	private final ProgramRepository programs;
	private final IskurWorkWeekService workWeeks;
	private final NotificationService notifications;
	private final IskurMentoringTally tally;
	private final PeerReportCalendar calendar;
	private final CoordinatorProgramService coordinatorProgramService;

	/**
	 * Who decides on this person's week: their sub-group's leader, or else the team's other leaders. Empty for the
	 * only leader of a team and for a member of a one-leader team without a leader: those weeks fall back to the
	 * program's coordinators (product decision, 19 Nov).
	 */
	static Set<UUID> reviewersOf(IskurPlacement person, List<IskurPlacement> teammates) {
		Set<UUID> leaders = teammates.stream()
				.filter(placement -> placement.getTeamRole() == IskurTeamRole.LEADER)
				.map(IskurPlacement::getUserId)
				.filter(id -> !id.equals(person.getUserId()))
				.collect(Collectors.toCollection(LinkedHashSet::new));
		UUID own = person.getLeaderId();
		return own != null && leaders.contains(own) ? Set.of(own) : leaders;
	}

	/** "Haftalık kayıtlar": the people whose week the caller reviews, for one week. */
	@Transactional(readOnly = true)
	public IskurLeaderQueueResponse queue(UUID actorId, @Nullable LocalDate weekStart) {
		IskurPlacement me = requireLeader(actorId);
		LocalDate monday = weekStart == null ? calendar.weekStartOf(calendar.today())
				: IskurTaskService.requireMonday(PeerReportCalendar.requirePlausible(weekStart));
		return new IskurLeaderQueueResponse(monday, rows(me.getProgramId(), revieweesOf(me), monday));
	}

	@Transactional(readOnly = true)
	public IskurLeaderWeekResponse week(UUID actorId, UUID weekId) {
		IskurPlacement me = requireLeader(actorId);
		Reviewed reviewed = requireReviewed(me, weekId);
		return response(reviewed, rows(me.getProgramId(), revieweesOf(me), reviewed.week().getWeekStart()));
	}

	/**
	 * Approves, sends back or rejects a sent week. A revision or a rejection needs a reason, which the intern sees and
	 * may object to. The intern is notified.
	 */
	@Transactional
	public IskurLeaderWeekResponse decide(UUID actorId, UUID weekId, IskurWeekDecisionRequest request) {
		IskurPlacement me = requireLeader(actorId);
		Reviewed decided = decideWeek(actorId, requireReviewed(me, weekId), request);
		return response(decided, rows(me.getProgramId(), revieweesOf(me), decided.week().getWeekStart()));
	}

	/** The coordinators' queue: only the weeks no leader of the team can review (a one-leader team). */
	@Transactional(readOnly = true)
	public IskurLeaderQueueResponse coordinatorQueue(UUID actorId, UUID programId, @Nullable LocalDate weekStart) {
		Program program = requireCoordinated(actorId, programId);
		LocalDate monday = weekStart == null ? calendar.weekStartOf(calendar.today())
				: IskurTaskService.requireMonday(PeerReportCalendar.requirePlausible(weekStart));
		return new IskurLeaderQueueResponse(monday, rows(program.getId(), fallbackPeople(program.getId()), monday));
	}

	@Transactional(readOnly = true)
	public IskurLeaderWeekResponse coordinatorWeek(UUID actorId, UUID programId, UUID weekId) {
		Program program = requireCoordinated(actorId, programId);
		Reviewed reviewed = requireFallback(program, weekId);
		return response(reviewed, rows(program.getId(), fallbackPeople(program.getId()),
				reviewed.week().getWeekStart()));
	}

	@Transactional
	public IskurLeaderWeekResponse coordinatorDecide(UUID actorId, UUID programId, UUID weekId,
			IskurWeekDecisionRequest request) {
		Program program = requireCoordinated(actorId, programId);
		Reviewed decided = decideWeek(actorId, requireFallback(program, weekId), request);
		return response(decided, rows(program.getId(), fallbackPeople(program.getId()),
				decided.week().getWeekStart()));
	}

	private Reviewed decideWeek(UUID actorId, Reviewed reviewed, IskurWeekDecisionRequest request) {
		IskurWorkWeek week = reviewed.week();
		// A coordinator or an admin may also be placed as an intern: never on their own week.
		if (week.getUserId().equals(actorId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, Texts.forRequest("error.iskurWeekOwn"));
		}
		programs.findById(week.getProgramId())
				.filter(program -> program.getStatus() == ProgramStatus.COMPLETED)
				.ifPresent(program -> {
					throw new InvalidRequestException(Texts.forRequest("error.iskurProgramArchived"));
				});
		if (week.getStatus() != IskurWeekStatus.SUBMITTED) {
			throw new ConflictException(Texts.forRequest("error.iskurWeekNotSubmitted"));
		}
		String reason = TextUtils.normalizeOptional(request.reason());
		if (request.decision() != IskurWeekDecision.APPROVE && reason == null) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurWeekReasonRequired"));
		}
		week.decide(request.decision(), reason, request.timeMatchesWork(), request.effortDocumented(),
				request.outputsSufficient(), actorId, calendar.now());
		IskurWorkWeek saved = IskurWorkWeekService.savedOrConflict(() -> weeks.saveAndFlush(week));
		AppUser intern = reviewed.person();
		String leaderName = users.findById(actorId).map(AppUser::getFullName).orElse("");
		notifications.create(intern, NotificationCategory.GENERAL,
				Texts.get(intern, "notification.iskurWeekDecided.title"),
				Texts.get(intern, "notification.iskurWeekDecided." + request.decision().name(), leaderName),
				"/student/work-log?view=weekly&week=" + saved.getWeekStart());
		return new Reviewed(saved, reviewed.placement(), intern);
	}

	private IskurLeaderWeekResponse response(Reviewed reviewed, List<IskurLeaderQueueResponse.Row> queue) {
		IskurWorkWeek week = reviewed.week();
		List<IskurWorkEntry> lines = entries.findByWeekIdOrderByEntryDateAscSortOrderAsc(week.getId());
		IskurMentoringTally.Week mentoring = tally.of(reviewed.person(), week.getWeekStart());
		Map<UUID, IskurTask> linked = tasks.findAllById(lines.stream()
				.map(IskurWorkEntry::getTaskId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(IskurTask::getId, Function.identity()));
		List<IskurLeaderWeekResponse.TeamLine> team = lines.stream()
				.filter(line -> line.getKind() == IskurWorkEntryKind.TEAM)
				.map(line -> {
					UUID taskId = line.getTaskId();
					IskurTask task = taskId == null ? null : linked.get(taskId);
					return new IskurLeaderWeekResponse.TeamLine(line.getEntryDate(), line.getTitle(),
							line.getMinutes(), line.getOutput(), line.getNote(),
							task == null ? null : task.getStatus(), task == null ? null : task.getHandoverReason());
				})
				.toList();
		Map<LocalDate, List<IskurWorkEntry>> supportByDay = lines.stream()
				.filter(line -> line.getKind() == IskurWorkEntryKind.SUPPORT_ACTIVITY)
				.collect(Collectors.groupingBy(IskurWorkEntry::getEntryDate));
		List<IskurLeaderWeekResponse.Day> days = new ArrayList<>();
		for (IskurMentoringTally.Day day : mentoring.days()) {
			List<IskurWorkEntry> support = supportByDay.getOrDefault(day.date(), List.of());
			int minutes = day.minutes() + support.stream().mapToInt(IskurWorkEntry::getMinutes).sum();
			if (day.meetingCount() == 0 && minutes == 0) {
				continue;
			}
			days.add(new IskurLeaderWeekResponse.Day(day.date(), day.meetingCount(), day.reportsSubmitted(),
					day.meetingCount(), minutes, support.stream().map(IskurWorkEntry::getTitle).toList()));
		}
		IskurWeekResponse.Totals totals = IskurWorkWeekService.totals(lines, mentoring);
		Short weeklyDays = reviewed.placement().getWeeklyDays();
		return new IskurLeaderWeekResponse(week.getId(), week.getWeekStart(),
				new IskurLeaderWeekResponse.Person(reviewed.person().getId(), reviewed.person().getFullName(),
						weeklyDays == null ? null : weeklyDays.intValue()),
				week.getStatus(), week.getSubmittedAt(), IskurWorkWeekService.split(reviewed.placement()),
				totals, team,
				new IskurLeaderWeekResponse.Mentoring(totals.mentoringMinutes(), mentoring.meetingCount(),
						mentoring.reportsSubmitted(), mentoring.meetingCount(), List.copyOf(days)),
				workWeeks.decision(week), IskurWorkWeekService.objection(week),
				week.getStatus() == IskurWeekStatus.SUBMITTED, queue);
	}

	private List<IskurPlacement> revieweesOf(IskurPlacement me) {
		List<IskurPlacement> teammates = placements.findByTeamId(me.getTeamId());
		return teammates.stream()
				.filter(placement -> reviewersOf(placement, teammates).contains(me.getUserId()))
				.toList();
	}

	/** The program's people whose team has no leader to review their week. */
	List<IskurPlacement> fallbackPeople(UUID programId) {
		Map<UUID, List<IskurPlacement>> byTeam = placements.findByProgramId(programId).stream()
				.collect(Collectors.groupingBy(IskurPlacement::getTeamId));
		return byTeam.values().stream()
				.flatMap(teammates -> teammates.stream()
						.filter(placement -> reviewersOf(placement, teammates).isEmpty()))
				.toList();
	}

	private List<IskurLeaderQueueResponse.Row> rows(UUID programId, List<IskurPlacement> reviewees,
			LocalDate monday) {
		if (reviewees.isEmpty()) {
			return List.of();
		}
		Set<UUID> ids = reviewees.stream().map(IskurPlacement::getUserId).collect(Collectors.toSet());
		Map<UUID, AppUser> people = users.findAllById(ids).stream()
				.collect(Collectors.toMap(AppUser::getId, Function.identity()));
		Map<UUID, IskurWorkWeek> byUser = weeks.findByProgramIdAndWeekStartAndUserIdIn(programId, monday, ids)
				.stream()
				.collect(Collectors.toMap(IskurWorkWeek::getUserId, Function.identity()));
		Map<UUID, List<IskurWorkEntry>> linesByWeek = byUser.isEmpty() ? Map.of()
				: entries.findByWeekIdIn(byUser.values().stream().map(IskurWorkWeek::getId).toList()).stream()
						.collect(Collectors.groupingBy(IskurWorkEntry::getWeekId));
		Collator collator = Collator.getInstance(TR);
		List<IskurLeaderQueueResponse.Row> rows = new ArrayList<>();
		Map<UUID, IskurMentoringTally.Week> tallies = new HashMap<>();
		for (IskurPlacement placement : reviewees) {
			AppUser person = people.get(placement.getUserId());
			if (person == null) {
				continue;
			}
			IskurWorkWeek found = byUser.get(person.getId());
			// A draft is the intern's own until it is sent: its status shows, not its lines or totals.
			boolean draft = found != null && found.getStatus() == IskurWeekStatus.DRAFT;
			IskurWorkWeek week = draft ? null : found;
			List<IskurWorkEntry> lines = week == null ? List.of()
					: linesByWeek.getOrDefault(week.getId(), List.of());
			IskurWeekResponse.Totals totals = week == null ? new IskurWeekResponse.Totals(0, 0)
					: IskurWorkWeekService.totals(lines, tallies.computeIfAbsent(person.getId(),
							id -> tally.of(person, monday)));
			rows.add(new IskurLeaderQueueResponse.Row(person.getId(), person.getFullName(),
					week == null ? null : week.getId(), found == null ? null : found.getStatus(),
					week == null ? null : week.getSubmittedAt(), totals.teamMinutes(), totals.mentoringMinutes()));
		}
		rows.sort(Comparator.comparing(IskurLeaderQueueResponse.Row::fullName, collator));
		return rows;
	}

	/** The week and its person, when the caller reviews that person now; anyone else is told it does not exist. */
	private Reviewed requireReviewed(IskurPlacement me, UUID weekId) {
		IskurWorkWeek week = weeks.findById(weekId)
				.filter(found -> found.getProgramId().equals(me.getProgramId()))
				.filter(found -> found.getStatus() != IskurWeekStatus.DRAFT)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurWeekNotFound")));
		List<IskurPlacement> teammates = placements.findByTeamId(me.getTeamId());
		IskurPlacement person = teammates.stream()
				.filter(placement -> placement.getUserId().equals(week.getUserId()))
				.filter(placement -> reviewersOf(placement, teammates).contains(me.getUserId()))
				.findFirst()
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurWeekNotFound")));
		AppUser user = users.findById(person.getUserId())
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurWeekNotFound")));
		return new Reviewed(week, person, user);
	}

	/** A fallback week of the program: its person's team has no leader to review it. Anything else is not found. */
	private Reviewed requireFallback(Program program, UUID weekId) {
		IskurWorkWeek week = weeks.findById(weekId)
				.filter(found -> found.getProgramId().equals(program.getId()))
				.filter(found -> found.getStatus() != IskurWeekStatus.DRAFT)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurWeekNotFound")));
		IskurPlacement person = fallbackPeople(program.getId()).stream()
				.filter(placement -> placement.getUserId().equals(week.getUserId()))
				.findFirst()
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurWeekNotFound")));
		AppUser user = users.findById(person.getUserId())
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurWeekNotFound")));
		return new Reviewed(week, person, user);
	}

	private Program requireCoordinated(UUID actorId, UUID programId) {
		Program program = coordinatorProgramService.requireManagedProgram(actorId, programId);
		if (program.getProgramType() != ProgramType.PEER_MENTORING) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurPeerProgramsOnly"));
		}
		return program;
	}

	private IskurPlacement requireLeader(UUID actorId) {
		IskurPlacement me = interns.currentPlacement(actorId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurNotPlaced")));
		if (me.getTeamRole() != IskurTeamRole.LEADER) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, Texts.forRequest("error.iskurLeadersOnly"));
		}
		return me;
	}

	private record Reviewed(IskurWorkWeek week, IskurPlacement placement, AppUser person) {
	}
}
