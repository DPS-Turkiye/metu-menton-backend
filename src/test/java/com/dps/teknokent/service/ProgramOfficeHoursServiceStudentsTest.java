package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.requests.ProgramOfficeHoursStudentsQuery;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentResponse.Person;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursStudentsResponse;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursStudentsResponse.StudentRequests;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursStudentsResponse.StudentRow;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.OfficeHoursUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.OfficeHoursMeetingRepository;
import com.dps.teknokent.repository.OfficeHoursRequestMessageRepository;
import com.dps.teknokent.repository.OfficeHoursRequestRepository;
import com.dps.teknokent.repository.OfficeHoursStudentLoad;
import com.dps.teknokent.repository.OfficeHoursUserRepository;

@ExtendWith(MockitoExtension.class)
class ProgramOfficeHoursServiceStudentsTest {

	private static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");
	private static final UUID COORDINATOR = UUID.randomUUID();
	private static final UUID PROGRAM = UUID.randomUUID();

	@Mock
	private CoordinatorProgramService coordinatorProgramService;
	@Mock
	private OfficeHoursRequestRepository requests;
	@Mock
	private OfficeHoursMeetingRepository meetings;
	@Mock
	private OfficeHoursRequestMessageRepository messages;
	@Mock
	private OfficeHoursUserRepository officeHoursUsers;
	@Mock
	private InstitutionPersonRepository institutionPeople;
	@Mock
	private OfficeHoursBookingService bookingService;

	private ProgramOfficeHoursService service;

	private final OfficeHoursUser ayse = person("Ayşe Yılmaz", true);
	private final OfficeHoursUser ilker = person("İlker Su", true);
	private final OfficeHoursUser berk = person("Berk Özkan", false);

