package com.dps.teknokent.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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
import com.dps.teknokent.repository.IskurInternEmailRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramMentorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.EmailDomains;

import lombok.RequiredArgsConstructor;

/**
 * Which İŞKUR program an intern works in, and where they are placed: the one lookup every intern-side İŞKUR screen
 * starts from (onboarding, the team reveal, the weekly work, the channels). An intern is an account of a
 * peer-mentoring program (an approved student, or an accepted peer mentor) whose e-mail is on that program's İŞKUR
 * intern list; everyone else in the program is a plain student. A program counts only once its coordinators have set up
 * İŞKUR units; before that it has no workspace to send anyone to.
 */
@Service
@RequiredArgsConstructor
public class IskurInternService {

	private final AppUserRepository users;
	private final ProgramRepository programs;
	private final ProgramMentorRepository programMentors;
	private final IskurUnitRepository units;
	private final IskurPlacementRepository placements;
	private final IskurInternEmailRepository internEmails;

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
	 * accepted peer mentors whose e-mail is on the program's intern list.
	 */
	@Transactional(readOnly = true)
	public Set<UUID> internIds(UUID programId) {
		Set<String> listed = Set.copyOf(internEmails.findEmailsByProgramId(programId));
		if (listed.isEmpty()) {
			return new LinkedHashSet<>();
		}
		Set<UUID> ids = new LinkedHashSet<>();
		users.findByProgramIdAndPermissionAndStatusOrderByCreatedAtAsc(programId, UserRole.MENTEE,
				UserAccountStatus.APPROVED).stream()
				.filter(student -> listed.contains(EmailDomains.normalizeEmail(student.getEmail())))
				.forEach(student -> ids.add(student.getId()));
		List<UUID> mentorIds = programMentors.findByProgramIdOrderByCreatedAtAsc(programId).stream()
				.filter(membership -> membership.getStatus() == ProgramMentorStatus.ACCEPTED)
				.map(ProgramMentor::getMentorId)
				.filter(id -> !ids.contains(id))
				.toList();
		Set<UUID> listedMentors = users.findAllById(mentorIds).stream()
				.filter(mentor -> listed.contains(EmailDomains.normalizeEmail(mentor.getEmail())))
				.map(AppUser::getId)
				.collect(Collectors.toSet());
		mentorIds.stream().filter(listedMentors::contains).forEach(ids::add);
		return ids;
	}

	/** Whether this one person is an intern of the program; the single-person form of {@link #internIds(UUID)}. */
	@Transactional(readOnly = true)
	public boolean isIntern(UUID programId, UUID userId) {
		return users.findById(userId)
				.filter(user -> isListed(programId, user))
				.filter(user -> inProgram(programId, user))
				.isPresent();
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
		candidates.removeIf(programId -> !isListed(programId, user));
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

	private boolean isListed(UUID programId, AppUser user) {
		String email = EmailDomains.normalizeEmail(user.getEmail());
		return !email.isEmpty() && internEmails.existsByProgramIdAndEmail(programId, email);
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
