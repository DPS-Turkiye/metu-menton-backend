package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dps.teknokent.domain.OfficeHoursHistoryStep;
import com.dps.teknokent.domain.OfficeHoursMessageType;
import com.dps.teknokent.domain.OfficeHoursRequestStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.requests.ProgramOfficeHoursAppointmentsQuery;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentResponse;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentResponse.HistoryEntry;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentsResponse;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentsResponse.Row;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.OfficeHoursMeeting;
import com.dps.teknokent.entity.OfficeHoursRequest;
import com.dps.teknokent.entity.OfficeHoursRequestMessage;
import com.dps.teknokent.entity.OfficeHoursUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.OfficeHoursMeetingRepository;
import com.dps.teknokent.repository.OfficeHoursRequestMessageRepository;
import com.dps.teknokent.repository.OfficeHoursRequestRepository;
import com.dps.teknokent.repository.OfficeHoursUserRepository;

@ExtendWith(MockitoExtension.class)
class ProgramOfficeHoursServiceTest {

	/** Friday 13 Nov 2026, 12:00 in Turkey; the week runs from Monday 9 Nov. */
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

	private final OfficeHoursUser ayse = officeHoursUser("Ayşe Yılmaz");
	private final OfficeHoursUser ahmet = officeHoursUser("Ahmet Kaya");
	private final OfficeHoursUser ilker = officeHoursUser("İlker Su");
	private final OfficeHoursUser zeynep = officeHoursUser("Zeynep Aydın");

	@BeforeEach
	void setUp() {
		service = new ProgramOfficeHoursService(coordinatorProgramService, requests, meetings, messages,
				officeHoursUsers, institutionPeople, bookingService, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void listsEveryRequestWithItsTimePeopleAndTheCards() {
		managed(ProgramType.OFFICE_HOURS);
		OfficeHoursRequest accepted = request(ayse, ahmet, OfficeHoursRequestStatus.ACCEPTED, "Yaz okulu",
				"2026-11-11T15:00", null);
		OfficeHoursRequest proposed = request(ilker, ahmet, OfficeHoursRequestStatus.COUNTER_PROPOSED, "CV",
				"2026-11-12T10:00", "2026-11-13T10:30,2026-11-14T09:00");
		OfficeHoursRequest pending = request(ayse, zeynep, OfficeHoursRequestStatus.PENDING, "Bitirme projesi",
				"2026-11-16T11:00,2026-11-17T11:00", null);
		candidates(accepted, proposed, pending);
		OfficeHoursMeeting meeting = meeting(accepted, Instant.parse("2026-11-11T12:00:00Z"));
		when(meetings.findByRequestIdIn(anyCollection())).thenReturn(List.of(meeting));
		people(record(ayse, "Endüstri Mühendisliği", null, null),
				record(ahmet, "Bilgisayar Mühendisliği", "Prof. Dr.", "Mühendislik Fakültesi"));
		when(requests.countByStatusInAndCreatedAtGreaterThanEqual(anyCollection(),
				eq(NOW.minus(ProgramOfficeHoursService.UNBOUNDED_LOOK_BACK)))).thenReturn(86L, 12L);
		when(meetings.countByScheduledAtGreaterThanEqualAndScheduledAtLessThan(
				Instant.parse("2026-11-08T21:00:00Z"), Instant.parse("2026-11-15T21:00:00Z"))).thenReturn(214L);
		when(requests.countByStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(OfficeHoursRequestStatus.DECLINED,
				Instant.parse("2026-11-08T21:00:00Z"), Instant.parse("2026-11-15T21:00:00Z"))).thenReturn(9L);
		when(institutionPeople.findAcademicFaculties()).thenReturn(List.of("Mühendislik Fakültesi"));

		ProgramOfficeHoursAppointmentsResponse page = service.list(COORDINATOR, PROGRAM, query(null, null, null));

		assertThat(page.counts()).isEqualTo(new ProgramOfficeHoursAppointmentsResponse.Counts(86, 12, 214, 9));
		assertThat(page.faculties()).containsExactly("Mühendislik Fakültesi");
		assertThat(page.appointments().totalElements()).isEqualTo(3);
		assertThat(page.appointments().content()).extracting(Row::topic)
				.containsExactly("Bitirme projesi", "CV", "Yaz okulu");
		Row cv = page.appointments().content().get(1);
		assertThat(cv.startsAt()).hasToString("2026-11-13T10:30+03:00");
		assertThat(cv.academic())
				.isEqualTo(new ProgramOfficeHoursAppointmentResponse.Person("Ahmet Kaya", "Prof. Dr.",
						"Bilgisayar Mühendisliği"));
		Row booked = page.appointments().content().get(2);
		assertThat(booked.startsAt()).hasToString("2026-11-11T15:00+03:00");
		assertThat(booked.student())
				.isEqualTo(new ProgramOfficeHoursAppointmentResponse.Person("Ayşe Yılmaz", null,
						"Endüstri Mühendisliği"));
		Row unknown = page.appointments().content().get(0);
		assertThat(unknown.academic()).isEqualTo(
				new ProgramOfficeHoursAppointmentResponse.Person("Zeynep Aydın", null, null));
	}

	@Test
	void withoutADateRangeReadsTheLastHalfYear() {
		managed(ProgramType.OFFICE_HOURS);

		service.list(COORDINATOR, PROGRAM, query(null, null, null));

		verify(requests).findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
				NOW.minus(ProgramOfficeHoursService.UNBOUNDED_LOOK_BACK), Instant.parse("2026-11-14T09:00:00Z"));
	}

	@Test
	void aStatusFilterIsAskedOfTheDatabase() {
		managed(ProgramType.OFFICE_HOURS);

		service.list(COORDINATOR, PROGRAM, new ProgramOfficeHoursAppointmentsQuery(OfficeHoursRequestStatus.PENDING,
				null, null, null, null, 0, 25));

		verify(requests).findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
				eq(OfficeHoursRequestStatus.PENDING), any(), any());
		verify(requests, never()).findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(any(),
				any());
	}

