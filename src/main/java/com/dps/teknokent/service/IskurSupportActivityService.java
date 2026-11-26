package com.dps.teknokent.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.AppLanguage;
import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.dto.requests.IskurSupportActivityRequest;
import com.dps.teknokent.dto.responses.IskurSupportActivitiesResponse;
import com.dps.teknokent.entity.IskurSupportActivity;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.IskurSupportActivityRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

/**
 * The support-activity pool of a peer program (rules frame 322:3413): what an intern can pick for peer mentoring in a
 * week without a meeting. Its coordinators keep it; the rules frame's five examples are the default pool.
 */
@Service
public class IskurSupportActivityService {

	/** The rules frame lists five activities. */
	static final int DEFAULT_ACTIVITIES = 5;
	private static final Locale TR = Locale.forLanguageTag("tr");

	private final CoordinatorProgramService coordinatorProgramService;
	private final ProgramRepository programs;
	private final IskurSupportActivityRepository activities;
	private final Clock clock;

	@Autowired
	public IskurSupportActivityService(CoordinatorProgramService coordinatorProgramService,
			ProgramRepository programs, IskurSupportActivityRepository activities) {
		this(coordinatorProgramService, programs, activities, Clock.systemUTC());
	}

	IskurSupportActivityService(CoordinatorProgramService coordinatorProgramService, ProgramRepository programs,
			IskurSupportActivityRepository activities, Clock clock) {
		this.coordinatorProgramService = coordinatorProgramService;
		this.programs = programs;
		this.activities = activities;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public IskurSupportActivitiesResponse list(UUID actorId, UUID programId) {
		return build(requireProgram(actorId, programId).getId());
	}

	/** Fills an empty pool with the rules frame's five activities. */
	@Transactional
	public IskurSupportActivitiesResponse createDefaults(UUID actorId, UUID programId) {
		Program program = requireEditable(actorId, programId);
		if (!activities.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()).isEmpty()) {
			throw new ConflictException(Texts.forRequest("error.iskurSupportPoolExists"));
		}
		Instant now = clock.instant();
		for (int i = 1; i <= DEFAULT_ACTIVITIES; i++) {
			activities.save(new IskurSupportActivity(program.getId(),
					Texts.get(AppLanguage.TR, "iskur.defaultSupport" + i), i - 1, actorId, now));
		}
		return build(program.getId());
	}

	@Transactional
	public IskurSupportActivitiesResponse create(UUID actorId, UUID programId, IskurSupportActivityRequest request) {
		Program program = requireEditable(actorId, programId);
		List<IskurSupportActivity> pool = activities.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId());
		String title = request.title().strip();
		requireFreeTitle(pool, title, null);
		int order = pool.stream().mapToInt(IskurSupportActivity::getSortOrder).max().orElse(-1) + 1;
		IskurSupportActivity activity = new IskurSupportActivity(program.getId(), title, order, actorId,
				clock.instant());
		activity.setDescription(TextUtils.normalizeOptional(request.description()));
		if (request.active() != null) {
			activity.setActive(request.active());
		}
		activities.save(activity);
		return build(program.getId());
	}

	@Transactional
	public IskurSupportActivitiesResponse update(UUID actorId, UUID programId, UUID activityId,
			IskurSupportActivityRequest request) {
		Program program = requireEditable(actorId, programId);
		IskurSupportActivity activity = requireActivity(program, activityId);
		String title = request.title().strip();
		requireFreeTitle(activities.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()), title,
				activityId);
		activity.setTitle(title);
		activity.setDescription(TextUtils.normalizeOptional(request.description()));
		if (request.active() != null) {
			activity.setActive(request.active());
		}
		activity.setUpdatedAt(clock.instant());
		activities.save(activity);
		return build(program.getId());
	}

	/** Removes the activity; weeks that already used it keep its title. Retiring it is {@code active = false}. */
	@Transactional
	public IskurSupportActivitiesResponse delete(UUID actorId, UUID programId, UUID activityId) {
		Program program = requireEditable(actorId, programId);
		activities.delete(requireActivity(program, activityId));
		return build(program.getId());
	}

	private IskurSupportActivitiesResponse build(UUID programId) {
		return new IskurSupportActivitiesResponse(programId,
				activities.findByProgramIdOrderBySortOrderAscCreatedAtAsc(programId).stream()
						.map(activity -> new IskurSupportActivitiesResponse.Activity(activity.getId(),
								activity.getTitle(), activity.getDescription(), activity.isActive(),
								activity.getSortOrder()))
						.toList());
	}

	private Program requireProgram(UUID actorId, UUID programId) {
		Program program = coordinatorProgramService.requireManagedProgram(actorId, programId);
		if (!program.has(ProgramFeature.ISKUR)) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurPeerProgramsOnly"));
		}
		return program;
	}

	/** Locked for the transaction, so two coordinators cannot both add the same title or both seed the pool. */
	private Program requireEditable(UUID actorId, UUID programId) {
		requireProgram(actorId, programId);
		Program program = programs.findByIdForUpdate(programId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurProgramNotFound")));
		if (program.getStatus() == ProgramStatus.COMPLETED) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurProgramArchived"));
		}
		return program;
	}

	private IskurSupportActivity requireActivity(Program program, UUID activityId) {
		return activities.findById(activityId)
				.filter(activity -> activity.getProgramId().equals(program.getId()))
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurSupportNotFound")));
	}

	/** Titles are Turkish text, so they compare under Turkish case rules: "OFİS" is "ofis", "SAATI" is not. */
	private static void requireFreeTitle(List<IskurSupportActivity> pool, String title, @Nullable UUID except) {
		String wanted = title.toLowerCase(TR);
		boolean taken = pool.stream()
				.filter(activity -> except == null || !activity.getId().equals(except))
				.anyMatch(activity -> activity.getTitle().toLowerCase(TR).equals(wanted));
		if (taken) {
			throw new ConflictException(Texts.forRequest("error.iskurSupportTitleTaken"));
		}
	}
}
