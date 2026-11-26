package com.dps.teknokent.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinatorFeature;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.repository.ProgramCoordinatorFeatureRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.util.ProgramCoordination;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;

/**
 * Which of a semester's features a person may open (Hocam, 26 Nov: coordinators per feature). The admin opens all of
 * them. A semester's coordinator opens the features assigned to them; its creator all of them. Programs of the older
 * types have no assignments, so their coordinators keep opening everything the program runs.
 */
@Service
@RequiredArgsConstructor
public class ProgramFeatureAccess {

	private final ProgramCoordinatorRepository coordinators;
	private final ProgramCoordinatorFeatureRepository assignments;
	private final DomainLookupService domainLookupService;

	@Transactional(readOnly = true)
	public Set<ProgramFeature> of(AppUser actor, Program program) {
		Set<ProgramFeature> runs = program.features();
		if (actor.has(UserRole.ADMIN)) {
			return runs;
		}
		if (!ProgramCoordination.coordinates(program, actor.getId(), coordinators)) {
			return EnumSet.noneOf(ProgramFeature.class);
		}
		if (program.getProgramType() != ProgramType.SEMESTER || actor.getId().equals(program.getCreatedBy())) {
			return runs;
		}
		Set<ProgramFeature> held = EnumSet.noneOf(ProgramFeature.class);
		assignments.findByProgramIdAndUserId(program.getId(), actor.getId()).stream()
				.map(ProgramCoordinatorFeature::getFeature)
				.filter(runs::contains)
				.forEach(held::add);
		return held;
	}

	/**
	 * For a screen of one feature, once the caller is known to run the program and the program to run the feature:
	 * a coordinator who was not given that feature gets a 403.
	 */
	@Transactional(readOnly = true)
	public void requireAssigned(UUID actorId, Program program, ProgramFeature feature) {
		if (!of(domainLookupService.requireUser(actorId), program).contains(feature)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, Texts.forRequest("error.programFeatureNotYours"));
		}
	}

	/** Each coordinator's features in {@code program}, by the same rule as {@link #of}. */
	@Transactional(readOnly = true)
	public Map<UUID, List<ProgramFeature>> ofCoordinators(Program program, Collection<UUID> coordinatorIds) {
		Set<ProgramFeature> runs = program.features();
		Map<UUID, Set<ProgramFeature>> held = new HashMap<>();
		if (program.getProgramType() == ProgramType.SEMESTER) {
			assignments.findByProgramId(program.getId()).forEach(row -> held
					.computeIfAbsent(row.getUserId(), id -> EnumSet.noneOf(ProgramFeature.class))
					.add(row.getFeature()));
		}
		Map<UUID, List<ProgramFeature>> features = new HashMap<>();
		for (UUID id : coordinatorIds) {
			boolean everything = program.getProgramType() != ProgramType.SEMESTER || id.equals(program.getCreatedBy());
			Set<ProgramFeature> mine = everything ? runs : held.getOrDefault(id, Set.of());
			features.put(id, runs.stream().filter(mine::contains).toList());
		}
		return features;
	}

	/**
	 * The features a semester coordinator is to run: what was asked for, or every feature of the semester when
	 * nothing was. Empty, or one the semester does not run, is a 400. Programs of the older types assign nothing.
	 */
	public Set<ProgramFeature> checkAssignment(Program program, @Nullable Set<ProgramFeature> requested) {
		if (program.getProgramType() != ProgramType.SEMESTER) {
			return EnumSet.noneOf(ProgramFeature.class);
		}
		Set<ProgramFeature> runs = program.features();
		if (requested == null) {
			return runs;
		}
		if (requested.isEmpty() || !runs.containsAll(requested)) {
			throw new InvalidRequestException(Texts.forRequest("error.coordinatorFeaturesInvalid"),
					ProgramFeature.INVALID_SET_CODE);
		}
		Set<ProgramFeature> chosen = EnumSet.noneOf(ProgramFeature.class);
		chosen.addAll(requested);
		return chosen;
	}

	/** Makes {@code userId}'s features in the program exactly {@code chosen} (from {@link #checkAssignment}). */
	@Transactional
	public void assign(Program program, UUID userId, Set<ProgramFeature> chosen) {
		Set<ProgramFeature> had = EnumSet.noneOf(ProgramFeature.class);
		for (ProgramCoordinatorFeature row : assignments.findByProgramIdAndUserId(program.getId(), userId)) {
			if (chosen.contains(row.getFeature())) {
				had.add(row.getFeature());
			} else {
				assignments.delete(row);
			}
		}
		chosen.stream()
				.filter(feature -> !had.contains(feature))
				.forEach(feature -> assignments.save(new ProgramCoordinatorFeature(program.getId(), userId, feature)));
	}

	/** A coordinator left the program: their features there go too. */
	@Transactional
	public void unassign(UUID programId, UUID userId) {
		assignments.deleteByProgramIdAndUserId(programId, userId);
	}
}