	@Test
	void aWeekShowsWhatHappensInItHoweverLongAgoItWasAskedFor() {
		managed(ProgramType.OFFICE_HOURS);
		OfficeHoursRequest friday = request(ayse, ahmet, OfficeHoursRequestStatus.PENDING, "Cuma",
				"2026-11-13T10:30", null);
		OfficeHoursRequest monday = request(ilker, ahmet, OfficeHoursRequestStatus.COUNTER_PROPOSED, "Pazartesi",
				"2026-10-01T10:00", "2026-11-09T00:00");
		OfficeHoursRequest nextWeek = request(ayse, zeynep, OfficeHoursRequestStatus.PENDING, "Sonraki hafta",
				"2026-11-16T00:00", null);
		OfficeHoursRequest booked = request(ilker, zeynep, OfficeHoursRequestStatus.ACCEPTED, "Uzun zaman önce",
				"2026-11-11T09:00", null);
		booked.setCreatedAt(NOW.minus(ProgramOfficeHoursService.UNBOUNDED_LOOK_BACK).minusSeconds(60));
		OfficeHoursMeeting meeting = meeting(booked, Instant.parse("2026-11-11T06:00:00Z"));
		when(meetings.findByScheduledAtGreaterThanEqualAndScheduledAtLessThan(Instant.parse("2026-11-08T21:00:00Z"),
				Instant.parse("2026-11-15T21:00:00Z"))).thenReturn(List.of(meeting));
		when(requests.findAllById(List.of(booked.getId()))).thenReturn(List.of(booked));
		when(meetings.findByRequestIdIn(anyCollection())).thenReturn(List.of(meeting));
		when(requests.findUnbookedShowingBetween("2026-11-09T00:00", "2026-11-16T00:00"))
				.thenReturn(List.of(friday, monday, nextWeek));
		people();

		ProgramOfficeHoursAppointmentsResponse page = service.list(COORDINATOR, PROGRAM,
				query(null, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15)));

