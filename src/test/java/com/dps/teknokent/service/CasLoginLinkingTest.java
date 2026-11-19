package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;

import com.dps.teknokent.domain.InstitutionDataSource;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.responses.InstitutionLoginStartResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.AppUserIdentity;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.repository.AppUserIdentityRepository;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.security.LoginProvisioner;
import com.dps.teknokent.security.institution.CasLoginTestSupport;
import com.dps.teknokent.security.institution.InstitutionLoginProvider;

import tools.jackson.databind.json.JsonMapper;

/**
 * The whole CAS sign-in, from the button to the one-time code, against a fake CAS server: the real provider and the
 * real login service, with only the database and Redis stood in for.
 */
class CasLoginLinkingTest {

	private static final String CAS = "odtu-cas";

	private final CasLoginTestSupport cas = new CasLoginTestSupport();
	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
	@SuppressWarnings("unchecked") // mock() takes the raw class of a generic type.
	private final ValueOperations<String, String> values = mock(ValueOperations.class);
	private final AppUserRepository users = mock(AppUserRepository.class);
	private final AppUserIdentityRepository identities = mock(AppUserIdentityRepository.class);
	private final InstitutionPersonRepository people = mock(InstitutionPersonRepository.class);
	private final ProgramRepository programs = mock(ProgramRepository.class);
	private InstitutionLoginService service;

	@BeforeEach
	@SuppressWarnings("unchecked") // mock() takes the raw class of a generic type.
	void setUp() {
		ObjectProvider<InstitutionLoginProvider> providers = mock(ObjectProvider.class);
		when(providers.orderedStream()).thenAnswer(invocation -> Stream.of(cas.provider()));
		when(redis.opsForValue()).thenReturn(values);
		when(users.save(any(AppUser.class))).thenAnswer(invocation -> {
			AppUser user = invocation.getArgument(0);
			if (user.getId() == null) {
				user.setId(UUID.randomUUID());
			}
			return user;
		});
		when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
		when(identities.findByProviderAndPersistentId(anyString(), anyString())).thenReturn(Optional.empty());
		when(people.findByInstitutionAndPersistentId(anyString(), anyString())).thenReturn(Optional.empty());
		when(people.findFirstByUserIdAndSource(any(), any())).thenReturn(Optional.empty());
		when(programs.findByProgramType(ProgramType.PEER_MENTORING)).thenReturn(List.of());
		service = new InstitutionLoginService(providers, redis, users, identities, people,
				mock(LoginProvisioner.class), new StudentProgramLookup(programs),
				mock(PlatformTransactionManager.class), JsonMapper.builder().build());
	}

	// Weekend launch, then CAS: the student signed up with their @metu.edu.tr address and must keep that one account.
	@Test
	void aStudentWhoSignedUpByEmailLandsOnTheSameAccountAndKeepsWhatTheyTyped() {
		AppUser signedUp = signedUpStudent("e123456@metu.edu.tr");
		UUID program = signedUp.getProgramId();
		InstitutionPerson typed = new InstitutionPerson("odtu", "signup:" + signedUp.getId());
		typed.setUser(signedUp);
		typed.setFullName("Deniz Kaya");
		typed.setDepartmentName("Bilgisayar Mühendisliği");
		typed.setClassYear(2);
		typed.setSource(InstitutionDataSource.SIGNUP);
		when(people.findFirstByUserIdAndSource(signedUp.getId(), InstitutionDataSource.SIGNUP))
				.thenReturn(Optional.of(typed));

		String code = signInThroughCas("E123456", Map.of(
				"mail", List.of("E123456@metu.edu.tr"), "eduPersonAffiliation", List.of("student")));

		// The code is for the account that already existed; no second one was made.
		verify(values).set(eq(InstitutionLoginService.CODE_KEY + code), eq(signedUp.getId().toString()), any());
		verify(users, never()).save(argThatIsNot(signedUp));
		assertThat(signedUp.getFirebaseUid()).isEqualTo("firebase-email-login");
		assertThat(signedUp.getProgramId()).isEqualTo(program);
		assertThat(signedUp.getStatus()).isEqualTo(UserAccountStatus.APPROVED);
		ArgumentCaptor<AppUserIdentity> link = ArgumentCaptor.forClass(AppUserIdentity.class);
		verify(identities).save(link.capture());
		assertThat(link.getValue().getUser()).isSameAs(signedUp);
		assertThat(link.getValue().getProvider()).isEqualTo(CAS);
		assertThat(link.getValue().getPersistentId()).isEqualTo("e123456");
		// One row for the person: the sign-up row becomes ODTÜ's, and keeps what CAS did not send.
		ArgumentCaptor<InstitutionPerson> record = ArgumentCaptor.forClass(InstitutionPerson.class);
		verify(people).save(record.capture());
		assertThat(record.getValue()).isSameAs(typed);
		assertThat(record.getValue().getInstitution()).isEqualTo(CAS);
		assertThat(record.getValue().getPersistentId()).isEqualTo("e123456");
		assertThat(record.getValue().getSource()).isEqualTo(InstitutionDataSource.LOGIN);
		assertThat(record.getValue().getFullName()).isEqualTo("Deniz Kaya");
		assertThat(record.getValue().getDepartmentName()).isEqualTo("Bilgisayar Mühendisliği");
		assertThat(record.getValue().getClassYear()).isEqualTo(2);
	}

