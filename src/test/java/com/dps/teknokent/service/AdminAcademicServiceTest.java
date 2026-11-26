package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.InstitutionDataSource;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.responses.AdminAcademicResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.AppUserIdentity;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.repository.AppUserIdentityRepository;
import com.dps.teknokent.repository.InstitutionAdvisingRepository;
import com.dps.teknokent.repository.InstitutionAdvisingRepository.AdvisorLoad;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.OfficeHoursMeetingRepository;
import com.dps.teknokent.repository.OfficeHoursUserRepository;

@ExtendWith(MockitoExtension.class)
class AdminAcademicServiceTest {

	private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID ALI = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

	@Mock
	private DomainLookupService domainLookupService;
	@Mock
	private InstitutionPersonRepository people;
	@Mock
	private InstitutionAdvisingRepository advising;
	@Mock
	private AppUserIdentityRepository identities;
	@Mock
	private CoordinatorProgramService coordinatorProgramService;
	@Mock
	private OfficeHoursUserRepository officeHoursPeople;
	@Mock
	private OfficeHoursMeetingRepository meetings;

	/** 15 November 2026, so this month in Istanbul runs from 31 October 21:00 UTC to 30 November 21:00 UTC. */
	private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");

	private AdminAcademicService service;

	@BeforeEach
	void setUp() {
		service = new AdminAcademicService(domainLookupService, people, advising, identities,
				coordinatorProgramService, officeHoursPeople, meetings, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private record Hours(UUID accountId, UUID personId, boolean hoursDefined)
			implements OfficeHoursUserRepository.PersonHours {

		@Override
		public UUID getAccountId() {
			return accountId;
		}

		@Override
		public UUID getPersonId() {
			return personId;
		}

		@Override
		public boolean isHoursDefined() {
			return hoursDefined;
		}
	}

	private record Meetings(UUID instructorId, long meetings)
			implements OfficeHoursMeetingRepository.InstructorMeetings {

		@Override
		public UUID getInstructorId() {
			return instructorId;
		}

		@Override
		public long getMeetings() {
			return meetings;
		}
	}

	@Test
	void listsAcademiciansWithTheirAdviseesAndLastSignIn() {
		when(domainLookupService.requireUser(ADMIN)).thenReturn(user(ADMIN, UserRole.ADMIN));
		AppUser ali = user(ALI, UserRole.MENTOR);
		InstitutionPerson signedIn = academic("A-1", "Ali Demir", ali);
		InstitutionPerson notYet = academic("A-2", "Zeynep Kara", null);
		notYet.setActive(false);
		when(people.findByAcademicTrueOrderByFullNameAsc()).thenReturn(List.of(signedIn, notYet));
		when(advising.countAdviseesByAdvisor()).thenReturn(List.of(load("A-1", 3), load("A-9", 1)));
		Instant older = Instant.parse("2026-11-01T09:00:00Z");
		Instant newer = Instant.parse("2026-11-07T09:00:00Z");
		when(identities.findByUserIdIn(List.of(ALI)))
				.thenReturn(List.of(identity(ali, older), identity(ali, newer)));

		List<AdminAcademicResponse> rows = service.list(ADMIN);

		assertThat(rows).extracting(AdminAcademicResponse::fullName).containsExactly("Ali Demir", "Zeynep Kara");
		AdminAcademicResponse first = rows.get(0);
		assertThat(first.userId()).isEqualTo(ALI);
		assertThat(first.signedIn()).isTrue();
		assertThat(first.lastLoginAt()).isEqualTo(newer);
		assertThat(first.adviseeCount()).isEqualTo(3);
		assertThat(first.departmentName()).isEqualTo("Bilgisayar Mühendisliği");
		AdminAcademicResponse second = rows.get(1);
		assertThat(second.userId()).isNull();
		assertThat(second.signedIn()).isFalse();
		assertThat(second.lastLoginAt()).isNull();
		assertThat(second.adviseeCount()).isZero();
		assertThat(second.active()).isFalse();
	}

	@Test
	void anAcademicListWithNobodySignedInSkipsTheSignInLookup() {
		when(domainLookupService.requireUser(ADMIN)).thenReturn(user(ADMIN, UserRole.ADMIN));
		when(people.findByAcademicTrueOrderByFullNameAsc()).thenReturn(List.of(academic("A-2", "Zeynep Kara", null)));
		when(advising.countAdviseesByAdvisor()).thenReturn(List.of());

		assertThat(service.list(ADMIN)).hasSize(1);
		verify(identities, never()).findByUserIdIn(any());
	}

	@Test
	void givesTheAdminEachAcademiciansOfficeHoursAndThisMonthsMeetings() {
		when(domainLookupService.requireUser(ADMIN)).thenReturn(user(ADMIN, UserRole.ADMIN));
		UUID ayse = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
		UUID aliPerson = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
		UUID aysePerson = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
		UUID can = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
		when(people.findByAcademicTrueOrderByFullNameAsc()).thenReturn(List.of(
				academic("A-1", "Ali Demir", user(ALI, UserRole.MENTOR)),
				academic("A-2", "Ayşe Kaya", user(ayse, UserRole.MENTOR)),
				academic("A-3", "Can Er", user(can, UserRole.MENTOR)),
				academic("A-4", "Zeynep Kara", null)));
		when(advising.countAdviseesByAdvisor()).thenReturn(List.of());
		when(officeHoursPeople.findHoursByAccountIdIn(List.of(ALI, ayse, can), NOW))
				.thenReturn(List.of(new Hours(ALI, aliPerson, true), new Hours(ayse, aysePerson, false)));
		when(meetings.countLiveByInstructorBetween(List.of(aliPerson, aysePerson),
				Instant.parse("2026-10-31T21:00:00Z"), Instant.parse("2026-11-30T21:00:00Z")))
				.thenReturn(List.of(new Meetings(aliPerson, 14)));

		List<AdminAcademicResponse> rows = service.list(ADMIN);

		assertThat(rows).extracting(AdminAcademicResponse::officeHours).containsExactly(
				new AdminAcademicResponse.OfficeHours(true, 14),
				new AdminAcademicResponse.OfficeHours(false, 0),
				// Signed in but never opened Office Hours, and never signed in at all: none set, no meetings.
				new AdminAcademicResponse.OfficeHours(false, 0),
				new AdminAcademicResponse.OfficeHours(false, 0));
	}

	@Test
	void onlyAnAdminListsAcademicians() {
		UUID coordinator = UUID.randomUUID();
		when(domainLookupService.requireUser(coordinator)).thenReturn(user(coordinator, UserRole.COORDINATOR));

		assertThatThrownBy(() -> service.list(coordinator)).isInstanceOf(ResponseStatusException.class);
		verify(people, never()).findByAcademicTrueOrderByFullNameAsc();
	}

	@Test
	void anAdminWhoseAccountIsNotApprovedIsRefusedToo() {
		UUID admin = UUID.randomUUID();
		AppUser pending = user(admin, UserRole.ADMIN);
		pending.setStatus(UserAccountStatus.PENDING);
		when(domainLookupService.requireUser(admin)).thenReturn(pending);

		assertThatThrownBy(() -> service.list(admin)).isInstanceOfSatisfying(ResponseStatusException.class,
				refused -> assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
		verify(people, never()).findByAcademicTrueOrderByFullNameAsc();
	}

	@Test
	void anOfficeHoursProgramListsEveryActiveAcademicianWithoutContactDetails() {
		UUID coordinator = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		Program officeHours = new Program();
		officeHours.setId(programId);
		officeHours.setProgramType(ProgramType.OFFICE_HOURS);
		when(coordinatorProgramService.requireManagedProgram(coordinator, programId)).thenReturn(officeHours);
		InstitutionPerson signedIn = academic("A-1", "Ali Demir", user(ALI, UserRole.MENTOR));
		InstitutionPerson left = academic("A-2", "Zeynep Kara", null);
		left.setActive(false);
		when(people.findByAcademicTrueOrderByFullNameAsc()).thenReturn(List.of(signedIn, left));
		when(advising.countAdviseesByAdvisor()).thenReturn(List.of(load("A-1", 2)));

		List<AdminAcademicResponse> rows = service.forProgram(coordinator, programId);

		verify(officeHoursPeople, never()).findHoursByAccountIdIn(any(), any());
		assertThat(rows).singleElement().satisfies(row -> {
			assertThat(row.officeHours()).isNull();
			assertThat(row.fullName()).isEqualTo("Ali Demir");
			assertThat(row.email()).isNull();
			assertThat(row.lastLoginAt()).isNull();
			assertThat(row.signedIn()).isTrue();
			assertThat(row.adviseeCount()).isEqualTo(2);
		});
		verify(identities, never()).findByUserIdIn(any());
	}

	@Test
	void onlyOfficeHoursProgramsListAcademicians() {
		UUID coordinator = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		Program peer = new Program();
		peer.setId(programId);
		peer.setProgramType(ProgramType.PEER_MENTORING);
		when(coordinatorProgramService.requireManagedProgram(coordinator, programId)).thenReturn(peer);

		assertThatThrownBy(() -> service.forProgram(coordinator, programId))
				.isInstanceOf(InvalidRequestException.class);
		verify(people, never()).findByAcademicTrueOrderByFullNameAsc();
	}

	@Test
	void someoneWhoDoesNotRunTheProgramIsRefusedByTheProgramGuard() {
		UUID stranger = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		when(coordinatorProgramService.requireManagedProgram(stranger, programId))
				.thenThrow(new InvalidRequestException("Only the program coordinator can change this program."));

		assertThatThrownBy(() -> service.forProgram(stranger, programId))
				.isInstanceOf(InvalidRequestException.class);
		verify(people, never()).findByAcademicTrueOrderByFullNameAsc();
	}

	private static InstitutionPerson academic(String persistentId, String name, AppUser user) {
		InstitutionPerson person = new InstitutionPerson("odtu", persistentId);
		person.setId(UUID.randomUUID());
		person.setFullName(name);
		person.setAcademic(true);
		person.setAcademicTitle("Doç. Dr.");
		person.setFaculty("Mühendislik Fakültesi");
		person.setDepartmentCode("CENG");
		person.setDepartmentName("Bilgisayar Mühendisliği");
		person.setSource(InstitutionDataSource.IMPORT);
		person.setSyncedAt(Instant.now());
		person.setUser(user);
		return person;
	}

	private static AdvisorLoad load(String advisor, long advisees) {
		return new AdvisorLoad() {
			@Override
			public String getInstitution() {
				return "odtu";
			}

			@Override
			public String getAdvisorPersistentId() {
				return advisor;
			}

			@Override
			public long getAdvisees() {
				return advisees;
			}
		};
	}

	private static AppUserIdentity identity(AppUser user, Instant lastLogin) {
		AppUserIdentity identity = new AppUserIdentity(user, "ODTU", "A-1", lastLogin);
		identity.setLastLoginAt(lastLogin);
		return identity;
	}

	private static AppUser user(UUID id, UserRole role) {
		AppUser user = new AppUser();
		user.setId(id);
		user.setRole(role);
		return user;
	}
}
