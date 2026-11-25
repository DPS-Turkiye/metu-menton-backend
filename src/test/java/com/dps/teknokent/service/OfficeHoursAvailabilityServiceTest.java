package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.OfficeHoursRequestStatus;
import com.dps.teknokent.domain.OfficeHoursRole;
import com.dps.teknokent.dto.requests.CreateMentorAvailabilityRuleRequest;
import com.dps.teknokent.dto.requests.MentorAvailabilityExceptionRequest;
import com.dps.teknokent.dto.requests.OfficeHoursAffectedItemRequest;
import com.dps.teknokent.dto.requests.OfficeHoursOneOffRequest;
import com.dps.teknokent.dto.requests.UpdateOfficeHoursAvailabilityRequest;
import com.dps.teknokent.dto.responses.OfficeHoursAvailabilityResponse;
import com.dps.teknokent.entity.OfficeHoursException;
import com.dps.teknokent.entity.OfficeHoursMeeting;
import com.dps.teknokent.entity.OfficeHoursOneOff;
import com.dps.teknokent.entity.OfficeHoursRequest;
import com.dps.teknokent.entity.OfficeHoursRule;
import com.dps.teknokent.entity.OfficeHoursSettings;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.repository.OfficeHoursEnrollmentRepository;
import com.dps.teknokent.repository.OfficeHoursExceptionRepository;
import com.dps.teknokent.repository.OfficeHoursOneOffRepository;
import com.dps.teknokent.repository.OfficeHoursRuleRepository;
import com.dps.teknokent.repository.OfficeHoursSettingsRepository;
import com.dps.teknokent.repository.OfficeHoursUserRepository;
import com.dps.teknokent.service.OfficeHoursAvailabilityImpactService.Impact;
import com.dps.teknokent.service.OfficeHoursSlotService.Hours;
import com.dps.teknokent.service.OfficeHoursSlotService.Setup;

@ExtendWith(MockitoExtension.class)
class OfficeHoursAvailabilityServiceTest {

	private static final UUID INSTRUCTOR = UUID.fromString("00000000-0000-0000-0000-00000000d001");
	private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

	@Mock
	private OfficeHoursEnrollmentRepository enrollmentRepository;
	@Mock
	private OfficeHoursSettingsRepository settingsRepository;
	@Mock
	private OfficeHoursRuleRepository ruleRepository;
	@Mock
	private OfficeHoursExceptionRepository exceptionRepository;
	@Mock
	private OfficeHoursOneOffRepository oneOffRepository;
	@Mock
	private OfficeHoursUserRepository userRepository;
	@Mock
	private OfficeHoursAvailabilityImpactService impactService;

	private OfficeHoursAvailabilityService service;