	@Test
	void theSecondSignInFindsTheLinkAndMakesNoNewOne() {
		AppUser signedUp = signedUpStudent("e123456@metu.edu.tr");
		when(identities.findByProviderAndPersistentId(CAS, "e123456"))
				.thenReturn(Optional.of(new AppUserIdentity(signedUp, CAS, "e123456", Instant.now())));

		String code = signInThroughCas("e123456", Map.of(
				"mail", List.of("e123456@metu.edu.tr"), "eduPersonAffiliation", List.of("student")));

		verify(values).set(eq(InstitutionLoginService.CODE_KEY + code), eq(signedUp.getId().toString()), any());
		verify(identities, never()).save(any());
	}

	// A student whose first contact is CAS joins the same program an e-mail sign-up does.
	@Test
	void aNewStudentGetsAnAccountInTheOneRunningPeerProgram() {
		Program peer = runningPeerProgram();
		when(programs.findByProgramType(ProgramType.PEER_MENTORING)).thenReturn(List.of(peer));

		signInThroughCas("e200001", Map.of("mail", List.of("e200001@metu.edu.tr"), "cn", List.of("Ece Ak"),
				"eduPersonAffiliation", List.of("student")));

		AppUser created = savedUser();
		assertThat(created.getEmail()).isEqualTo("e200001@metu.edu.tr");
		assertThat(created.getRole()).isEqualTo(UserRole.MENTEE);
		assertThat(created.getStatus()).isEqualTo(UserAccountStatus.APPROVED);
		assertThat(created.getProgramId()).isEqualTo(peer.getId());
		assertThat(created.getFirebaseUid()).isEqualTo("odtu-cas_e200001");
	}

	// Never a reason to turn a student away: with no program to guess, they get in without one.
	@Test
	void withNoneOrSeveralPeerProgramsTheNewStudentStillGetsInWithoutOne() {
		signInThroughCas("e200001", Map.of("mail", List.of("e200001@metu.edu.tr"),
				"eduPersonAffiliation", List.of("student")));
		assertThat(savedUser().getProgramId()).isNull();

		List<Program> several = List.of(runningPeerProgram(), runningPeerProgram());
		when(programs.findByProgramType(ProgramType.PEER_MENTORING)).thenReturn(several);
		signInThroughCas("e200002", Map.of("mail", List.of("e200002@metu.edu.tr"),
				"eduPersonAffiliation", List.of("student")));
		assertThat(savedUser().getProgramId()).isNull();
	}

	@Test
	void anAcademicBecomesAnAcademicianAndJoinsNoProgram() {
		List<Program> one = List.of(runningPeerProgram());
		when(programs.findByProgramType(ProgramType.PEER_MENTORING)).thenReturn(one);

		signInThroughCas("akademik1", Map.of("mail", List.of("akademik1@metu.edu.tr"),
				"eduPersonAffiliation", List.of("faculty")));

		AppUser created = savedUser();
		assertThat(created.getRole()).isEqualTo(UserRole.ACADEMICIAN);
		assertThat(created.getProgramId()).isNull();
	}

	// Administrative staff are only identified: no account, no grant, and the code leads to the ordinary sign-up.
	@Test
	void someoneWhoIsNeitherStudentNorAcademicGetsASignupCodeAndNoAccount() {
		String code = signInThroughCas("idari1", Map.of("mail", List.of("idari1@metu.edu.tr"),
				"eduPersonAffiliation", List.of("staff")));

		verify(values).set(eq(InstitutionLoginService.SIGNUP_CODE_KEY + code), anyString(), any());
		verify(values, never()).set(startsWith(InstitutionLoginService.CODE_KEY), anyString(), any());
		verify(users, never()).save(any());
		verify(people, never()).save(any());
	}

	@Test
	void aCoordinatorsAccountIsNeverBoundToACasLogin() {
		AppUser coordinator = signedUpStudent("koordinator@metu.edu.tr");
		coordinator.setRole(UserRole.COORDINATOR);

		assertThatThrownBy(() -> signInThroughCas("koordinator", Map.of("mail", List.of("koordinator@metu.edu.tr"),
				"eduPersonAffiliation", List.of("student")))).isInstanceOf(ConflictException.class);
		verify(identities, never()).save(any());
	}

	/** Start, sign in at the fake CAS, come back with its ticket; answers with the one-time code. */
	private String signInThroughCas(String user, Map<String, List<String>> attributes) {
		InstitutionLoginStartResponse started = service.start(CAS);
		when(values.getAndDelete(InstitutionLoginService.STATE_KEY + started.state())).thenReturn(CAS);
		String ticket = cas.server().signIn(started.redirectUrl(), user, attributes);
		return service.complete(CAS, started.state(), Map.of("ticket", ticket));
	}

	private AppUser signedUpStudent(String email) {
		AppUser student = new AppUser();
		student.setId(UUID.randomUUID());
		student.setEmail(email);
		student.setFullName("Deniz Kaya");
		student.setRole(UserRole.MENTEE);
		student.setStatus(UserAccountStatus.APPROVED);
		student.setProgramId(UUID.randomUUID());
		student.setFirebaseUid("firebase-email-login");
		when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(student));
		return student;
	}

	private static Program runningPeerProgram() {
		Program program = mock(Program.class);
		when(program.getId()).thenReturn(UUID.randomUUID());
		when(program.getStatus()).thenReturn(ProgramStatus.ACTIVE);
		return program;
	}

	/** The account the last sign-in saved. */
	private AppUser savedUser() {
		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(users, atLeastOnce()).save(saved.capture());
		return saved.getValue();
	}

	private static AppUser argThatIsNot(AppUser known) {
		return argThat(user -> user != known);
	}
}
