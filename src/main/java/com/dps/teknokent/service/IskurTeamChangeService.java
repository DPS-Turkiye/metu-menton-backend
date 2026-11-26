package com.dps.teknokent.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IskurPlacementStatus;
import com.dps.teknokent.domain.IskurTeamChangeStatus;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.dto.requests.IskurTeamChangeAskRequest;
import com.dps.teknokent.dto.requests.IskurTeamChangeDeclineRequest;
import com.dps.teknokent.dto.responses.IskurTeamChangeMineResponse;
import com.dps.teknokent.dto.responses.IskurTeamChangeResponse;
import com.dps.teknokent.dto.responses.IskurTeamChangesResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurTeamChangeRequest;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamChangeRequestRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.ProgramCoordination;
import com.dps.teknokent.util.Texts;

/**
 * Team change requests during the trial (student handbook p.04, p.12, p.18: "Ekip değiştirmek istersen 4. haftada
 * belirt; kararı koordinatör verir"). The intern sends a short reason; the program's coordinators see it in the İŞKUR
 * workspace and either move the intern with the usual placement change, which closes the request, or decline it with a
 * note. The intern is told either way.
 */
@Service
public class IskurTeamChangeService {

	private final IskurInternService interns;
	private final CoordinatorProgramService coordinatorProgramService;
	private final ProgramRepository programs;
	private final ProgramCoordinatorRepository programCoordinators;
	private final IskurPlacementRepository placements;
	private final IskurTeamRepository teams;
	private final IskurUnitRepository units;
	private final IskurTeamChangeRequestRepository requests;
	private final AppUserRepository users;
	private final NotificationService notifications;
	private final Clock clock;

	@Autowired
	public IskurTeamChangeService(IskurInternService interns, CoordinatorProgramService coordinatorProgramService,
			ProgramRepository programs, ProgramCoordinatorRepository programCoordinators,
			IskurPlacementRepository placements, IskurTeamRepository teams, IskurUnitRepository units,
			IskurTeamChangeRequestRepository requests, AppUserRepository users, NotificationService notifications) {
		this(interns, coordinatorProgramService, programs, programCoordinators, placements, teams, units, requests,
				users, notifications, Clock.systemUTC());
	}

