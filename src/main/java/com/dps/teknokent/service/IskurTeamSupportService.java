package com.dps.teknokent.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.IskurTeamSupportStatus;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.dto.requests.IskurTeamSupportAnswerRequest;
import com.dps.teknokent.dto.requests.IskurTeamSupportOpenRequest;
import com.dps.teknokent.dto.responses.IskurTeamSupportResponse;
import com.dps.teknokent.dto.responses.IskurTeamSupportsResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTask;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurTeamSupportRequest;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTaskRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurTeamSupportRequestRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

/**
 * Help from another team (student handbook p.12, p.14, p.18: "Başka ekipten destek lazım → Liderin talep açar"; the
 * task example "Dijital Dönüşüm ekibinden destek talebi açıldı"). A team leader asks another team of the program,
 * optionally for one of their team's tasks; that team's leaders are told and one of them accepts or declines. The
 * task shows its requests to its assignee and its team's leaders.
 */
@Service
public class IskurTeamSupportService {

	private final IskurInternService interns;
	private final ProgramRepository programs;
	private final IskurPlacementRepository placements;
	private final IskurTeamRepository teams;
	private final IskurUnitRepository units;
	private final IskurTaskRepository tasks;
	private final IskurTeamSupportRequestRepository requests;
	private final AppUserRepository users;
	private final NotificationService notifications;
	private final Clock clock;

	@Autowired
	public IskurTeamSupportService(IskurInternService interns, ProgramRepository programs,
			IskurPlacementRepository placements, IskurTeamRepository teams, IskurUnitRepository units,
			IskurTaskRepository tasks, IskurTeamSupportRequestRepository requests, AppUserRepository users,
			NotificationService notifications) {
		this(interns, programs, placements, teams, units, tasks, requests, users, notifications,
				Clock.systemUTC());
	}

	IskurTeamSupportService(IskurInternService interns, ProgramRepository programs,
			IskurPlacementRepository placements, IskurTeamRepository teams, IskurUnitRepository units,
			IskurTaskRepository tasks, IskurTeamSupportRequestRepository requests, AppUserRepository users,
			NotificationService notifications, Clock clock) {
		this.interns = interns;
		this.programs = programs;
		this.placements = placements;
		this.teams = teams;
		this.units = units;
		this.tasks = tasks;
		this.requests = requests;
		this.users = users;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public IskurTeamSupportsResponse mine(UUID actorId) {
		IskurPlacement leader = requireLeader(actorId);
		return view(leader);
	}

	@Transactional
	public IskurTeamSupportsResponse open(UUID actorId, IskurTeamSupportOpenRequest request) {
		IskurPlacement leader = lockedLeader(actorId);
		IskurTeam target = teams.findById(request.toTeamId())
				.filter(team -> team.getProgramId().equals(leader.getProgramId()))
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurTeamSupportNoTeam")));
		if (target.getId().equals(leader.getTeamId())) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurTeamSupportOwnTeam"));
		}
		UUID taskId = request.taskId();
		// A task of another team is not named to this leader.
		if (taskId != null && tasks.findById(taskId)
				.filter(task -> task.getTeamId().equals(leader.getTeamId()))
				.isEmpty()) {
			throw new ResourceNotFoundException(Texts.forRequest("error.iskurTaskNotFound"));
		}
		// One open request per pair of teams and task: asking again would only notify the same leaders again.
		boolean alreadyOpen = requests.findForTeam(leader.getTeamId()).stream()
				.anyMatch(row -> row.getStatus() == IskurTeamSupportStatus.OPEN
						&& row.getFromTeamId().equals(leader.getTeamId())
						&& row.getToTeamId().equals(target.getId())
						&& Objects.equals(row.getTaskId(), taskId));
		if (alreadyOpen) {
			throw new ConflictException(Texts.forRequest("error.iskurTeamSupportOpenExists"));
		}
		IskurTeamSupportRequest created = new IskurTeamSupportRequest(leader.getProgramId(), leader.getTeamId(),
				target.getId(), actorId, request.need().strip(), clock.instant());
		created.setTaskId(taskId);
		requests.save(created);
		String fromName = teams.findById(leader.getTeamId()).map(IskurTeam::getName).orElse("");
		for (AppUser recipient : users.findAllById(leaderIds(target.getId()))) {
			notifications.create(recipient, NotificationCategory.GENERAL,
					Texts.get(recipient, "notification.iskurTeamSupportOpened.title"),
					Texts.get(recipient, "notification.iskurTeamSupportOpened.body", fromName), "/student/team");
		}
		return view(leader);
	}

