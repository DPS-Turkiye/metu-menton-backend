package com.dps.teknokent.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IdentityKind;
import com.dps.teknokent.domain.InstitutionDataSource;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.requests.RegisterStudentRequest;
import com.dps.teknokent.dto.responses.UserResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.security.FirebaseIdentity;
import com.dps.teknokent.util.EmailDomains;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ODTÜ's e-mail sign-up for students, until ODTÜ's own login exists. The address that proves someone is a student
 * is the one in the Firebase token, verified and under metu.edu.tr; nothing in the request body can stand in for
 * it. The account is an approved mentee of the one running peer-mentoring program, and what the student typed about
 * their department and year sits in institution_person as a SIGNUP row, which ODTÜ's login later takes over.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentSignupService {

	/** ODTÜ's domain; department addresses such as ceng.metu.edu.tr count too. */
	static final String STUDENT_EMAIL_DOMAIN = "metu.edu.tr";
	/** The institution the SIGNUP row is filed under: ODTÜ's, the same as its institution login's. */
	static final String INSTITUTION = "odtu";
	static final String PERSISTENT_ID_PREFIX = "signup:";

	public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
	public static final String STUDENT_EMAIL_DOMAIN_REQUIRED = "STUDENT_EMAIL_DOMAIN_REQUIRED";
	public static final String NO_STUDENT_PROGRAM = "NO_STUDENT_PROGRAM";
	public static final String SEVERAL_STUDENT_PROGRAMS = "SEVERAL_STUDENT_PROGRAMS";

	private final AppUserRepository users;
	private final ProgramRepository programs;
	private final InstitutionPersonRepository people;
	private final EmailDomainPolicy emailDomainPolicy;

	@Transactional
	public UserResponse register(FirebaseIdentity identity, RegisterStudentRequest request) {
		String email = requireStudentAddress(identity);
		if (users.findByFirebaseUid(identity.uid()).isPresent()) {
			throw new InvalidRequestException(Texts.forRequest("error.accountAlreadyRegistered"),
					RegistrationService.ACCOUNT_ALREADY_REGISTERED);
		}
		if (users.existsByEmailIgnoreCase(email)) {
			throw new InvalidRequestException(Texts.forRequest("error.emailAlreadyRegistered"),
					RegistrationService.EMAIL_ALREADY_REGISTERED);
		}
		Program program = requireTheStudentProgram();

		AppUser student = new AppUser();
		student.setEmail(email);
		student.setFullName(request.firstName().trim() + " " + request.lastName().trim());
		// Never ADMIN or COORDINATOR: this path makes mentees and nothing else.
		student.setRole(UserRole.MENTEE);
		student.setStatus(UserAccountStatus.APPROVED);
		student.setProgramId(program.getId());
		student.setFirebaseUid(identity.uid());
		student = users.saveAndFlush(student);

		people.save(signupRecord(student, request));
		return UserResponse.fromEntity(student, program.isIndividualMentees());
	}

	/**
	 * Only the token's own address counts, and only once Firebase has seen it verified: an unverified address is one
	 * anybody can type.
	 */
	private String requireStudentAddress(FirebaseIdentity identity) {
		if (!identity.emailVerified()) {
			throw new InvalidRequestException(Texts.forRequest("error.studentSignupUnverified"), EMAIL_NOT_VERIFIED);
		}
		String email = EmailDomains.normalizeEmail(identity.email());
		if (!EmailDomains.isEmail(email) || !EmailDomains.matches(email, STUDENT_EMAIL_DOMAIN)) {
			throw new InvalidRequestException(Texts.forRequest("error.studentSignupDomain", "@" + STUDENT_EMAIL_DOMAIN),
					STUDENT_EMAIL_DOMAIN_REQUIRED);
		}
		emailDomainPolicy.requireAllowed(email);
		return email;
	}

	/**
	 * Students join the one running peer-mentoring program; with none, or more than one, there is no right guess. The
	 * student only hears that sign-up is closed; the log says why, for whoever runs the deployment.
	 */
	private Program requireTheStudentProgram() {
		List<Program> running = programs.findByProgramType(ProgramType.PEER_MENTORING).stream()
				.filter(program -> program.getStatus() == ProgramStatus.ACTIVE)
				.toList();
		if (running.size() == 1) {
			return running.getFirst();
		}
		log.warn("Student sign-up refused: {} active peer-mentoring programs, exactly one is needed", running.size());
		throw new ConflictException(Texts.forRequest("error.studentSignupClosed"),
				running.isEmpty() ? NO_STUDENT_PROGRAM : SEVERAL_STUDENT_PROGRAMS);
	}

	private static InstitutionPerson signupRecord(AppUser student, RegisterStudentRequest request) {
		InstitutionPerson person = new InstitutionPerson(INSTITUTION, PERSISTENT_ID_PREFIX + student.getId());
		person.setUser(student);
		person.setKinds(Set.of(IdentityKind.STUDENT));
		person.setFullName(student.getFullName());
		person.setEmail(student.getEmail());
		person.setDepartmentName(request.department().trim());
		person.setClassYear(request.year().classYear());
		person.setProgramLevel(request.year().studyLevel());
		person.setSource(InstitutionDataSource.SIGNUP);
		person.setSyncedAt(Instant.now());
		return person;
	}
}
