package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dps.teknokent.config.RegistrationProperties;
import com.dps.teknokent.domain.IdentityKind;
import com.dps.teknokent.domain.InstitutionDataSource;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.StudentYear;
import com.dps.teknokent.domain.StudyLevel;
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
import com.dps.teknokent.util.Texts;

class StudentSignupServiceTest {

	private static final RegisterStudentRequest FORM = new RegisterStudentRequest(" Deniz ", " Kaya ",
			" Bilgisayar Mühendisliği ", StudentYear.YEAR_3);

	private final AppUserRepository users = mock(AppUserRepository.class);
	private final ProgramRepository programs = mock(ProgramRepository.class);
	private final InstitutionPersonRepository people = mock(InstitutionPersonRepository.class);
	private final List<Program> peerPrograms = new ArrayList<>();
	private StudentSignupService service;
	private Program iskur;

	@BeforeEach
	void setUp() {
		iskur = program(ProgramStatus.ACTIVE);
		iskur.setIndividualMentees(true);
		peerPrograms.add(iskur);
		when(programs.findByProgramType(ProgramType.PEER_MENTORING)).thenReturn(peerPrograms);
		when(users.findByFirebaseUid(any())).thenReturn(Optional.empty());
		when(users.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> {
			AppUser user = invocation.getArgument(0);
			user.setId(UUID.randomUUID());
			return user;
		});
		service = studentSignup(List.of());
	}

	private StudentSignupService studentSignup(List<String> allowedDomains) {
		return new StudentSignupService(users, new StudentProgramLookup(programs), people,
				new EmailDomainPolicy(new RegistrationProperties(allowedDomains)));
	}

	@Test
	void aVerifiedOdtuStudentIsAnApprovedMenteeOfTheProgramAtOnce() {
		UserResponse response = service.register(student("uid-1", " Deniz.Kaya@METU.edu.tr "), FORM);

		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(users).saveAndFlush(saved.capture());
		AppUser user = saved.getValue();
		assertThat(user.getEmail()).isEqualTo("deniz.kaya@metu.edu.tr");
		assertThat(user.getFullName()).isEqualTo("Deniz Kaya");
		assertThat(user.getStatus()).isEqualTo(UserAccountStatus.APPROVED);
		assertThat(user.getRole()).isEqualTo(UserRole.MENTEE);
		assertThat(user.activePermissions()).containsExactly(UserRole.MENTEE);
		assertThat(user.getProgramId()).isEqualTo(iskur.getId());
		assertThat(user.getFirebaseUid()).isEqualTo("uid-1");
		assertThat(user.getOrganization()).isNull();
		assertThat(response.status()).isEqualTo(UserAccountStatus.APPROVED);
		assertThat(response.individualMentee()).isTrue();
	}

	@Test
	void theDepartmentAndYearGoWhereTheRosterReadsThemMarkedAsTheStudentsOwnWord() {
		service.register(student("uid-1", "deniz.kaya@metu.edu.tr"), FORM);

		ArgumentCaptor<InstitutionPerson> record = ArgumentCaptor.forClass(InstitutionPerson.class);
		verify(people).save(record.capture());
		InstitutionPerson person = record.getValue();
		assertThat(person.getUser().getEmail()).isEqualTo("deniz.kaya@metu.edu.tr");
		assertThat(person.getInstitution()).isEqualTo("odtu");
		assertThat(person.getPersistentId()).isEqualTo("signup:" + person.getUser().getId());
		assertThat(person.getSource()).isEqualTo(InstitutionDataSource.SIGNUP);
		assertThat(person.kinds()).containsExactly(IdentityKind.STUDENT);
		assertThat(person.getFullName()).isEqualTo("Deniz Kaya");
		assertThat(person.getDepartmentName()).isEqualTo("Bilgisayar Mühendisliği");
		assertThat(person.getClassYear()).isEqualTo(3);
		assertThat(person.getProgramLevel()).isEqualTo(StudyLevel.BACHELOR);
		assertThat(person.getSyncedAt()).isNotNull();
	}

	@Test
	void preparatoryAndGraduateYearsHaveNoClassYear() {
		service.register(student("uid-1", "a@metu.edu.tr"),
				new RegisterStudentRequest("A", "B", "Fizik", StudentYear.PREP));
		service.register(student("uid-2", "b@metu.edu.tr"),
				new RegisterStudentRequest("A", "B", "Fizik", StudentYear.MASTER));
		service.register(student("uid-3", "c@metu.edu.tr"),
				new RegisterStudentRequest("A", "B", "Fizik", StudentYear.YEAR_5_PLUS));

		ArgumentCaptor<InstitutionPerson> records = ArgumentCaptor.forClass(InstitutionPerson.class);
		verify(people, times(3)).save(records.capture());
		assertThat(records.getAllValues())
				.extracting(InstitutionPerson::getClassYear, InstitutionPerson::getProgramLevel)
				.containsExactly(
						tuple(null, StudyLevel.BACHELOR),
						tuple(null, StudyLevel.MASTER),
						tuple(5, StudyLevel.BACHELOR));
	}