	@BeforeEach
	void setUp() {
		service = new ProgramOfficeHoursService(coordinatorProgramService, requests, meetings, messages,
				officeHoursUsers, institutionPeople, bookingService, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void listsEveryoneWhoAskedInTheWindowWithTheirRequestsNewestFirst() {
		managed(ProgramType.OFFICE_HOURS);
		when(requests.loadByStudentSince(NOW.minus(ProgramOfficeHoursService.RECENT_WINDOW)))
				.thenReturn(List.of(load(ayse, 2, 1, 3, 0, 0, 2, "2026-11-10T08:00:00Z"),
						load(berk, 0, 0, 1, 1, 0, 1, "2026-11-12T08:00:00Z")));
		people(record(ayse, "Endüstri Mühendisliği", 4, "Mühendislik Fakültesi"));

		ProgramOfficeHoursStudentsResponse page = service.students(COORDINATOR, PROGRAM, query(null, null, false));

		assertThat(page.faculties()).containsExactly("Mühendislik Fakültesi");
		assertThat(page.students().content()).containsExactly(
				new StudentRow(berk.getId(), new Person("Berk Özkan", null, null), null,
						new StudentRequests(0, 0, 1, 1, 0), 1, Instant.parse("2026-11-12T08:00:00Z")),
				new StudentRow(ayse.getId(), new Person("Ayşe Yılmaz", null, "Endüstri Mühendisliği"), 4,
						new StudentRequests(2, 1, 3, 0, 0), 2, Instant.parse("2026-11-10T08:00:00Z")));
	}

	@Test
	void filtersByNameFacultyAndAnOpenRequest() {
		managed(ProgramType.OFFICE_HOURS);
		when(requests.loadByStudentSince(NOW.minus(ProgramOfficeHoursService.RECENT_WINDOW)))
				.thenReturn(List.of(load(ayse, 0, 0, 1, 0, 0, 1, "2026-11-10T08:00:00Z"),
						load(ilker, 1, 0, 0, 0, 0, 1, "2026-11-11T08:00:00Z")));
		people(record(ayse, "Endüstri Mühendisliği", 4, "Mühendislik Fakültesi"),
				record(ilker, "Fizik", 2, "Fen-Edebiyat Fakültesi"));

		assertThat(names(service.students(COORDINATOR, PROGRAM, query(" İLKER ", null, false))))
				.containsExactly("İlker Su");
		assertThat(names(service.students(COORDINATOR, PROGRAM, query(null, "Mühendislik Fakültesi", false))))
				.containsExactly("Ayşe Yılmaz");
		assertThat(names(service.students(COORDINATOR, PROGRAM, query(null, null, true))))
				.containsExactly("İlker Su");
	}

	@Test
	void pagesTheStudents() {
		managed(ProgramType.OFFICE_HOURS);
		when(requests.loadByStudentSince(NOW.minus(ProgramOfficeHoursService.RECENT_WINDOW)))
				.thenReturn(List.of(load(ayse, 1, 0, 0, 0, 0, 1, "2026-11-12T08:00:00Z"),
						load(ilker, 1, 0, 0, 0, 0, 1, "2026-11-11T08:00:00Z"),
						load(berk, 1, 0, 0, 0, 0, 1, "2026-11-10T08:00:00Z")));
		people();

		ProgramOfficeHoursStudentsResponse second = service.students(COORDINATOR, PROGRAM,
				new ProgramOfficeHoursStudentsQuery(null, null, false, 1, 2));

		assertThat(names(second)).containsExactly("Berk Özkan");
		assertThat(second.students().totalElements()).isEqualTo(3);
	}

	@Test
	void onlyAnApprovedCoordinatorOfAnOfficeHoursProgramMayLook() {
		managed(ProgramType.PEER_MENTORING);
		assertThatThrownBy(() -> service.students(COORDINATOR, PROGRAM, query(null, null, false)))
				.isInstanceOf(InvalidRequestException.class);

		UUID waiting = UUID.randomUUID();
		when(coordinatorProgramService.requireCoordinatorOrAdmin(waiting))
				.thenReturn(account(waiting, UserAccountStatus.REJECTED));
		assertThatThrownBy(() -> service.students(waiting, PROGRAM, query(null, null, false)))
				.isInstanceOf(InvalidRequestException.class);
		verifyNoInteractions(requests, officeHoursUsers, institutionPeople);
	}

	private void managed(ProgramType type) {
		when(coordinatorProgramService.requireCoordinatorOrAdmin(COORDINATOR))
				.thenReturn(account(COORDINATOR, UserAccountStatus.APPROVED));
		Program program = new Program();
		program.setId(PROGRAM);
		program.setProgramType(type);
		when(coordinatorProgramService.requireManagedProgram(COORDINATOR, PROGRAM)).thenReturn(program);
	}

	private void people(InstitutionPerson... records) {
		when(officeHoursUsers.findAllById(anyCollection())).thenReturn(List.of(ayse, ilker, berk));
		org.mockito.Mockito.lenient().when(institutionPeople.findByUserIdIn(anyCollection()))
				.thenReturn(List.of(records));
	}

	private static List<String> names(ProgramOfficeHoursStudentsResponse page) {
		return page.students().content().stream().map(row -> row.student().name()).toList();
	}

	private static ProgramOfficeHoursStudentsQuery query(@Nullable String search, @Nullable String faculty,
			boolean awaitingOnly) {
		return new ProgramOfficeHoursStudentsQuery(search, faculty, awaitingOnly, 0, 25);
	}

	private static AppUser account(UUID id, UserAccountStatus status) {
		AppUser account = new AppUser();
		account.setId(id);
		account.setRole(UserRole.COORDINATOR);
		account.setStatus(status);
		return account;
	}

	private static OfficeHoursUser person(String name, boolean hasAccount) {
		OfficeHoursUser user = new OfficeHoursUser();
		user.setId(UUID.randomUUID());
		user.setAppUserId(hasAccount ? UUID.randomUUID() : null);
		user.setFullName(name);
		return user;
	}

	private static InstitutionPerson record(OfficeHoursUser user, String department, int classYear, String faculty) {
		AppUser account = new AppUser();
		account.setId(user.getAppUserId());
		InstitutionPerson person = new InstitutionPerson("odtu", UUID.randomUUID().toString());
		person.setUser(account);
		person.setDepartmentName(department);
		person.setClassYear(classYear);
		person.setFaculty(faculty);
		person.setSyncedAt(NOW);
		return person;
	}

	private static OfficeHoursStudentLoad load(OfficeHoursUser student, long awaiting, long alternative,
			long accepted, long declined, long withdrawn, long academicians, String lastAt) {
		return new OfficeHoursStudentLoad() {
			@Override
			public UUID getStudentId() {
				return student.getId();
			}

			@Override
			public Long getAwaitingDecision() {
				return awaiting;
			}

			@Override
			public Long getAlternativeProposed() {
				return alternative;
			}

			@Override
			public Long getAccepted() {
				return accepted;
			}

			@Override
			public Long getDeclined() {
				return declined;
			}

			@Override
			public Long getWithdrawn() {
				return withdrawn;
			}

			@Override
			public Long getAcademicians() {
				return academicians;
			}

			@Override
			public Instant getLastRequestedAt() {
				return Instant.parse(lastAt);
			}
		};
	}
}
