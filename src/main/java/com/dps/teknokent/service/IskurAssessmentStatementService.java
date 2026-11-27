package com.dps.teknokent.service;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IskurAssessmentStatus;
import com.dps.teknokent.domain.IskurMentoringDecision;
import com.dps.teknokent.domain.IskurWeeklySplit;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.TrainingState;
import com.dps.teknokent.dto.requests.IskurAssessmentDecisionRequest;
import com.dps.teknokent.dto.requests.IskurAssessmentStatementRequest;
import com.dps.teknokent.dto.requests.IskurPlacementRequest;
import com.dps.teknokent.dto.responses.IskurAssessmentMineResponse;
import com.dps.teknokent.dto.responses.IskurAssessmentStatementResponse;
import com.dps.teknokent.dto.responses.IskurAssessmentStatementsResponse;
import com.dps.teknokent.dto.responses.StudentTrainingResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurAssessmentStatement;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurAssessmentStatementRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

/**
 * Assessment statements (İŞKUR work model, 27 Nov: "Değerlendirmeyi geçemeyen stajyer nedenini koordinatöre yazar,
 * kararı koordinatör verir"). A placed intern without the Akran Mentoru badge sends a reason and a short text; the
 * program's coordinators see it in the İŞKUR workspace and answer with one decision: the intern's weekly mentoring
 * time, written through the usual placement change so the week's split follows; optionally a Prism retake; and a note.
 * The intern is told in the app, as with team change requests.
 */
@Service
public class IskurAssessmentStatementService {

	private final IskurInternService interns;
	private final IskurWorkspaceService workspace;
	private final TrainingService training;
	private final PrismTrainingService prismTraining;
	private final ProgramRepository programs;
	private final ProgramCoordinatorRepository programCoordinators;
	private final IskurPlacementRepository placements;
	private final IskurTeamRepository teams;
	private final IskurUnitRepository units;
	private final IskurAssessmentStatementRepository statements;
	private final AppUserRepository users;
	private final NotificationService notifications;
	private final Clock clock;

	@Autowired
	public IskurAssessmentStatementService(IskurInternService interns, IskurWorkspaceService workspace,
			TrainingService training, PrismTrainingService prismTraining, ProgramRepository programs,
			ProgramCoordinatorRepository programCoordinators, IskurPlacementRepository placements,
			IskurTeamRepository teams, IskurUnitRepository units, IskurAssessmentStatementRepository statements,
			AppUserRepository users, NotificationService notifications) {
		this(interns, workspace, training, prismTraining, programs, programCoordinators, placements, teams, units,
				statements, users, notifications, Clock.systemUTC());
	}

	IskurAssessmentStatementService(IskurInternService interns, IskurWorkspaceService workspace,
			TrainingService training, PrismTrainingService prismTraining, ProgramRepository programs,
			ProgramCoordinatorRepository programCoordinators, IskurPlacementRepository placements,
			IskurTeamRepository teams, IskurUnitRepository units, IskurAssessmentStatementRepository statements,
			AppUserRepository users, NotificationService notifications, Clock clock) {
		this.interns = interns;
		this.workspace = workspace;
		this.training = training;
		this.prismTraining = prismTraining;
		this.programs = programs;
		this.programCoordinators = programCoordinators;
		this.placements = placements;
		this.teams = teams;
		this.units = units;
		this.statements = statements;
		this.users = users;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public IskurAssessmentMineResponse mine(UUID actorId) {
		Program program = requireInternProgram(actorId);
		IskurAssessmentStatement latest = statements
				.findFirstByProgramIdAndUserIdOrderByCreatedAtDesc(program.getId(), actorId)
				.orElse(null);
		boolean canSend = maySend(program, actorId) && (latest == null
				|| latest.getStatus() != IskurAssessmentStatus.PENDING);
		return new IskurAssessmentMineResponse(program.getId(), canSend, latest == null ? null : own(latest));
	}

	@Transactional
	public IskurAssessmentMineResponse send(UUID actorId, IskurAssessmentStatementRequest request) {
		Program program = requireInternProgram(actorId);
		programs.findByIdForUpdate(program.getId());
		IskurPlacement placement = placements.findByProgramIdAndUserId(program.getId(), actorId)
				.orElseThrow(() -> new InvalidRequestException(Texts.forRequest("error.iskurAssessmentNotPlaced")));
		if (!maySend(program, actorId)) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurAssessmentNotOpen"));
		}
		if (statements.findByProgramIdAndUserIdAndStatus(program.getId(), actorId, IskurAssessmentStatus.PENDING)
				.isPresent()) {
			throw new ConflictException(Texts.forRequest("error.iskurAssessmentPending"));
		}
		statements.save(new IskurAssessmentStatement(program.getId(), actorId, request.reason(),
				request.text().strip(), clock.instant()));
		notifyCoordinators(program, placement, actorId);
		return mine(actorId);
	}

