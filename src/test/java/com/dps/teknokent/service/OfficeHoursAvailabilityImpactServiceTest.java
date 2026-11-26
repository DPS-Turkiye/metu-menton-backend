package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dps.teknokent.domain.OfficeHoursRequestStatus;
import com.dps.teknokent.entity.OfficeHoursException;
import com.dps.teknokent.entity.OfficeHoursMeeting;
import com.dps.teknokent.entity.OfficeHoursOneOff;
import com.dps.teknokent.entity.OfficeHoursRequest;
import com.dps.teknokent.entity.OfficeHoursRule;
import com.dps.teknokent.entity.OfficeHoursSettings;
import com.dps.teknokent.repository.OfficeHoursExceptionRepository;
import com.dps.teknokent.repository.OfficeHoursMeetingRepository;
import com.dps.teknokent.repository.OfficeHoursOneOffRepository;
import com.dps.teknokent.repository.OfficeHoursRequestRepository;
import com.dps.teknokent.repository.OfficeHoursRuleRepository;
import com.dps.teknokent.repository.OfficeHoursSettingsRepository;
import com.dps.teknokent.service.OfficeHoursAvailabilityImpactService.Impact;
import com.dps.teknokent.service.OfficeHoursAvailabilityImpactService.Item;
import com.dps.teknokent.service.OfficeHoursSlotService.DaysAway;
import com.dps.teknokent.service.OfficeHoursSlotService.Hours;
import com.dps.teknokent.service.OfficeHoursSlotService.Setup;
import com.dps.teknokent.service.OfficeHoursSlotService.WeeklyRange;
import com.dps.teknokent.util.MeetingSlotTimes;

@ExtendWith(MockitoExtension.class)
class OfficeHoursAvailabilityImpactServiceTest {

	private static final UUID ACADEMIC = UUID.fromString("00000000-0000-0000-0000-00000000f001");
	/** Monday 9 November 2026, 09:00 in Turkey. */
	private static final Instant NOW = Instant.parse("2026-11-09T06:00:00Z");
	/** Tuesday 10 November 2026, 10:00 in Turkey. */
	private static final Instant TUESDAY_10AM = Instant.parse("2026-11-10T07:00:00Z");

	@Mock
	private OfficeHoursRuleRepository ruleRepository;
	@Mock
	private OfficeHoursExceptionRepository exceptionRepository;
	@Mock
	private OfficeHoursMeetingRepository meetingRepository;
	@Mock
	private OfficeHoursSettingsRepository settingsRepository;
	@Mock
	private OfficeHoursRequestRepository requestRepository;
	@Mock
	private OfficeHoursOneOffRepository oneOffRepository;

