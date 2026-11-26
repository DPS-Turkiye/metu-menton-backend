package com.dps.teknokent.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.ProgramBlocker;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.responses.StudentProgramChangeResponse;
import com.dps.teknokent.dto.responses.StudentProgramResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ProgramWorkOpenException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.security.AdminAccess;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kullanıcılar › Öğrenciler, admin only (Hocam, 26 Nov): "Programa ekle" puts a student into a semester (moving them
 * if they are in another) and "Programdan çıkar" takes them out. Work still open in their semester refuses both with
 * the work listed, so nobody's mentorship, request, report, placement or meeting ends without a word; nothing is
 * deleted, and an Akran mentor keeps mentoring in the semester they move to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStudentProgramService {

	public static final String ALREADY_IN_PROGRAM = "ALREADY_IN_PROGRAM";
	public static final String NOT_IN_PROGRAM = "NOT_IN_PROGRAM";

	private final DomainLookupService domainLookupService;
	private final AppUserRepository users;
	private final ProgramRepository programs;
	private final ProgramWorkBlockers workBlockers;
	private final BadgeService badges;

	@Transactional(readOnly = true)
	public StudentProgramResponse overview(UUID actorId, UUID userId) {
		requireAdmin(actorId);
		AppUser student = requireStudent(userId);
		Optional<Program> current = currentOf(student);
		List<StudentProgramResponse.ProgramRef> targets = programs.findByProgramType(ProgramType.SEMESTER).stream()
				.filter(program -> program.getStatus() != ProgramStatus.COMPLETED)
				.filter(program -> current.map(found -> !found.getId().equals(program.getId())).orElse(true))
				.sorted(Comparator.comparing(Program::getCreatedAt).reversed())
				.map(AdminStudentProgramService::ref)
				.toList();
		return new StudentProgramResponse(current.map(AdminStudentProgramService::ref).orElse(null), targets,
				current.map(program -> workBlockers.forPerson(program, student)).orElse(List.of()));
	}

	/** "Programa ekle"; a student already in a semester moves, once nothing is open there. */
	@Transactional
	public StudentProgramChangeResponse move(UUID actorId, UUID userId, UUID programId) {
		requireAdmin(actorId);
		AppUser student = requireStudent(userId);
		Program target = programs.findById(programId)
				.orElseThrow(() -> new ResourceNotFoundException("Program not found: " + programId));
		if (target.getProgramType() != ProgramType.SEMESTER || target.getStatus() == ProgramStatus.COMPLETED) {
			throw new InvalidRequestException(Texts.forRequest("error.studentProgramTargetInvalid"));
		}
		Optional<Program> current = currentOf(student);
		if (current.isPresent() && current.get().getId().equals(target.getId())) {
			throw new ConflictException(Texts.forRequest("error.studentAlreadyInProgram"), ALREADY_IN_PROGRAM);
		}
		boolean movedMentor = current.isPresent() && leave(actorId, student, current.get(), target);
		return change(actorId, student, current.orElse(null), target, movedMentor);
	}

	/** "Programdan çıkar": the student is in no semester afterwards. */
	@Transactional
	public StudentProgramChangeResponse remove(UUID actorId, UUID userId) {
		requireAdmin(actorId);
		AppUser student = requireStudent(userId);
		Program current = currentOf(student)
				.orElseThrow(() -> new ConflictException(Texts.forRequest("error.studentNotInProgram"),
						NOT_IN_PROGRAM));
		boolean movedMentor = leave(actorId, student, current, null);
		return change(actorId, student, current, null, movedMentor);
	}

	/** Refused while work is open in {@code from}; otherwise their Akran mentoring there ends (or moves). */
	private boolean leave(UUID actorId, AppUser student, Program from, @Nullable Program to) {
		List<ProgramBlocker> open = workBlockers.forPerson(from, student);
		if (!open.isEmpty()) {
			throw new ProgramWorkOpenException(Texts.forRequest("error.studentProgramWorkOpen"), open);
		}
		return badges.movePeerMentor(student, from, to, actorId);
	}

	private StudentProgramChangeResponse change(UUID actorId, AppUser student, @Nullable Program from,
			@Nullable Program to, boolean movedMentor) {
		student.setProgramId(to == null ? null : to.getId());
		users.save(student);
		UUID fromId = from == null ? null : from.getId();
		UUID toId = to == null ? null : to.getId();
		log.info("Admin {} changed the program of user {} from {} to {} (peer mentor moved: {})", actorId,
				student.getId(), fromId, toId, movedMentor);
		return new StudentProgramChangeResponse(student.getId(), fromId, toId, movedMentor);
	}

	private Optional<Program> currentOf(AppUser student) {
		return student.getProgramId() == null ? Optional.empty() : programs.findById(student.getProgramId());
	}

	private void requireAdmin(UUID actorId) {
		AdminAccess.requireAdmin(domainLookupService.requireUser(actorId));
	}

	/** A student account: a mentee that is not deleted; academicians, coordinators and admins have no semester. */
	private AppUser requireStudent(UUID userId) {
		AppUser user = users.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
		if (user.getStatus() == UserAccountStatus.DELETED || !user.has(UserRole.MENTEE)) {
			throw new InvalidRequestException(Texts.forRequest("error.notAStudent"));
		}
		return user;
	}

	private static StudentProgramResponse.ProgramRef ref(Program program) {
		return new StudentProgramResponse.ProgramRef(program.getId(), program.getName(), program.getStatus());
	}
}
