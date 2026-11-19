package com.dps.teknokent.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.ProgramMentorStatus;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramMentor;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramMentorRepository;
import com.dps.teknokent.repository.ProgramRepository;

import lombok.RequiredArgsConstructor;

/**
 * Which İŞKUR program an intern works in, and where they are placed: the one lookup every intern-side İŞKUR screen
 * starts from (onboarding, the team reveal, the weekly work, the channels). An intern is who the coordinator's
 * placement pool counts: an approved student of a peer-mentoring program, or one of its accepted peer mentors. A
 * program counts only once its coordinators have set up İŞKUR units; before that it has no workspace to send anyone to.
 */
@Service
@RequiredArgsConstructor
public class IskurInternService {

	private final AppUserRepository users;
	private final ProgramRepository programs;
	private final ProgramMentorRepository programMentors;
	private final IskurUnitRepository units;
	private final IskurPlacementRepository placements;

	/**
	 * The İŞKUR program the person works in; when they qualify for more than one, the newest. Archived programs and
	 * programs without İŞKUR units do not count.
	 */
	@Transactional(readOnly = true)
	public Optional<Program> currentProgram(UUID userId) {
		return users.findById(userId).flatMap(this::currentProgram);
	}

	/** The person's placement in {@link #currentProgram(UUID)}, if a coordinator has placed them. */
	@Transactional(readOnly = true)
	public Optional<IskurPlacement> currentPlacement(UUID userId) {
		return currentProgram(userId)
				.flatMap(program -> placements.findByProgramIdAndUserId(program.getId(), userId));
	}

	/**
	 * Everyone the program's İŞKUR workspace counts as an intern, whether placed or not: its approved students and its
	 * accepted peer mentors.
	 */
	@Transactional(readOnly = true)
	public Set<UUID> internIds(UUID programId) {
		Set<UUID> ids = new LinkedHashSet<>();
		users.findByProgramIdAndPermissionAndStatusOrderByCreatedAtAsc(programId, UserRole.MENTEE,
				UserAccountStatus.APPROVED).forEach(student -> ids.add(student.getId()));
		programMentors.findByProgramIdOrderByCreatedAtAsc(programId).stream()
				.filter(membership -> membership.getStatus() == ProgramMentorStatus.ACCEPTED)
				.forEach(membership -> ids.add(membership.getMentorId()));
		return ids;
	}

	private Optional<Program> currentProgram(AppUser user) {
		Set<UUID> candidates = new LinkedHashSet<>();
		UUID studentOf = user.getProgramId();
		if (studentOf != null && user.has(UserRole.MENTEE) && user.getStatus() == UserAccountStatus.APPROVED) {
			candidates.add(studentOf);
		}
		programMentors.findByMentorIdOrderByCreatedAtAsc(user.getId()).stream()
				.filter(membership -> membership.getStatus() == ProgramMentorStatus.ACCEPTED)
				.map(ProgramMentor::getProgramId)
				.forEach(candidates::add);
		if (candidates.isEmpty()) {
			return Optional.empty();
		}
		return programs.findAllById(candidates).stream()
				.filter(program -> program.getProgramType() == ProgramType.PEER_MENTORING)
				.filter(program -> program.getStatus() != ProgramStatus.COMPLETED)
				.filter(program -> units.existsByProgramId(program.getId()))
				.max(Comparator
						.comparing(Program::getCreatedAt, Comparator.nullsFirst(Comparator.<Instant>naturalOrder()))
						.thenComparing(Program::getId));
	}
}