	private OfficeHoursAvailabilityImpactService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		OfficeHoursSlotService slots = new OfficeHoursSlotService(ruleRepository, exceptionRepository,
				meetingRepository, settingsRepository, requestRepository, oneOffRepository, clock);
		service = new OfficeHoursAvailabilityImpactService(slots, meetingRepository, requestRepository, clock);
		OfficeHoursSettings settings = new OfficeHoursSettings();
		settings.setDefaultMeetingDurationMinutes(30);
		lenient().when(settingsRepository.findById(ACADEMIC)).thenReturn(Optional.of(settings));
		lenient().when(exceptionRepository.findByInstructorIdOrderByStartsOnAsc(ACADEMIC)).thenReturn(List.of());
		lenient().when(oneOffRepository.findByInstructorIdOrderByStartsAtAsc(ACADEMIC)).thenReturn(List.of());
		lenient().when(meetingRepository
				.findByInstructorIdAndCancelledAtIsNullAndScheduledAtAfterOrderByScheduledAtAsc(ACADEMIC, NOW))
				.thenReturn(List.of());
		lenient().when(requestRepository.findByInstructorIdAndStatusIn(eq(ACADEMIC), any())).thenReturn(List.of());
	}

	private void saved(OfficeHoursRule... rules) {
		when(ruleRepository.findByInstructorIdOrderByDayOfWeekAscStartTimeAsc(ACADEMIC)).thenReturn(List.of(rules));
	}

	private static OfficeHoursRule rule(DayOfWeek day, int from, int to, int slotMinutes) {
		OfficeHoursRule rule = new OfficeHoursRule();
		rule.setDay(day);
		rule.setStartTime(LocalTime.of(from, 0));
		rule.setEndTime(LocalTime.of(to, 0));
		rule.setSlotMinutes(slotMinutes);
		return rule;
	}

	private static WeeklyRange range(DayOfWeek day, int from, int to, int slotMinutes) {
		return new WeeklyRange(day, LocalTime.of(from, 0), LocalTime.of(to, 0), slotMinutes);
	}

	private static Setup proposed(WeeklyRange... ranges) {
		return new Setup(List.of(ranges), List.of(), List.of(), 30);
	}

	private void booked(OfficeHoursMeeting... meetings) {
		when(meetingRepository.findByInstructorIdAndCancelledAtIsNullAndScheduledAtAfterOrderByScheduledAtAsc(ACADEMIC,
				NOW)).thenReturn(List.of(meetings));
	}

	private void open(OfficeHoursRequest... requests) {
		when(requestRepository.findByInstructorIdAndStatusIn(ACADEMIC, OfficeHoursSlotService.HOLDING))
				.thenReturn(List.of(requests));
	}

	private static OfficeHoursMeeting meeting(Instant at, int minutes) {
		OfficeHoursMeeting meeting = new OfficeHoursMeeting();
		meeting.setId(UUID.randomUUID());
		meeting.setRequestId(UUID.randomUUID());
		meeting.setInstructorId(ACADEMIC);
		meeting.setScheduledAt(at);
		meeting.setDurationMinutes(minutes);
		return meeting;
	}

	private static OfficeHoursRequest request(OfficeHoursRequestStatus status, String preferred, String proposed) {
		OfficeHoursRequest request = new OfficeHoursRequest();
		request.setId(UUID.randomUUID());
		request.setInstructorId(ACADEMIC);
		request.setStatus(status);
		request.setPreferredSlots(preferred);
		request.setProposedSlots(proposed);
		return request;
	}

	@Test
	void listsAMeetingWhoseWeeklyRangeIsRemovedAndLeavesOneThatIsStillCovered() {
		saved(rule(DayOfWeek.TUESDAY, 10, 12, 30), rule(DayOfWeek.WEDNESDAY, 10, 12, 30));
		OfficeHoursMeeting tuesday = meeting(TUESDAY_10AM, 30);
		OfficeHoursMeeting wednesday = meeting(Instant.parse("2026-11-11T07:00:00Z"), 30);
		booked(tuesday, wednesday);

		Impact impact = service.affectedBy(ACADEMIC, proposed(range(DayOfWeek.WEDNESDAY, 10, 12, 30)));

		assertThat(impact.meetings()).containsExactly(tuesday);
		assertThat(impact.items()).containsExactly(new Item(tuesday.getRequestId(), OfficeHoursRequestStatus.ACCEPTED));
	}

	@Test
	void doesNotRaiseAMeetingThatWasAlreadyOutsideTheHoursBeforeThisChange() {
		saved(rule(DayOfWeek.TUESDAY, 10, 12, 30));
		// An alternative typed by hand at 15:00, outside every range, booked long ago.
		booked(meeting(Instant.parse("2026-11-10T12:00:00Z"), 30));

		assertThat(service.affectedBy(ACADEMIC, proposed(range(DayOfWeek.TUESDAY, 10, 12, 30))).meetings()).isEmpty();
	}

	@Test
	void treatsTouchingRangesAsOneSoAMeetingAcrossTheJoinStaysInside() {
		saved(rule(DayOfWeek.TUESDAY, 10, 12, 60));
		// 10:45–11:15 crosses from the first proposed range into the second.
		booked(meeting(Instant.parse("2026-11-10T07:45:00Z"), 30));

		Impact impact = service.affectedBy(ACADEMIC,
				proposed(range(DayOfWeek.TUESDAY, 10, 11, 60), range(DayOfWeek.TUESDAY, 11, 12, 60)));

		assertThat(impact.meetings()).isEmpty();
	}

	@Test
	void listsAMeetingOnANewDayOff() {
		saved(rule(DayOfWeek.TUESDAY, 10, 12, 30));
		booked(meeting(TUESDAY_10AM, 30));
		Setup away = new Setup(List.of(range(DayOfWeek.TUESDAY, 10, 12, 30)),
				List.of(new DaysAway(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10))), List.of(), 30);

		assertThat(service.affectedBy(ACADEMIC, away).meetings()).hasSize(1);
	}

	@Test
	void stillSeesAMeetingBeyondTheEightWeeksStudentsBookIn() {
		saved(rule(DayOfWeek.TUESDAY, 10, 12, 30));
		// Tuesday 12 January 2027, nine weeks ahead.
		booked(meeting(Instant.parse("2027-01-12T07:00:00Z"), 30));

		assertThat(service.affectedBy(ACADEMIC, proposed()).meetings()).hasSize(1);
	}

	@Test
	void listsARequestInsideTheBookingLeadTimeToo() {
		saved(rule(DayOfWeek.MONDAY, 10, 12, 30));
		// Monday 10:30, an hour and a half from now: too soon to book, still held by the open request.
		OfficeHoursRequest soon = request(OfficeHoursRequestStatus.PENDING, "2026-11-09T10:30", null);
		open(soon);

		Impact impact = service.affectedBy(ACADEMIC, proposed());

		assertThat(impact.requests()).singleElement().satisfies(affected -> {
			assertThat(affected.request()).isEqualTo(soon);
			assertThat(affected.lostTimes()).containsExactly(Instant.parse("2026-11-09T07:30:00Z"));
			assertThat(affected.keptTimes()).isEmpty();
		});
	}

	@Test
	void splitsARequestsTimesIntoLostAndKept() {
		saved(rule(DayOfWeek.TUESDAY, 10, 12, 30), rule(DayOfWeek.WEDNESDAY, 10, 12, 30));
		open(request(OfficeHoursRequestStatus.PENDING, "2026-11-10T10:00,2026-11-11T10:00", null));

		Impact impact = service.affectedBy(ACADEMIC, proposed(range(DayOfWeek.WEDNESDAY, 10, 12, 30)));

		assertThat(impact.requests()).singleElement().satisfies(affected -> {
			assertThat(affected.lostTimes()).containsExactly(TUESDAY_10AM);
			assertThat(affected.keptTimes()).containsExactly(Instant.parse("2026-11-11T07:00:00Z"));
		});
	}

	@Test
	void readsTheOfferedTimesOfARequestWaitingOnTheStudent() {
		saved(rule(DayOfWeek.TUESDAY, 10, 12, 30), rule(DayOfWeek.WEDNESDAY, 10, 12, 30));
		OfficeHoursRequest offered = request(OfficeHoursRequestStatus.COUNTER_PROPOSED, "2026-11-11T10:00",
				"2026-11-10T10:00");
		open(offered);

		Impact impact = service.affectedBy(ACADEMIC, proposed(range(DayOfWeek.WEDNESDAY, 10, 12, 30)));

		assertThat(impact.items())
				.containsExactly(new Item(offered.getId(), OfficeHoursRequestStatus.COUNTER_PROPOSED));
		assertThat(impact.requests().getFirst().lostTimes()).containsExactly(TUESDAY_10AM);
	}

	@Test
	void doesNotRaiseARequestTimeThatWasNeverAStartInTheSavedHours() {
		saved(rule(DayOfWeek.TUESDAY, 10, 12, 30));
		// 10:10 was typed by hand; no slot of the saved hours starts then.
		open(request(OfficeHoursRequestStatus.PENDING, "2026-11-10T10:10", null));

		assertThat(service.affectedBy(ACADEMIC, proposed()).requests()).isEmpty();
	}

	@Test
	void aLongerSlotLosesTheStartsInBetween() {
		saved(rule(DayOfWeek.TUESDAY, 10, 12, 30));
		open(request(OfficeHoursRequestStatus.PENDING, "2026-11-10T10:00,2026-11-10T10:30", null));

		Impact impact = service.affectedBy(ACADEMIC, proposed(range(DayOfWeek.TUESDAY, 10, 12, 60)));

		assertThat(impact.requests().getFirst().lostTimes()).containsExactly(Instant.parse("2026-11-10T07:30:00Z"));
		assertThat(impact.requests().getFirst().keptTimes()).containsExactly(TUESDAY_10AM);
	}

	@Test
	void comparesOneOffHoursAsWell() {
		saved();
		OfficeHoursOneOff oneOff = new OfficeHoursOneOff();
		oneOff.setStartsAt(Instant.parse("2026-11-12T11:00:00Z"));
		oneOff.setEndsAt(Instant.parse("2026-11-12T12:00:00Z"));
		when(oneOffRepository.findByInstructorIdOrderByStartsAtAsc(ACADEMIC)).thenReturn(List.of(oneOff));
		booked(meeting(Instant.parse("2026-11-12T11:00:00Z"), 30));
		Setup kept = new Setup(List.of(), List.of(),
				List.of(new Hours(oneOff.getStartsAt(), oneOff.getEndsAt())), 30);

		assertThat(service.affectedBy(ACADEMIC, kept).meetings()).isEmpty();
		assertThat(service.affectedBy(ACADEMIC, proposed()).meetings()).hasSize(1);
	}

	@Test
	void readsNothingElseWhenTheAcademicHasNothingBookedOrOpen() {
		assertThat(service.affectedBy(ACADEMIC, proposed()).items()).isEmpty();
		verify(ruleRepository, never()).findByInstructorIdOrderByDayOfWeekAscStartTimeAsc(any());
	}

	@Test
	void anAwayRangeOnTheSavedSideMeansTheMeetingWasNotCoveredBefore() {
		saved(rule(DayOfWeek.TUESDAY, 10, 12, 30));
		OfficeHoursException away = new OfficeHoursException();
		away.setStartsOn(LocalDate.of(2026, 11, 10));
		away.setEndsOn(LocalDate.of(2026, 11, 10));
		when(exceptionRepository.findByInstructorIdOrderByStartsOnAsc(ACADEMIC)).thenReturn(List.of(away));
		booked(meeting(TUESDAY_10AM, 30));

		assertThat(service.affectedBy(ACADEMIC, proposed()).meetings()).isEmpty();
	}

	@Test
	void readsOnlyTheDayOfATimeYearsAheadAndStillComparesItsHours() {
		// A time typed by hand far ahead used to make every week up to it be worked out; only its own day is read now.
		Instant farAhead = Instant.parse("9999-12-30T07:00:00Z");
		DayOfWeek day = farAhead.atZone(MeetingSlotTimes.APP_ZONE).getDayOfWeek();
		saved(rule(day, 9, 17, 30));
		OfficeHoursRequest offered = request(OfficeHoursRequestStatus.COUNTER_PROPOSED, "2026-11-10T10:00",
				"9999-12-30T10:00");
		open(offered);

		Impact impact = assertTimeoutPreemptively(Duration.ofSeconds(2),
				() -> service.affectedBy(ACADEMIC, proposed()));
		Impact kept = service.affectedBy(ACADEMIC, proposed(range(day, 9, 17, 30)));

		assertThat(impact.requests()).singleElement()
				.satisfies(affected -> assertThat(affected.lostTimes()).containsExactly(farAhead));
		assertThat(kept.requests()).isEmpty();
	}
}