	@Transactional
	public IskurTeamSupportsResponse answer(UUID actorId, UUID requestId, boolean accept,
			IskurTeamSupportAnswerRequest body) {
		IskurPlacement leader = lockedLeader(actorId);
		IskurTeamSupportRequest request = requests.findById(requestId)
				.filter(row -> row.getToTeamId().equals(leader.getTeamId()))
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurTeamSupportNotFound")));
		if (request.getStatus() != IskurTeamSupportStatus.OPEN) {
			throw new ConflictException(Texts.forRequest("error.iskurTeamSupportAnswered"));
		}
		request.setStatus(accept ? IskurTeamSupportStatus.ACCEPTED : IskurTeamSupportStatus.DECLINED);
		request.setAnswerNote(TextUtils.normalizeOptional(body.note()));
		request.setAnsweredBy(actorId);
		request.setAnsweredAt(clock.instant());
		requests.save(request);
		notifyAnswer(request, accept);
		return view(leader);
	}

	/** The requests opened for a task, for whoever may open the task: its assignee and its team's leaders. */
	@Transactional(readOnly = true)
	public List<IskurTeamSupportResponse> forTask(UUID actorId, UUID taskId) {
		IskurTask task = tasks.findById(taskId)
				.filter(found -> programs.findById(found.getProgramId())
						.filter(program -> program.has(ProgramFeature.ISKUR))
						.isPresent())
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurTaskNotFound")));
		IskurPlacement mine = placements.findByProgramIdAndUserId(task.getProgramId(), actorId)
				.filter(placement -> placement.getTeamId().equals(task.getTeamId()))
				.filter(placement -> task.getAssigneeId().equals(actorId)
						|| placement.getTeamRole() == IskurTeamRole.LEADER)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurTaskNotFound")));
		return describe(requests.findByTaskIdOrderByCreatedAtDesc(task.getId()), mine);
	}