	@BeforeEach
	void setUp() {
		service = new OfficeHoursAvailabilityService(enrollmentRepository, settingsRepository, ruleRepository,
				exceptionRepository, oneOffRepository, userRepository, impactService,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private void instructorSomewhere(boolean instructor) {
		when(enrollmentRepository.existsByUserIdAndRole(INSTRUCTOR, OfficeHoursRole.INSTRUCTOR)).thenReturn(instructor);
	}

	private static CreateMentorAvailabilityRuleRequest rule(DayOfWeek day, int from, int to, Integer slot) {
		return new CreateMentorAvailabilityRuleRequest(day, LocalTime.of(from, 0), LocalTime.of(to, 0), slot);
	}

	private static UpdateOfficeHoursAvailabilityRequest request(List<CreateMentorAvailabilityRuleRequest> rules,
			List<MentorAvailabilityExceptionRequest> exceptions) {
		return new UpdateOfficeHoursAvailabilityRequest(rules, exceptions, 5, 30, 10, null, null);
	}

	private static OfficeHoursOneOffRequest oneOff(String startsAt, String endsAt) {
		return new OfficeHoursOneOffRequest(OffsetDateTime.parse(startsAt), OffsetDateTime.parse(endsAt), " B-204 ");
	}

	@Test
	void showsAnAcademicTheMentorDefaultsBeforeAnythingIsSaved() {
		instructorSomewhere(true);
		when(settingsRepository.findById(any())).thenReturn(Optional.empty());

		OfficeHoursAvailabilityResponse response = service.find(INSTRUCTOR);

		assertThat(response.rules()).isEmpty();
		assertThat(response.exceptions()).isEmpty();
		assertThat(response.studentCapacity()).isNull();
		assertThat(response.defaultMeetingDurationMinutes()).isEqualTo(45);
		assertThat(response.meetingBufferMinutes()).isEqualTo(15);
	}

	@Test
	void replacesTheWholeSetupAndDeletesTheOldOneFirst() {
		instructorSomewhere(true);
		OfficeHoursRule old = new OfficeHoursRule();
		old.setDay(DayOfWeek.MONDAY);
		old.setStartTime(LocalTime.of(9, 0));
		old.setEndTime(LocalTime.of(10, 0));
		when(ruleRepository.findByInstructorIdOrderByDayOfWeekAscStartTimeAsc(INSTRUCTOR))
				.thenReturn(List.of(old));
		when(exceptionRepository.findByInstructorIdOrderByStartsOnAsc(INSTRUCTOR))
				.thenReturn(List.of());
		when(settingsRepository.findById(INSTRUCTOR)).thenReturn(Optional.empty());

		service.update(INSTRUCTOR, request(
				List.of(rule(DayOfWeek.TUESDAY, 14, 16, 30), rule(DayOfWeek.THURSDAY, 10, 12, null)),
				List.of(new MentorAvailabilityExceptionRequest(LocalDate.of(2026, 11, 20),
						LocalDate.of(2026, 11, 21), "  Konferans  "))));

		InOrder order = inOrder(ruleRepository);
		order.verify(ruleRepository).deleteAll(List.of(old));
		order.verify(ruleRepository).flush();
		@SuppressWarnings("unchecked") // ArgumentCaptor.forClass takes the raw collection class.
		ArgumentCaptor<List<OfficeHoursRule>> rules = ArgumentCaptor.forClass(List.class);
		order.verify(ruleRepository).saveAll(rules.capture());
		assertThat(rules.getValue()).extracting(OfficeHoursRule::day, OfficeHoursRule::getSlotMinutes)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(DayOfWeek.TUESDAY, 30),
						org.assertj.core.groups.Tuple.tuple(DayOfWeek.THURSDAY, 60));
		assertThat(rules.getValue()).allSatisfy(saved -> {
			assertThat(saved.getInstructorId()).isEqualTo(INSTRUCTOR);
			assertThat(saved.getCreatedAt()).isEqualTo(NOW);
		});

		@SuppressWarnings("unchecked") // ArgumentCaptor.forClass takes the raw collection class.
		ArgumentCaptor<List<OfficeHoursException>> exceptions = ArgumentCaptor.forClass(List.class);
		verify(exceptionRepository).saveAll(exceptions.capture());
		assertThat(exceptions.getValue()).singleElement()
				.satisfies(saved -> assertThat(saved.getReason()).isEqualTo("Konferans"));

		ArgumentCaptor<OfficeHoursSettings> settings = ArgumentCaptor.forClass(OfficeHoursSettings.class);
		verify(settingsRepository).save(settings.capture());
		assertThat(settings.getValue().getInstructorId()).isEqualTo(INSTRUCTOR);
		assertThat(settings.getValue().getStudentCapacity()).isEqualTo(5);
		assertThat(settings.getValue().getDefaultMeetingDurationMinutes()).isEqualTo(30);
		assertThat(settings.getValue().getMeetingBufferMinutes()).isEqualTo(10);
	}

	@Test
	void keepsAnyoneWhoIsNotAnAcademicAwayFromTheSetup() {
		instructorSomewhere(false);

		assertThatThrownBy(() -> service.find(INSTRUCTOR))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
		assertThatThrownBy(() -> service.update(INSTRUCTOR, request(List.of(), List.of())))
				.isInstanceOf(ResponseStatusException.class);
		verify(settingsRepository, never()).save(any());
	}

	@Test
	void refusesRangesThatEndBeforeTheyStartOrOverlap() {
		instructorSomewhere(true);

		assertThatThrownBy(() -> service.update(INSTRUCTOR,
				request(List.of(rule(DayOfWeek.MONDAY, 12, 10, null)), List.of())))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.update(INSTRUCTOR, request(
				List.of(rule(DayOfWeek.MONDAY, 10, 12, null), rule(DayOfWeek.MONDAY, 11, 13, null)), List.of())))
				.isInstanceOf(InvalidRequestException.class);
		verify(ruleRepository, never()).saveAll(any());
	}