	IskurTeamChangeService(IskurInternService interns, CoordinatorProgramService coordinatorProgramService,
			ProgramRepository programs, ProgramCoordinatorRepository programCoordinators,
			IskurPlacementRepository placements, IskurTeamRepository teams, IskurUnitRepository units,
			IskurTeamChangeRequestRepository requests, AppUserRepository users, NotificationService notifications,
			Clock clock) {
		this.interns = interns;
		this.coordinatorProgramService = coordinatorProgramService;
		this.programs = programs;
		this.programCoordinators = programCoordinators;
		this.placements = placements;
		this.teams = teams;
		this.units = units;
		this.requests = requests;
		this.users = users;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public IskurTeamChangeMineResponse mine(UUID actorId) {
		Program program = requireInternProgram(actorId);
		IskurTeamChangeRequest latest = requests
				.findFirstByProgramIdAndUserIdOrderByCreatedAtDesc(program.getId(), actorId)
				.orElse(null);
		IskurPlacement placement = placements.findByProgramIdAndUserId(program.getId(), actorId).orElse(null);
		boolean canAsk = mayAsk(program, placement) && (latest == null
				|| latest.getStatus() != IskurTeamChangeStatus.PENDING);
		return new IskurTeamChangeMineResponse(program.getId(), canAsk,
				latest == null ? null : describeOne(latest, placement));
	}

	/** Only while placed and provisional: once the coordinator finalizes the team, the trial is over. */
	@Transactional
	public IskurTeamChangeMineResponse ask(UUID actorId, IskurTeamChangeAskRequest request) {
		Program program = requireInternProgram(actorId);
		programs.findByIdForUpdate(program.getId());
		IskurPlacement placement = placements.findByProgramIdAndUserId(program.getId(), actorId).orElse(null);
		if (placement == null) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurTeamChangeNotPlaced"));
		}
		if (!mayAsk(program, placement)) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurTeamChangeTrialOver"));
		}
		if (requests.findByProgramIdAndUserIdAndStatus(program.getId(), actorId, IskurTeamChangeStatus.PENDING)
				.isPresent()) {
			throw new ConflictException(Texts.forRequest("error.iskurTeamChangePending"));
		}
		requests.save(new IskurTeamChangeRequest(program.getId(), actorId, placement.getTeamId(),
				request.reason().strip(), clock.instant()));
		notifyCoordinators(program, placement, actorId);
		return mine(actorId);
	}

	@Transactional(readOnly = true)
	public IskurTeamChangesResponse list(UUID actorId, UUID programId) {
		Program program = requirePeerProgram(actorId, programId);
		List<IskurTeamChangeRequest> all = requests.findByProgramIdOrderByCreatedAtDesc(program.getId()).stream()
				.sorted(Comparator.comparing((IskurTeamChangeRequest row) -> row
						.getStatus() != IskurTeamChangeStatus.PENDING))
				.toList();
		return new IskurTeamChangesResponse(program.getId(), describe(all));
	}

	@Transactional
	public IskurTeamChangesResponse decline(UUID actorId, UUID programId, UUID requestId,
			IskurTeamChangeDeclineRequest body) {
		Program program = requirePeerProgram(actorId, programId);
		programs.findByIdForUpdate(program.getId());
		// Like asking: an archived program's requests stay as they were.
		if (program.getStatus() == ProgramStatus.COMPLETED) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurProgramArchived"));
		}
		IskurTeamChangeRequest request = requests.findById(requestId)
				.filter(row -> row.getProgramId().equals(program.getId()))
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurTeamChangeNotFound")));
		if (request.getStatus() != IskurTeamChangeStatus.PENDING) {
			throw new ConflictException(Texts.forRequest("error.iskurTeamChangeAnswered"));
		}
		String note = body.note().strip();
		request.setDecisionNote(note);
		request.close(IskurTeamChangeStatus.DECLINED, actorId, clock.instant());
		requests.save(request);
		users.findById(request.getUserId()).ifPresent(intern -> notifications.create(intern,
				NotificationCategory.GENERAL, Texts.get(intern, "notification.iskurTeamChangeDeclined.title"),
				Texts.get(intern, "notification.iskurTeamChangeDeclined.body", note), "/student"));
		return list(actorId, programId);
	}

	/**
	 * In the same transaction as the placement change: a move to another team answers the request, and unplacing the
	 * intern cancels it. A change inside the same team (a new leader, a finalize) leaves it waiting.
	 */
	@EventListener
	public void onPlacementChanged(IskurPlacementChangedEvent event) {
		IskurTeamChangeRequest pending = requests.findByProgramIdAndUserIdAndStatus(event.programId(),
				event.userId(), IskurTeamChangeStatus.PENDING).orElse(null);
		if (pending == null || Objects.equals(event.previousTeamId(), event.teamId())) {
			return;
		}
		Instant now = clock.instant();
		UUID teamId = event.teamId();
		if (teamId == null) {
			pending.close(IskurTeamChangeStatus.CANCELLED, null, now);
			requests.save(pending);
			return;
		}
		pending.close(IskurTeamChangeStatus.MOVED, null, now);
		requests.save(pending);
		String teamName = teams.findById(teamId).map(IskurTeam::getName).orElse("");
		users.findById(event.userId()).ifPresent(intern -> notifications.create(intern, NotificationCategory.GENERAL,
				Texts.get(intern, "notification.iskurTeamChangeMoved.title"),
				Texts.get(intern, "notification.iskurTeamChangeMoved.body", teamName), "/student"));
	}

	private boolean mayAsk(Program program, @Nullable IskurPlacement placement) {
		return placement != null && placement.getStatus() == IskurPlacementStatus.PROVISIONAL
				&& program.getStatus() != ProgramStatus.COMPLETED;
	}

	/** The unit's own coordinator, or every coordinator of the program while the unit has none. */
	private void notifyCoordinators(Program program, IskurPlacement placement, UUID internId) {
		IskurUnit unit = teams.findById(placement.getTeamId())
				.flatMap(team -> units.findById(team.getUnitId()))
				.orElse(null);
		List<UUID> coordinatorIds = ProgramCoordination.coordinatorIds(program, programCoordinators);
		UUID own = unit == null ? null : unit.getCoordinatorId();
		List<UUID> recipients = own != null && coordinatorIds.contains(own) ? List.of(own) : coordinatorIds;
		String internName = users.findById(internId).map(AppUser::getFullName).orElse("");
		String href = "/coordinator/programs/" + program.getId() + "/iskur";
		users.findAllById(recipients).forEach(coordinator -> notifications.create(coordinator,
				NotificationCategory.GENERAL, Texts.get(coordinator, "notification.iskurTeamChangeAsked.title"),
				Texts.get(coordinator, "notification.iskurTeamChangeAsked.body", internName), href));
	}

	private Program requireInternProgram(UUID actorId) {
		return interns.currentProgram(actorId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurNotAnIntern")));
	}

	private Program requirePeerProgram(UUID actorId, UUID programId) {
		Program program = coordinatorProgramService.requireManagedProgram(actorId, programId);
		if (!program.has(ProgramFeature.ISKUR)) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurPeerProgramsOnly"));
		}
		coordinatorProgramService.requireAssigned(actorId, program, ProgramFeature.ISKUR);
		return program;
	}

	/** The intern's own request: only their placement and the one or two teams it names. */
	private IskurTeamChangeResponse describeOne(IskurTeamChangeRequest row, @Nullable IskurPlacement placement) {
		Set<UUID> teamIds = new HashSet<>();
		if (row.getFromTeamId() != null) {
			teamIds.add(row.getFromTeamId());
		}
		UUID current = placement == null ? null : placement.getTeamId();
		if (current != null) {
			teamIds.add(current);
		}
		Map<UUID, String> teamNames = teams.findAllById(teamIds).stream()
				.collect(Collectors.toMap(IskurTeam::getId, IskurTeam::getName));
		AppUser intern = users.findById(row.getUserId()).orElse(null);
		AppUser decider = row.getDecidedBy() == null ? null : users.findById(row.getDecidedBy()).orElse(null);
		return response(row, intern, decider, teamNames, current);
	}

	private static IskurTeamChangeResponse response(IskurTeamChangeRequest row, @Nullable AppUser intern,
			@Nullable AppUser decider, Map<UUID, String> teamNames, @Nullable UUID currentTeamId) {
		return new IskurTeamChangeResponse(row.getId(), row.getUserId(), intern == null ? "" : intern.getFullName(),
				row.getFromTeamId() == null ? null : teamNames.get(row.getFromTeamId()),
				currentTeamId == null ? null : teamNames.get(currentTeamId), row.getReason(), row.getStatus(),
				row.getDecisionNote(), decider == null ? null : decider.getFullName(), row.getCreatedAt(),
				row.getDecidedAt());
	}

	private List<IskurTeamChangeResponse> describe(List<IskurTeamChangeRequest> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Set<UUID> people = new HashSet<>();
		rows.forEach(row -> {
			people.add(row.getUserId());
			if (row.getDecidedBy() != null) {
				people.add(row.getDecidedBy());
			}
		});
		Map<UUID, AppUser> byId = users.findAllById(people).stream()
				.collect(Collectors.toMap(AppUser::getId, Function.identity()));
		UUID programId = rows.getFirst().getProgramId();
		Map<UUID, UUID> currentTeam = new HashMap<>();
		placements.findByProgramId(programId)
				.forEach(placement -> currentTeam.put(placement.getUserId(), placement.getTeamId()));
		Map<UUID, String> teamNames = teams.findByProgramIdOrderBySortOrderAscCreatedAtAsc(programId).stream()
				.collect(Collectors.toMap(IskurTeam::getId, IskurTeam::getName));
		return rows.stream()
				.map(row -> response(row, byId.get(row.getUserId()),
						row.getDecidedBy() == null ? null : byId.get(row.getDecidedBy()), teamNames,
						currentTeam.get(row.getUserId())))
				.toList();
	}
}