	private IskurTeamSupportsResponse view(IskurPlacement leader) {
		Program program = programs.findById(leader.getProgramId())
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurProgramNotFound")));
		List<IskurTeam> programTeams = teams.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId());
		Map<UUID, String> unitNames = units.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()).stream()
				.collect(Collectors.toMap(IskurUnit::getId, IskurUnit::getName));
		List<IskurTeamSupportsResponse.Team> others = programTeams.stream()
				.filter(team -> !team.getId().equals(leader.getTeamId()))
				.map(team -> new IskurTeamSupportsResponse.Team(team.getId(), team.getName(),
						unitNames.getOrDefault(team.getUnitId(), "")))
				.toList();
		List<IskurTeamSupportResponse> all = describe(requests.findForTeam(leader.getTeamId()), leader);
		String teamName = programTeams.stream()
				.filter(team -> team.getId().equals(leader.getTeamId()))
				.map(IskurTeam::getName)
				.findFirst()
				.orElse("");
		return new IskurTeamSupportsResponse(program.getId(), leader.getTeamId(), teamName, others,
				all.stream().filter(row -> row.toTeamId().equals(leader.getTeamId())).toList(),
				all.stream().filter(row -> row.fromTeamId().equals(leader.getTeamId())).toList());
	}

	private List<IskurTeamSupportResponse> describe(List<IskurTeamSupportRequest> rows, IskurPlacement viewer) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Set<UUID> teamIds = new HashSet<>();
		Set<UUID> people = new HashSet<>();
		Set<UUID> taskIds = new HashSet<>();
		for (IskurTeamSupportRequest row : rows) {
			teamIds.add(row.getFromTeamId());
			teamIds.add(row.getToTeamId());
			if (row.getOpenedBy() != null) {
				people.add(row.getOpenedBy());
			}
			if (row.getAnsweredBy() != null) {
				people.add(row.getAnsweredBy());
			}
			if (row.getTaskId() != null) {
				taskIds.add(row.getTaskId());
			}
		}
		Map<UUID, String> teamNames = teams.findAllById(teamIds).stream()
				.collect(Collectors.toMap(IskurTeam::getId, IskurTeam::getName));
		Map<UUID, String> names = users.findAllById(people).stream()
				.collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
		Map<UUID, IskurTask> taskById = tasks.findAllById(taskIds).stream()
				.collect(Collectors.toMap(IskurTask::getId, Function.identity()));
		boolean leads = viewer.getTeamRole() == IskurTeamRole.LEADER;
		return rows.stream().map(row -> {
			IskurTask task = row.getTaskId() == null ? null : taskById.get(row.getTaskId());
			return new IskurTeamSupportResponse(row.getId(), row.getFromTeamId(),
					teamNames.getOrDefault(row.getFromTeamId(), ""), row.getToTeamId(),
					teamNames.getOrDefault(row.getToTeamId(), ""), task == null ? null : task.getId(),
					task == null ? null : task.getTitle(), nameOf(names, row.getOpenedBy()), row.getNeed(),
					row.getStatus(), row.getAnswerNote(), nameOf(names, row.getAnsweredBy()), row.getCreatedAt(),
					row.getAnsweredAt(), leads && row.getStatus() == IskurTeamSupportStatus.OPEN
							&& row.getToTeamId().equals(viewer.getTeamId()));
		}).toList();
	}

	/** The asking leader, the asking team's leaders now, and the task's assignee. */
	private void notifyAnswer(IskurTeamSupportRequest request, boolean accept) {
		Set<UUID> recipients = new LinkedHashSet<>();
		if (request.getOpenedBy() != null) {
			recipients.add(request.getOpenedBy());
		}
		recipients.addAll(leaderIds(request.getFromTeamId()));
		if (request.getTaskId() != null) {
			tasks.findById(request.getTaskId()).map(IskurTask::getAssigneeId).ifPresent(recipients::add);
		}
		String toName = teams.findById(request.getToTeamId()).map(IskurTeam::getName).orElse("");
		String key = accept ? "notification.iskurTeamSupportAccepted" : "notification.iskurTeamSupportDeclined";
		String href = request.getTaskId() == null ? "/student/team" : "/student/team/tasks/" + request.getTaskId();
		for (AppUser recipient : users.findAllById(recipients)) {
			notifications.create(recipient, NotificationCategory.GENERAL, Texts.get(recipient, key + ".title"),
					Texts.get(recipient, key + ".body", toName), href);
		}
	}

	private List<UUID> leaderIds(UUID teamId) {
		return placements.findByTeamId(teamId).stream()
				.filter(placement -> placement.getTeamRole() == IskurTeamRole.LEADER)
				.map(IskurPlacement::getUserId)
				.toList();
	}

	/**
	 * The leader, read only once the program row is locked: placement changes take the same lock, so a leader demoted
	 * or moved meanwhile is seen as they are now. The placement is loaded for the first time after the lock (the
	 * program look-up does not touch it), so the persistence context cannot hand back an older copy.
	 */
	private IskurPlacement lockedLeader(UUID actorId) {
		Program program = interns.currentProgram(actorId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurNotAnIntern")));
		programs.findByIdForUpdate(program.getId());
		return leaderOnly(placements.findByProgramIdAndUserId(program.getId(), actorId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurNotAnIntern"))));
	}

	/** Only a team leader opens and answers requests ("talebi lider açar"). */
	private IskurPlacement requireLeader(UUID actorId) {
		return leaderOnly(interns.currentPlacement(actorId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurNotAnIntern"))));
	}

	private static IskurPlacement leaderOnly(IskurPlacement placement) {
		if (placement.getTeamRole() != IskurTeamRole.LEADER) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					Texts.forRequest("error.iskurTeamSupportLeadersOnly"));
		}
		return placement;
	}

	private static @Nullable String nameOf(Map<UUID, String> names, @Nullable UUID id) {
		return id == null ? null : names.get(id);
	}
}