	@Transactional(readOnly = true)
	public IskurAssessmentStatementsResponse list(UUID actorId, UUID programId) {
		Program program = workspace.requireProgram(actorId, programId);
		List<IskurAssessmentStatement> all = statements.findByProgramIdOrderByCreatedAtDesc(program.getId()).stream()
				.sorted(Comparator.comparing((IskurAssessmentStatement row) -> row
						.getStatus() != IskurAssessmentStatus.PENDING))
				.toList();
		long pending = all.stream().filter(row -> row.getStatus() == IskurAssessmentStatus.PENDING).count();
		return new IskurAssessmentStatementsResponse(program.getId(), pending, describe(program.getId(), all));
	}

	/**
	 * One transaction: a new mentoring time goes through {@link IskurWorkspaceService#place} with the intern's team,
	 * role, leader and days as they are, so its checks and its event stay the only way a placement changes. A retake
	 * is offered only while the intern waits for a Prism decision; before that they can take the test anyway.
	 */
	@Transactional
	public IskurAssessmentStatementsResponse decide(UUID actorId, UUID programId, UUID statementId,
			IskurAssessmentDecisionRequest body) {
		Program program = workspace.requireProgram(actorId, programId);
		programs.findByIdForUpdate(program.getId());
		if (program.getStatus() == ProgramStatus.COMPLETED) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurProgramArchived"));
		}
		IskurAssessmentStatement statement = statements.findById(statementId)
				.filter(row -> row.getProgramId().equals(program.getId()))
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurAssessmentNotFound")));
		if (statement.getStatus() != IskurAssessmentStatus.PENDING) {
			throw new ConflictException(Texts.forRequest("error.iskurAssessmentAnswered"));
		}
		IskurMentoringDecision mentoring = body.mentoring();
		@Nullable
		String note = TextUtils.normalizeOptional(body.note());
		if (mentoring == IskurMentoringDecision.KEEP && !body.openRetake() && note == null) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurAssessmentEmptyDecision"));
		}
		UUID internId = statement.getUserId();
		IskurPlacement placement = placements.findByProgramIdAndUserId(program.getId(), internId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurPlacementNotFound")));
		if (mentoring != IskurMentoringDecision.KEEP) {
			statement.setMentoringMinutes(changeMentoring(actorId, program, placement, mentoring, body));
		}
		if (body.openRetake()) {
			StudentTrainingResponse state = training.forStudent(internId);
			if (state.state() != TrainingState.PRISM_PENDING || !program.getId().equals(state.programId())) {
				throw new InvalidRequestException(Texts.forRequest("error.iskurAssessmentNoRetake"));
			}
			prismTraining.askToRetake(program.getId(), internId, actorId);
		}
		statement.setMentoringDecision(mentoring);
		statement.setRetakeOpened(body.openRetake());
		statement.setDecisionNote(note);
		statement.setStatus(IskurAssessmentStatus.DECIDED);
		statement.setDecidedBy(actorId);
		statement.setDecidedAt(clock.instant());
		statements.save(statement);
		users.findById(internId).ifPresent(intern -> notifications.create(intern, NotificationCategory.GENERAL,
				Texts.get(intern, "notification.iskurAssessmentDecided.title"),
				Texts.get(intern, "notification.iskurAssessmentDecided.body"), PrismTrainingService.TRAINING_PAGE));
		return list(actorId, programId);
	}

	/** Unplacing the intern cancels their waiting statement, in the same transaction. */
	@EventListener
	public void onPlacementChanged(IskurPlacementChangedEvent event) {
		if (event.teamId() != null) {
			return;
		}
		statements.findByProgramIdAndUserIdAndStatus(event.programId(), event.userId(),
				IskurAssessmentStatus.PENDING).ifPresent(pending -> {
					pending.setStatus(IskurAssessmentStatus.CANCELLED);
					pending.setDecidedAt(clock.instant());
					statements.save(pending);
				});
	}

	/** The intern's weekly mentoring minutes after the change; null when their week has no day count yet. */
	private @Nullable Integer changeMentoring(UUID actorId, Program program, IskurPlacement placement,
			IskurMentoringDecision mentoring, IskurAssessmentDecisionRequest body) {
		Integer override = null;
		if (mentoring == IskurMentoringDecision.SET) {
			override = body.mentoringMinutes();
			if (override == null) {
				throw new InvalidRequestException(Texts.forRequest("error.iskurMentoringMinutes"));
			}
		}
		Integer days = placement.getWeeklyDays() == null ? null : placement.getWeeklyDays().intValue();
		workspace.place(actorId, program.getId(), placement.getUserId(), new IskurPlacementRequest(actorId,
				placement.getTeamId(), placement.getTeamRole(), placement.getLeaderId(), days, override));
		return days == null ? null : IskurWeeklySplit.of(days, override).mentoringMinutes();
	}

	/** Placed, without the badge, in a program that is still running. */
	private boolean maySend(Program program, UUID userId) {
		if (program.getStatus() == ProgramStatus.COMPLETED
				|| placements.findByProgramIdAndUserId(program.getId(), userId).isEmpty()) {
			return false;
		}
		return users.findById(userId)
				.map(user -> interns.peerMentorIds(program.getId(), List.of(user)).isEmpty())
				.orElse(false);
	}

	private void notifyCoordinators(Program program, IskurPlacement placement, UUID internId) {
		List<UUID> recipients = IskurTeamChangeService.unitCoordinatorOrAll(program, placement.getTeamId(), teams,
				units, programCoordinators);
		String internName = users.findById(internId).map(AppUser::getFullName).orElse("");
		String href = "/coordinator/programs/" + program.getId() + "/iskur";
		users.findAllById(recipients).forEach(coordinator -> notifications.create(coordinator,
				NotificationCategory.GENERAL, Texts.get(coordinator, "notification.iskurAssessmentSent.title"),
				Texts.get(coordinator, "notification.iskurAssessmentSent.body", internName), href));
	}

	private Program requireInternProgram(UUID actorId) {
		return interns.currentProgram(actorId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurNotAnIntern")));
	}

	/** The intern's own copy: their statement and the decision, without the coordinator's view of them. */
	private IskurAssessmentStatementResponse own(IskurAssessmentStatement row) {
		AppUser decider = row.getDecidedBy() == null ? null : users.findById(row.getDecidedBy()).orElse(null);
		return response(row, null, decider, null, null, null, false);
	}

	private List<IskurAssessmentStatementResponse> describe(UUID programId, List<IskurAssessmentStatement> rows) {
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
		Set<UUID> internIds = rows.stream().map(IskurAssessmentStatement::getUserId).collect(Collectors.toSet());
		Set<UUID> mentors = interns.peerMentorIds(programId,
				internIds.stream().map(byId::get).filter(user -> user != null).toList());
		Map<UUID, IskurPlacement> placementOf = new HashMap<>();
		placements.findByProgramId(programId).forEach(placement -> placementOf.put(placement.getUserId(), placement));
		Map<UUID, String> teamNames = teams.findByProgramIdOrderBySortOrderAscCreatedAtAsc(programId).stream()
				.collect(Collectors.toMap(IskurTeam::getId, IskurTeam::getName));
		return rows.stream().map(row -> {
			IskurPlacement placement = placementOf.get(row.getUserId());
			// Only a waiting statement needs the live state; it decides whether a retake can be offered.
			TrainingState state = row.getStatus() == IskurAssessmentStatus.PENDING
					? training.forStudent(row.getUserId()).state()
					: null;
			return response(row, byId.get(row.getUserId()),
					row.getDecidedBy() == null ? null : byId.get(row.getDecidedBy()), placement,
					placement == null ? null : teamNames.get(placement.getTeamId()), state,
					mentors.contains(row.getUserId()));
		}).toList();
	}

	private static IskurAssessmentStatementResponse response(IskurAssessmentStatement row, @Nullable AppUser intern,
			@Nullable AppUser decider, @Nullable IskurPlacement placement, @Nullable String teamName,
			@Nullable TrainingState trainingState, boolean peerMentor) {
		Integer days = null;
		Integer current = null;
		if (placement != null && placement.getWeeklyDays() != null) {
			days = placement.getWeeklyDays().intValue();
			current = IskurWeeklySplit.of(days, placement.getMentoringMinutes()).mentoringMinutes();
		}
		return new IskurAssessmentStatementResponse(row.getId(), row.getUserId(),
				intern == null ? "" : intern.getFullName(), teamName, days, current,
				placement != null && placement.getMentoringMinutes() == null, trainingState, peerMentor,
				row.getReason(), row.getText(), row.getStatus(), row.getMentoringDecision(),
				row.getMentoringMinutes(), row.isRetakeOpened(), row.getDecisionNote(),
				decider == null ? null : decider.getFullName(), row.getCreatedAt(), row.getDecidedAt());
	}
}