		assertThat(page.appointments().content()).extracting(Row::topic)
				.containsExactly("Pazartesi", "Uzun zaman önce", "Cuma");
		assertThat(page.appointments().totalElements()).isEqualTo(3);
		verify(requests, never()).findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(any(),
				any());
	}

	@Test
	void aWeekFilteredByAnOpenStatusSkipsTheBookedMeetings() {
		managed(ProgramType.OFFICE_HOURS);
		OfficeHoursRequest pending = request(ayse, ahmet, OfficeHoursRequestStatus.PENDING, "Bekleyen",
				"2026-11-13T10:30", null);
		OfficeHoursRequest declined = request(ayse, ahmet, OfficeHoursRequestStatus.DECLINED, "Reddedilen",
				"2026-11-13T11:30", null);
		when(requests.findUnbookedShowingBetween(any(), any())).thenReturn(List.of(pending, declined));
		people();

		ProgramOfficeHoursAppointmentsResponse page = service.list(COORDINATOR, PROGRAM,
				new ProgramOfficeHoursAppointmentsQuery(OfficeHoursRequestStatus.PENDING, null, null,
						LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15), 0, 25));

		assertThat(page.appointments().content()).extracting(Row::topic).containsExactly("Bekleyen");
		verify(meetings, never()).findByScheduledAtGreaterThanEqualAndScheduledAtLessThan(any(), any());
	}

	@Test
	void aWeekFilteredByAcceptedReadsOnlyTheBookedMeetings() {
		managed(ProgramType.OFFICE_HOURS);

		service.list(COORDINATOR, PROGRAM, new ProgramOfficeHoursAppointmentsQuery(
				OfficeHoursRequestStatus.ACCEPTED, null, null, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15),
				0, 25));

		verify(meetings).findByScheduledAtGreaterThanEqualAndScheduledAtLessThan(any(), any());
		verify(requests, never()).findUnbookedShowingBetween(any(), any());
	}

	@Test
	void looksUpManyPeopleInSlicesTheDatabaseAccepts() {
		managed(ProgramType.OFFICE_HOURS);
		List<OfficeHoursRequest> many = new ArrayList<>();
		for (int i = 0; i < 600; i++) {
			many.add(request(officeHoursUser("Öğrenci " + i), officeHoursUser("Hoca " + i),
					OfficeHoursRequestStatus.PENDING, "Konu " + i, "2026-11-13T10:30", null));
		}
		candidates(many.toArray(OfficeHoursRequest[]::new));

		service.list(COORDINATOR, PROGRAM, query(null, null, null));

		verify(officeHoursUsers, times(2)).findAllById(anyCollection());
	}

	@Test
	void searchFindsTheStudentOrTheAcademicianWithTurkishCase() {
		managed(ProgramType.OFFICE_HOURS);
		OfficeHoursRequest first = request(ilker, ahmet, OfficeHoursRequestStatus.PENDING, "Bir",
				"2026-11-13T10:30", null);
		OfficeHoursRequest second = request(ayse, zeynep, OfficeHoursRequestStatus.PENDING, "İki",
				"2026-11-13T11:30", null);
		candidates(first, second);
		people();

		assertThat(service.list(COORDINATOR, PROGRAM, query("  İLKER ", null, null)).appointments().content())
				.extracting(Row::topic).containsExactly("Bir");
		assertThat(service.list(COORDINATOR, PROGRAM, query("aydın", null, null)).appointments().content())
				.extracting(Row::topic).containsExactly("İki");
	}

	@Test
	void aFacultyKeepsTheRequestsToItsAcademicians() {
		managed(ProgramType.OFFICE_HOURS);
		OfficeHoursRequest engineering = request(ayse, ahmet, OfficeHoursRequestStatus.PENDING, "Mühendislik",
				"2026-11-13T10:30", null);
		OfficeHoursRequest science = request(ilker, zeynep, OfficeHoursRequestStatus.PENDING, "Fen",
				"2026-11-13T11:30", null);
		candidates(engineering, science);
		people(record(ahmet, "Bilgisayar Mühendisliği", "Prof. Dr.", "Mühendislik Fakültesi"),
				record(zeynep, "Fizik", "Doç. Dr.", "Fen-Edebiyat Fakültesi"));

		ProgramOfficeHoursAppointmentsResponse page = service.list(COORDINATOR, PROGRAM,
				new ProgramOfficeHoursAppointmentsQuery(null, "Fen-Edebiyat Fakültesi", null, null, null, 0, 25));

		assertThat(page.appointments().content()).extracting(Row::topic).containsExactly("Fen");
	}

	@Test
	void pagesTheRowsAndCapsThePageSize() {
		managed(ProgramType.OFFICE_HOURS);
		OfficeHoursRequest a = request(ayse, ahmet, OfficeHoursRequestStatus.PENDING, "A", "2026-11-13T12:00", null);
		OfficeHoursRequest b = request(ayse, ahmet, OfficeHoursRequestStatus.PENDING, "B", "2026-11-13T11:00", null);
		OfficeHoursRequest c = request(ayse, ahmet, OfficeHoursRequestStatus.PENDING, "C", "2026-11-13T10:00", null);
		candidates(a, b, c);
		people();

		ProgramOfficeHoursAppointmentsResponse second = service.list(COORDINATOR, PROGRAM,
				new ProgramOfficeHoursAppointmentsQuery(null, null, null, null, null, 1, 2));
		ProgramOfficeHoursAppointmentsResponse huge = service.list(COORDINATOR, PROGRAM,
				new ProgramOfficeHoursAppointmentsQuery(null, null, null, null, null, -3, 5000));

		assertThat(second.appointments().content()).extracting(Row::topic).containsExactly("C");
		assertThat(second.appointments().totalPages()).isEqualTo(2);
		assertThat(huge.appointments().page()).isZero();
		assertThat(huge.appointments().size()).isEqualTo(ProgramOfficeHoursService.MAX_PAGE_SIZE);
	}

	@Test
	void refusesADateRangeWithOneEndBackwardsOrTooLong() {
		managed(ProgramType.OFFICE_HOURS);

		assertThatThrownBy(() -> service.list(COORDINATOR, PROGRAM, query(null, LocalDate.of(2026, 11, 9), null)))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.list(COORDINATOR, PROGRAM,
				query(null, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 8))))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.list(COORDINATOR, PROGRAM,
				query(null, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 2))))
				.isInstanceOf(InvalidRequestException.class);
		verifyNoInteractions(requests);
	}

	@Test
	void onlyOfficeHoursProgramsHaveAppointments() {
		managed(ProgramType.PEER_MENTORING);

		assertThatThrownBy(() -> service.list(COORDINATOR, PROGRAM, query(null, null, null)))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.detail(COORDINATOR, PROGRAM, UUID.randomUUID()))
				.isInstanceOf(InvalidRequestException.class);
		verifyNoInteractions(requests, meetings, messages);
	}

	@Test
	void someoneWhoDoesNotRunTheProgramIsRefusedBeforeAnythingIsRead() {
		UUID stranger = UUID.randomUUID();
		when(coordinatorProgramService.requireCoordinatorOrAdmin(stranger))
				.thenReturn(account(stranger, UserAccountStatus.APPROVED));
		when(coordinatorProgramService.requireManagedProgram(stranger, PROGRAM))
				.thenThrow(new InvalidRequestException("Only the program coordinator can change this program."));

		assertThatThrownBy(() -> service.list(stranger, PROGRAM, query(null, null, null)))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.detail(stranger, PROGRAM, UUID.randomUUID()))
				.isInstanceOf(InvalidRequestException.class);
		verifyNoInteractions(requests, meetings, messages, officeHoursUsers, institutionPeople);
	}

	@Test
	void aCoordinatorWhoseAccountIsNotApprovedIsRefused() {
		for (UserAccountStatus status : List.of(UserAccountStatus.PENDING, UserAccountStatus.REJECTED)) {
			UUID waiting = UUID.randomUUID();
			when(coordinatorProgramService.requireCoordinatorOrAdmin(waiting)).thenReturn(account(waiting, status));

			assertThatThrownBy(() -> service.list(waiting, PROGRAM, query(null, null, null)))
					.isInstanceOf(InvalidRequestException.class);
			assertThatThrownBy(() -> service.detail(waiting, PROGRAM, UUID.randomUUID()))
					.isInstanceOf(InvalidRequestException.class);
		}
		verify(coordinatorProgramService, never()).requireManagedProgram(any(), any());
		verifyNoInteractions(requests, meetings, messages, officeHoursUsers, institutionPeople);
	}

	@Test
	void theDetailShowsAProposedTimeAsLongAsTheMeetingItWouldBookAndHowItGotThere() {
		managed(ProgramType.OFFICE_HOURS);
		OfficeHoursRequest proposed = request(ayse, ahmet, OfficeHoursRequestStatus.COUNTER_PROPOSED, "CV",
				"2026-11-12T10:00", "2026-11-13T10:30");
		proposed.setCreatedAt(Instant.parse("2026-11-10T13:20:00Z"));
		proposed.setUpdatedAt(Instant.parse("2026-11-11T06:05:00Z"));
		when(requests.findById(proposed.getId())).thenReturn(Optional.of(proposed));
		when(bookingService.meetingLengths(ahmet.getId())).thenReturn(start -> 30);
		OfficeHoursRequestMessage question = message(proposed, OfficeHoursMessageType.INSTRUCTOR_QUESTION,
				"2026-11-10T15:00:00Z");
		OfficeHoursRequestMessage reply = message(proposed, OfficeHoursMessageType.STUDENT_REPLY,
				"2026-11-10T16:00:00Z");
		when(messages.findByRequestIdInOrderByCreatedAtAsc(List.of(proposed.getId())))
				.thenReturn(List.of(question, reply));
		people(record(ayse, "Endüstri Mühendisliği", null, null));

		ProgramOfficeHoursAppointmentResponse detail = service.detail(COORDINATOR, PROGRAM, proposed.getId());

		assertThat(detail.topic()).isEqualTo("CV");
		assertThat(detail.startsAt()).hasToString("2026-11-13T10:30+03:00");
		assertThat(detail.endsAt()).hasToString("2026-11-13T11:00+03:00");
		assertThat(detail.student().department()).isEqualTo("Endüstri Mühendisliği");
		verify(meetings, never()).findByRequestId(any());
		assertThat(detail.history()).containsExactly(
				new HistoryEntry(OfficeHoursHistoryStep.SUBMITTED, Instant.parse("2026-11-10T13:20:00Z")),
				new HistoryEntry(OfficeHoursHistoryStep.INFO_REQUESTED, Instant.parse("2026-11-10T15:00:00Z")),
				new HistoryEntry(OfficeHoursHistoryStep.STUDENT_REPLIED, Instant.parse("2026-11-10T16:00:00Z")),
				new HistoryEntry(OfficeHoursHistoryStep.ALTERNATIVE_PROPOSED, Instant.parse("2026-11-11T06:05:00Z")));
	}

	@Test
	void theDetailOfABookedMeetingUsesItsOwnTimeAndLength() {
		managed(ProgramType.OFFICE_HOURS);
		OfficeHoursRequest accepted = request(ayse, ahmet, OfficeHoursRequestStatus.ACCEPTED, "Yaz okulu",
				"2026-11-11T15:00", "2026-11-12T15:00");
		OfficeHoursMeeting meeting = meeting(accepted, Instant.parse("2026-11-12T12:00:00Z"));
		meeting.setDurationMinutes(45);
		meeting.setCreatedAt(Instant.parse("2026-11-10T08:00:00Z"));
		when(requests.findById(accepted.getId())).thenReturn(Optional.of(accepted));
		when(meetings.findByRequestId(accepted.getId())).thenReturn(Optional.of(meeting));
		people();

		ProgramOfficeHoursAppointmentResponse detail = service.detail(COORDINATOR, PROGRAM, accepted.getId());

		assertThat(detail.startsAt()).hasToString("2026-11-12T15:00+03:00");
		assertThat(detail.endsAt()).hasToString("2026-11-12T15:45+03:00");
		assertThat(detail.history()).extracting(HistoryEntry::step).containsExactly(OfficeHoursHistoryStep.SUBMITTED,
				OfficeHoursHistoryStep.ALTERNATIVE_PROPOSED, OfficeHoursHistoryStep.ACCEPTED);
		assertThat(detail.history().get(1).at()).isNull();
		assertThat(detail.history().get(2).at()).isEqualTo(Instant.parse("2026-11-10T08:00:00Z"));
		verifyNoInteractions(bookingService);
	}

	@Test
	void aDeclinedOrWithdrawnRequestEndsTheHistoryAtItsLastChange() {
		OfficeHoursRequest declined = request(ayse, ahmet, OfficeHoursRequestStatus.DECLINED, "X",
				"2026-11-13T10:30", null);
		declined.setUpdatedAt(Instant.parse("2026-11-11T09:00:00Z"));
		OfficeHoursRequest withdrawn = request(ayse, ahmet, OfficeHoursRequestStatus.WITHDRAWN, "Y",
				"2026-11-13T10:30", null);

		assertThat(ProgramOfficeHoursService.history(declined, null, List.of())).last()
				.isEqualTo(new HistoryEntry(OfficeHoursHistoryStep.DECLINED, Instant.parse("2026-11-11T09:00:00Z")));
		assertThat(ProgramOfficeHoursService.history(withdrawn, null, List.of())).extracting(HistoryEntry::step)
				.containsExactly(OfficeHoursHistoryStep.SUBMITTED, OfficeHoursHistoryStep.WITHDRAWN);
	}

	@Test
	void aRequestWithoutAReadableTimeHasNone() {
		OfficeHoursRequest broken = request(ayse, ahmet, OfficeHoursRequestStatus.COUNTER_PROPOSED, "?", "",
				"not-a-time");

		assertThat(ProgramOfficeHoursService.startOf(broken, null)).isNull();
	}

	@Test
	void anUnknownRequestIsNotFound() {
		managed(ProgramType.OFFICE_HOURS);
		UUID missing = UUID.randomUUID();
		when(requests.findById(missing)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.detail(COORDINATOR, PROGRAM, missing))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	private void managed(ProgramType type) {
		when(coordinatorProgramService.requireCoordinatorOrAdmin(COORDINATOR))
				.thenReturn(account(COORDINATOR, UserAccountStatus.APPROVED));
		Program program = new Program();
		program.setId(PROGRAM);
		program.setProgramType(type);
		when(coordinatorProgramService.requireManagedProgram(COORDINATOR, PROGRAM)).thenReturn(program);
	}

	private void candidates(OfficeHoursRequest... found) {
		when(requests.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(any(), any()))
				.thenReturn(List.of(found));
	}

	private void people(InstitutionPerson... records) {
		lenient().when(officeHoursUsers.findAllById(anyCollection()))
				.thenReturn(List.of(ayse, ahmet, ilker, zeynep));
		lenient().when(institutionPeople.findByUserIdIn(anyCollection())).thenReturn(List.of(records));
	}

	private static ProgramOfficeHoursAppointmentsQuery query(@Nullable String search, @Nullable LocalDate from,
			@Nullable LocalDate to) {
		return new ProgramOfficeHoursAppointmentsQuery(null, null, search, from, to, 0, 25);
	}

	private static AppUser account(UUID id, UserAccountStatus status) {
		AppUser account = new AppUser();
		account.setId(id);
		account.setRole(UserRole.COORDINATOR);
		account.setStatus(status);
		return account;
	}

	private static OfficeHoursUser officeHoursUser(String name) {
		OfficeHoursUser user = new OfficeHoursUser();
		user.setId(UUID.randomUUID());
		user.setAppUserId(UUID.randomUUID());
		user.setFullName(name);
		return user;
	}

	private static InstitutionPerson record(OfficeHoursUser user, String department, @Nullable String title,
			@Nullable String faculty) {
		AppUser account = new AppUser();
		account.setId(user.getAppUserId());
		InstitutionPerson person = new InstitutionPerson("odtu", UUID.randomUUID().toString());
		person.setUser(account);
		person.setFullName(user.getFullName());
		person.setDepartmentName(department);
		person.setAcademicTitle(title);
		person.setFaculty(faculty);
		person.setSyncedAt(NOW);
		return person;
	}

	private static OfficeHoursRequest request(OfficeHoursUser student, OfficeHoursUser instructor,
			OfficeHoursRequestStatus status, String topic, String preferred, @Nullable String proposed) {
		OfficeHoursRequest request = new OfficeHoursRequest();
		request.setId(UUID.randomUUID());
		request.setStudentId(student.getId());
		request.setInstructorId(instructor.getId());
		request.setStatus(status);
		request.setTopic(topic);
		request.setExpectedOutcome("Gizli açıklama");
		request.setPreferredSlots(preferred);
		request.setProposedSlots(proposed);
		request.setCreatedAt(NOW.minusSeconds(86_400));
		request.setUpdatedAt(NOW.minusSeconds(3_600));
		return request;
	}

	private static OfficeHoursMeeting meeting(OfficeHoursRequest request, Instant at) {
		OfficeHoursMeeting meeting = new OfficeHoursMeeting();
		meeting.setId(UUID.randomUUID());
		meeting.setRequestId(request.getId());
		meeting.setInstructorId(request.getInstructorId());
		meeting.setStudentId(request.getStudentId());
		meeting.setScheduledAt(at);
		meeting.setDurationMinutes(30);
		meeting.setCreatedAt(NOW);
		return meeting;
	}

	private static OfficeHoursRequestMessage message(OfficeHoursRequest request, OfficeHoursMessageType type,
			String at) {
		OfficeHoursRequestMessage message = new OfficeHoursRequestMessage();
		message.setId(UUID.randomUUID());
		message.setRequestId(request.getId());
		message.setType(type);
		message.setBody("Gizli mesaj");
		message.setCreatedAt(Instant.parse(at));
		return message;
	}
}