	@Test
	void aDepartmentAddressCountsAsOdtus() {
		service.register(student("uid-1", "deniz@ceng.metu.edu.tr"), FORM);

		verify(users).saveAndFlush(any(AppUser.class));
	}

	// Firebase puts any typed address in the token before it is verified.
	@Test
	void anUnverifiedAddressCreatesNothing() {
		assertThatThrownBy(() -> service.register(new FirebaseIdentity("uid-1", "deniz@metu.edu.tr", false), FORM))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						error -> assertThat(error.code()).isEqualTo(StudentSignupService.EMAIL_NOT_VERIFIED));
		assertNothingCreated();
	}

	@Test
	void anAddressOutsideOdtuCreatesNothing() {
		for (String email : List.of("deniz@example.test", "deniz@evilmetu.edu.tr", "deniz@metu.edu.tr.example.test",
				"deniz@metu.edu", "metu.edu.tr", "")) {
			assertThatThrownBy(() -> service.register(student("uid-1", email), FORM))
					.isInstanceOfSatisfying(InvalidRequestException.class, error -> assertThat(error.code())
							.isEqualTo(StudentSignupService.STUDENT_EMAIL_DOMAIN_REQUIRED));
		}
		assertNothingCreated();
	}

	@Test
	void theDeploymentsOwnDomainListStillApplies() {
		service = studentSignup(List.of("ceng.metu.edu.tr"));

		assertThatThrownBy(() -> service.register(student("uid-1", "deniz@ee.metu.edu.tr"), FORM))
				.isInstanceOf(InvalidRequestException.class);
		assertNothingCreated();
	}

	@Test
	void aLoginThatAlreadyHasAnAccountSignsInInstead() {
		when(users.findByFirebaseUid("uid-1")).thenReturn(Optional.of(new AppUser()));

		assertThatThrownBy(() -> service.register(student("uid-1", "deniz@metu.edu.tr"), FORM))
				.isInstanceOfSatisfying(InvalidRequestException.class, error -> assertThat(error.code())
						.isEqualTo(RegistrationService.ACCOUNT_ALREADY_REGISTERED));
		assertNothingCreated();
	}

	// The bearer filter already lets a verified address claim its (non-staff) account, so the student just signs in.
	@Test
	void anAddressThatAlreadyHasAnAccountSignsInInstead() {
		when(users.existsByEmailIgnoreCase("deniz@metu.edu.tr")).thenReturn(true);

		assertThatThrownBy(() -> service.register(student("uid-1", "Deniz@metu.edu.tr"), FORM))
				.isInstanceOfSatisfying(InvalidRequestException.class, error -> assertThat(error.code())
						.isEqualTo(RegistrationService.EMAIL_ALREADY_REGISTERED));
		assertNothingCreated();
	}

	@Test
	void withoutARunningPeerProgramThereIsNothingToJoin() {
		peerPrograms.clear();
		peerPrograms.add(program(ProgramStatus.DRAFT));
		peerPrograms.add(program(ProgramStatus.COMPLETED));

		assertThatThrownBy(() -> service.register(student("uid-1", "deniz@metu.edu.tr"), FORM))
				.isInstanceOfSatisfying(ConflictException.class,
						error -> {
							assertThat(error.code()).isEqualTo(StudentSignupService.NO_STUDENT_PROGRAM);
							assertThat(error.getMessage()).isEqualTo(Texts.forRequest("error.studentSignupClosed"));
						});
		assertNothingCreated();
	}

	@Test
	void withTwoRunningPeerProgramsTheSignupDoesNotGuess() {
		peerPrograms.add(program(ProgramStatus.ACTIVE));

		assertThatThrownBy(() -> service.register(student("uid-1", "deniz@metu.edu.tr"), FORM))
				.isInstanceOfSatisfying(ConflictException.class,
						error -> {
							assertThat(error.code()).isEqualTo(StudentSignupService.SEVERAL_STUDENT_PROGRAMS);
							assertThat(error.getMessage()).isEqualTo(Texts.forRequest("error.studentSignupClosed"));
						});
		assertNothingCreated();
	}

	@Test
	void aDraftOrFinishedPeerProgramBesideTheRunningOneIsIgnored() {
		peerPrograms.add(program(ProgramStatus.DRAFT));
		peerPrograms.add(program(ProgramStatus.COMPLETED));

		service.register(student("uid-1", "deniz@metu.edu.tr"), FORM);

		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(users).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getProgramId()).isEqualTo(iskur.getId());
	}

	private void assertNothingCreated() {
		verify(users, never()).saveAndFlush(any());
		verify(people, never()).save(any());
	}

	private static FirebaseIdentity student(String uid, String email) {
		return new FirebaseIdentity(uid, email, true);
	}

	private static Program program(ProgramStatus status) {
		Program program = new Program();
		program.setId(UUID.randomUUID());
		program.setName("Akran Mentorluğu");
		program.setProgramType(ProgramType.PEER_MENTORING);
		program.setStatus(status);
		return program;
	}
}
