package com.dps.teknokent.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramMentorStatus;
import com.dps.teknokent.domain.ProgramStatus;
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
 * starts from (onboarding, the team reveal, the weekly work, the channels). Everyone who comes into hocam is an İŞKUR
 * student (Arya, 27 Nov): an intern is any approved student of a program that runs İŞKUR, or one of its accepted peer
 * mentors from before badges. Nobody is asked or listed first; the old e-mail list decides nothing. A program counts
 * only once its coordinators have set up İŞKUR units; before that it has no workspace to send anyone to.
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
				.map(ProgramMentor::getMentorId)
				.forEach(ids::add);
		return ids;
	}

	/**
	 * Who among these people can give peer mentoring in the program: a badge holder (the badge grants MENTOR) or an
	 * accepted peer mentor from before badges. Every other intern works in İŞKUR with their badge still pending.
	 */
	@Transactional(readOnly = true)
	public Set<UUID> peerMentorIds(UUID programId, Collection<AppUser> people) {
		Set<UUID> accepted = programMentors.findByProgramIdOrderByCreatedAtAsc(programId).stream()
				.filter(membership -> membership.getStatus() == ProgramMentorStatus.ACCEPTED)
				.map(ProgramMentor::getMentorId)
				.collect(Collectors.toSet());
		return people.stream()
				.filter(person -> person.has(UserRole.MENTOR) || accepted.contains(person.getId()))
				.map(AppUser::getId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** Whether this one person is an intern of the program; the single-person form of {@link #internIds(UUID)}. */
	@Transactional(readOnly = true)
	public boolean isIntern(UUID programId, UUID userId) {
		return users.findById(userId).filter(user -> inProgram(programId, user)).isPresent();
	}

	private Optional<Program> currentProgram(AppUser user) {
		Set<UUID> candidates = new LinkedHashSet<>();
		if (isApprovedStudent(user)) {
			candidates.add(user.getProgramId());
		}
		programMentors.findByMentorIdOrderByCreatedAtAsc(user.getId()).stream()
				.filter(membership -> membership.getStatus() == ProgramMentorStatus.ACCEPTED)
				.map(ProgramMentor::getProgramId)
				.forEach(candidates::add);
		if (candidates.isEmpty()) {
			return Optional.empty();
		}
		return programs.findAllById(candidates).stream()
				.filter(program -> program.has(ProgramFeature.ISKUR))
				.filter(program -> program.getStatus() != ProgramStatus.COMPLETED)
				.filter(program -> units.existsByProgramId(program.getId()))
				.max(Comparator
						.comparing(Program::getCreatedAt, Comparator.nullsFirst(Comparator.<Instant>naturalOrder()))
						.thenComparing(Program::getId));
	}

	private boolean inProgram(UUID programId, AppUser user) {
		if (isApprovedStudent(user) && programId.equals(user.getProgramId())) {
			return true;
		}
		return programMentors.findByMentorIdOrderByCreatedAtAsc(user.getId()).stream()
				.anyMatch(membership -> membership.getStatus() == ProgramMentorStatus.ACCEPTED
						&& programId.equals(membership.getProgramId()));
	}

	private static boolean isApprovedStudent(AppUser user) {
		return user.getProgramId() != null && user.has(UserRole.MENTEE)
				&& user.getStatus() == UserAccountStatus.APPROVED;
	}
}