	@Test
	void refusesDatesAwayThatEndBeforeTheyStartOrRepeat() {
		instructorSomewhere(true);
		MentorAvailabilityExceptionRequest backwards = new MentorAvailabilityExceptionRequest(
				LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 20), null);
		MentorAvailabilityExceptionRequest day = new MentorAvailabilityExceptionRequest(
				LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20), null);

		assertThatThrownBy(() -> service.update(INSTRUCTOR, request(List.of(), List.of(backwards))))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.update(INSTRUCTOR, request(List.of(), List.of(day, day))))
				.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void savesOneOffHoursAndLeavesThemAloneWhenAClientSendsNone() {
		instructorSomewhere(true);
		when(settingsRepository.findById(any())).thenReturn(Optional.empty());
		UpdateOfficeHoursAvailabilityRequest withOneOff = new UpdateOfficeHoursAvailabilityRequest(List.of(),
				List.of(), 5, 30, 10, List.of(oneOff("2026-11-12T14:00:00+03:00", "2026-11-12T15:00:00+03:00")), null);

		service.update(INSTRUCTOR, withOneOff);

		ArgumentCaptor<List<OfficeHoursOneOff>> saved = ArgumentCaptor.captor();
		verify(oneOffRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).singleElement().satisfies(entity -> {
			assertThat(entity.getStartsAt()).isEqualTo(Instant.parse("2026-11-12T11:00:00Z"));
			assertThat(entity.getPlace()).isEqualTo("B-204");
		});

		service.update(INSTRUCTOR, request(List.of(), List.of()));
		verify(oneOffRepository, times(1)).saveAll(any());
		verify(oneOffRepository, times(1)).deleteAll(any());
	}

	@Test
	void refusesOneOffHoursThatAreTooShortOverlapFallOnADayOffOrRunPastMidnight() {
		instructorSomewhere(true);
		MentorAvailabilityExceptionRequest thursdayOff = new MentorAvailabilityExceptionRequest(
				LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 12), null);

		assertThatThrownBy(() -> service.update(INSTRUCTOR, new UpdateOfficeHoursAvailabilityRequest(List.of(),
				List.of(), 5, 30, 10, List.of(oneOff("2026-11-12T14:00:00+03:00", "2026-11-12T14:10:00+03:00")), null)))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.update(INSTRUCTOR, new UpdateOfficeHoursAvailabilityRequest(List.of(),
				List.of(), 5, 30, 10, List.of(oneOff("2026-11-12T14:00:00+03:00", "2026-11-12T15:00:00+03:00"),
						oneOff("2026-11-12T14:30:00+03:00", "2026-11-12T16:00:00+03:00")),
				null)))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.update(INSTRUCTOR, new UpdateOfficeHoursAvailabilityRequest(List.of(),
				List.of(thursdayOff), 5, 30, 10,
				List.of(oneOff("2026-11-12T14:00:00+03:00", "2026-11-12T15:00:00+03:00")), null)))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.update(INSTRUCTOR, new UpdateOfficeHoursAvailabilityRequest(List.of(),
				List.of(), 5, 30, 10, List.of(oneOff("2026-11-12T23:00:00+03:00", "2026-11-13T01:00:00+03:00")), null)))
				.isInstanceOf(InvalidRequestException.class);
		verify(oneOffRepository, never()).saveAll(any());
	}

	private static final UUID BOOKED = UUID.fromString("00000000-0000-0000-0000-00000000d101");
	private static final UUID WAITING = UUID.fromString("00000000-0000-0000-0000-00000000d102");

	private static UpdateOfficeHoursAvailabilityRequest confirming(OfficeHoursAffectedItemRequest... shown) {
		return new UpdateOfficeHoursAvailabilityRequest(List.of(), List.of(), 5, 30, 10, null, List.of(shown));
	}

	private static Impact affecting(UUID meetingRequestId, OfficeHoursRequest... requests) {
		List<OfficeHoursMeeting> meetings = List.of();
		if (meetingRequestId != null) {
			OfficeHoursMeeting meeting = new OfficeHoursMeeting();
			meeting.setRequestId(meetingRequestId);
			meetings = List.of(meeting);
		}
		return new Impact(meetings, Arrays.stream(requests)
				.map(request -> new OfficeHoursAvailabilityImpactService.AffectedRequest(request,
						List.of(NOW.plusSeconds(86_400)), List.of()))
				.toList());
	}

	private static OfficeHoursRequest waiting(OfficeHoursRequestStatus status) {
		OfficeHoursRequest request = new OfficeHoursRequest();
		request.setId(WAITING);
		request.setStatus(status);
		return request;
	}

	@Test
	void locksTheAcademicBeforeReplacingAnything() {
		instructorSomewhere(true);

		service.update(INSTRUCTOR, request(List.of(), List.of()));

		InOrder order = inOrder(userRepository, ruleRepository);
		order.verify(userRepository).findForUpdate(INSTRUCTOR);
		order.verify(ruleRepository).deleteAll(any());
	}

	@Test
	void checksNothingWhenTheClientSendsNoConfirmedList() {
		instructorSomewhere(true);

		service.update(INSTRUCTOR, request(List.of(), List.of()));

		verify(impactService, never()).affectedBy(any(), any());
		verify(settingsRepository).save(any());
	}

	@Test
	void refusesASaveWhenARequestCameInAfterThePreviewShowedNothing() {
		instructorSomewhere(true);
		when(impactService.affectedBy(eq(INSTRUCTOR), any()))
				.thenReturn(affecting(null, waiting(OfficeHoursRequestStatus.PENDING)));

		assertThatThrownBy(() -> service.update(INSTRUCTOR, confirming()))
				.isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.code())
						.isEqualTo(OfficeHoursAvailabilityService.OFFICE_HOURS_AFFECTED_BOOKINGS_CHANGED));
		verify(ruleRepository, never()).deleteAll(any());
		verify(settingsRepository, never()).save(any());
		InOrder order = inOrder(userRepository, impactService);
		order.verify(userRepository).findForUpdate(INSTRUCTOR);
		order.verify(impactService).affectedBy(eq(INSTRUCTOR), any());
	}

	@Test
	void refusesASaveWhenAnOfferedTimeWasTakenAfterThePreview() {
		instructorSomewhere(true);
		// The preview showed the request waiting on the student; they took the time, so it is a meeting now.
		when(impactService.affectedBy(eq(INSTRUCTOR), any())).thenReturn(affecting(WAITING));

		assertThatThrownBy(() -> service.update(INSTRUCTOR,
				confirming(new OfficeHoursAffectedItemRequest(WAITING, OfficeHoursRequestStatus.COUNTER_PROPOSED))))
				.isInstanceOf(ConflictException.class);
		verify(settingsRepository, never()).save(any());
	}

	@Test
	void savesWhenEverythingAffectedWasShown() {
		instructorSomewhere(true);
		when(impactService.affectedBy(eq(INSTRUCTOR), any()))
				.thenReturn(affecting(BOOKED, waiting(OfficeHoursRequestStatus.COUNTER_PROPOSED)));

		service.update(INSTRUCTOR,
				confirming(new OfficeHoursAffectedItemRequest(BOOKED, OfficeHoursRequestStatus.ACCEPTED),
						new OfficeHoursAffectedItemRequest(WAITING, OfficeHoursRequestStatus.COUNTER_PROPOSED)));

		verify(settingsRepository).save(any());
		verify(ruleRepository).saveAll(any());
	}

	@Test
	void previewComparesTheRequestAndKeepsSavedOneOffsItLeavesOut() {
		OfficeHoursOneOff savedOneOff = new OfficeHoursOneOff();
		savedOneOff.setStartsAt(Instant.parse("2026-11-12T11:00:00Z"));
		savedOneOff.setEndsAt(Instant.parse("2026-11-12T12:00:00Z"));
		when(oneOffRepository.findByInstructorIdOrderByStartsAtAsc(INSTRUCTOR)).thenReturn(List.of(savedOneOff));
		when(impactService.affectedBy(eq(INSTRUCTOR), any())).thenReturn(affecting(BOOKED));

		Impact impact = service.preview(INSTRUCTOR, request(List.of(rule(DayOfWeek.TUESDAY, 14, 16, null)), List.of()));

		assertThat(impact.meetings()).hasSize(1);
		ArgumentCaptor<Setup> proposed = ArgumentCaptor.forClass(Setup.class);
		verify(impactService).affectedBy(eq(INSTRUCTOR), proposed.capture());
		assertThat(proposed.getValue().weekly()).singleElement()
				.satisfies(range -> assertThat(range.slotMinutes()).isEqualTo(60));
		assertThat(proposed.getValue().oneOffs())
				.containsExactly(new Hours(savedOneOff.getStartsAt(), savedOneOff.getEndsAt()));
		assertThat(proposed.getValue().oneOffMinutes()).isEqualTo(30);
		verify(ruleRepository, never()).deleteAll(any());
		verify(settingsRepository, never()).save(any());
	}

	@Test
	void previewRefusesHoursASaveWouldRefuse() {
		assertThatThrownBy(() -> service.preview(INSTRUCTOR,
				request(List.of(rule(DayOfWeek.MONDAY, 12, 10, null)), List.of())))
				.isInstanceOf(InvalidRequestException.class);
		verify(impactService, never()).affectedBy(any(), any());
	}
}
