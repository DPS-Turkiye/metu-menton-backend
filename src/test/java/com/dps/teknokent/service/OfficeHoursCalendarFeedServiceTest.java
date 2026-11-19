package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.AppLanguage;
import com.dps.teknokent.domain.OfficeHoursRole;
import com.dps.teknokent.entity.OfficeHoursCalendarFeed;
import com.dps.teknokent.entity.OfficeHoursCourse;
import com.dps.teknokent.entity.OfficeHoursEnrollment;
import com.dps.teknokent.entity.OfficeHoursMeeting;
import com.dps.teknokent.entity.OfficeHoursRequest;
import com.dps.teknokent.entity.OfficeHoursUser;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.OfficeHoursCalendarFeedRepository;
import com.dps.teknokent.repository.OfficeHoursCourseRepository;
import com.dps.teknokent.repository.OfficeHoursEnrollmentRepository;
import com.dps.teknokent.repository.OfficeHoursMeetingRepository;
import com.dps.teknokent.repository.OfficeHoursRequestRepository;
import com.dps.teknokent.repository.OfficeHoursUserRepository;

@ExtendWith(MockitoExtension.class)
class OfficeHoursCalendarFeedServiceTest {

	private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-00000000f001");
	private static final UUID ACADEMIC = UUID.fromString("00000000-0000-0000-0000-00000000f002");
	private static final UUID COURSE = UUID.fromString("00000000-0000-0000-0000-00000000f003");
	private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-00000000f004");
	private static final UUID MEETING = UUID.fromString("00000000-0000-0000-0000-00000000f005");
	private static final Instant NOW = Instant.parse("2026-11-12T15:00:00Z");
	private static final String TOKEN = "a".repeat(43);

	@Mock
	private OfficeHoursCalendarFeedRepository feedRepository;
	@Mock
	private OfficeHoursUserRepository userRepository;
	@Mock
	private OfficeHoursCourseRepository courseRepository;
	@Mock
	private OfficeHoursEnrollmentRepository enrollmentRepository;
	@Mock
	private OfficeHoursMeetingRepository meetingRepository;
	@Mock
	private OfficeHoursRequestRepository requestRepository;
	@Mock
	private OfficeHoursSlotService slotService;

	private OfficeHoursCalendarFeedService service;

	@BeforeEach
	void setUp() {
		service = new OfficeHoursCalendarFeedService(feedRepository, userRepository, courseRepository,
				enrollmentRepository, meetingRepository, requestRepository, slotService,
				Clock.fixed(NOW, ZoneOffset.UTC));
		lenient().when(userRepository.findAllById(any()))
				.thenReturn(List.of(person(STUDENT, "Zeynep Demir"), person(ACADEMIC, "Ayşe Yılmaz")));
		lenient().when(courseRepository.findAllById(any())).thenReturn(List.of(course()));
		lenient().when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course()));
	}

	private static OfficeHoursUser person(UUID id, String name) {
		OfficeHoursUser person = new OfficeHoursUser();
		person.setId(id);
		person.setFullName(name);
		return person;
	}

	private static OfficeHoursCourse course() {
		OfficeHoursCourse course = new OfficeHoursCourse();
		course.setId(COURSE);
		course.setTitle("CENG 140 Programlama");
		return course;
	}

	private static OfficeHoursEnrollment enrollment(UUID userId, OfficeHoursRole role) {
		OfficeHoursEnrollment enrollment = new OfficeHoursEnrollment();
		enrollment.setUserId(userId);
		enrollment.setCourseId(COURSE);
		enrollment.setRole(role);
		return enrollment;
	}

	private static OfficeHoursCalendarFeed feed(UUID userId, UUID courseId) {
		OfficeHoursCalendarFeed feed = new OfficeHoursCalendarFeed();
		feed.setUserId(userId);
		feed.setCourseId(courseId);
		feed.setToken(TOKEN);
		return feed;
	}

	private void meetingToday() {
		meetingToday(COURSE, null);
	}

	private void meetingToday(UUID courseId, Instant cancelledAt) {
		OfficeHoursMeeting meeting = new OfficeHoursMeeting();
		meeting.setId(MEETING);
		meeting.setRequestId(REQUEST);
		meeting.setCourseId(courseId);
		meeting.setCancelledAt(cancelledAt);
		meeting.setStudentId(STUDENT);
		meeting.setInstructorId(ACADEMIC);
		meeting.setScheduledAt(Instant.parse("2026-11-17T11:00:00Z"));
		meeting.setDurationMinutes(45);
		OfficeHoursRequest request = new OfficeHoursRequest();
		request.setId(REQUEST);
		request.setTopic("Ödev 2, özyineleme");
		when(meetingRepository.findForPersonFrom(any(), any())).thenReturn(List.of(meeting));
		when(requestRepository.findAllById(any())).thenReturn(List.of(request));
	}

	@Test
	void makesThePersonalLinkOnceAndHandsBackTheSavedOne() {
		when(userRepository.existsById(STUDENT)).thenReturn(true);
		when(feedRepository.findByUserId(STUDENT)).thenReturn(Optional.of(feed(STUDENT, null)));

		assertThat(service.personalFeed(STUDENT).token()).isEqualTo(TOKEN);
		// A race-safe insert that does nothing when the row is there; the saved token wins.
		verify(feedRepository).insertForUserIfAbsent(any(), eq(STUDENT), anyString(), eq(NOW));
	}

	@Test
	void aPersonWhoNeverLaunchedHasNoLink() {
		assertThatThrownBy(() -> service.personalFeed(STUDENT)).isInstanceOf(ResourceNotFoundException.class);
		verify(feedRepository, never()).insertForUserIfAbsent(any(), any(), any(), any());
	}

	@Test
	void resettingGivesANewRandomTokenThatFitsTheLinkPattern() {
		OfficeHoursCalendarFeed saved = feed(STUDENT, null);
		when(userRepository.existsById(STUDENT)).thenReturn(true);
		when(feedRepository.findByUserId(STUDENT)).thenReturn(Optional.of(saved));

		String token = service.resetPersonalFeed(STUDENT).token();

		assertThat(token).isNotEqualTo(TOKEN).matches("[A-Za-z0-9_-]{43}");
		assertThat(saved.getToken()).isEqualTo(token);
	}

	@Test
	void theCourseLinkIsForPeopleInTheCourse() {
		assertThatThrownBy(() -> service.courseFeed(STUDENT, COURSE)).isInstanceOf(ResourceNotFoundException.class);

		when(enrollmentRepository.findByUserIdAndCourseId(STUDENT, COURSE))
				.thenReturn(Optional.of(enrollment(STUDENT, OfficeHoursRole.STUDENT)));
		when(feedRepository.findByCourseId(COURSE)).thenReturn(Optional.of(feed(null, COURSE)));
		assertThat(service.courseFeed(STUDENT, COURSE).token()).isEqualTo(TOKEN);
	}

	@Test
	void onlyAnAcademicOfTheCourseResetsItsSharedLink() {
		when(enrollmentRepository.findByUserIdAndCourseId(STUDENT, COURSE))
				.thenReturn(Optional.of(enrollment(STUDENT, OfficeHoursRole.STUDENT)));
		assertThatThrownBy(() -> service.resetCourseFeed(STUDENT, COURSE))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

		OfficeHoursCalendarFeed saved = feed(null, COURSE);
		when(enrollmentRepository.findByUserIdAndCourseId(ACADEMIC, COURSE))
				.thenReturn(Optional.of(enrollment(ACADEMIC, OfficeHoursRole.INSTRUCTOR)));
		when(feedRepository.findByCourseId(COURSE)).thenReturn(Optional.of(saved));
		assertThat(service.resetCourseFeed(ACADEMIC, COURSE).token()).isNotEqualTo(TOKEN).isEqualTo(saved.getToken());
	}

	@Test
	void aMalformedLinkNeverReachesTheDatabaseAndAnUnknownOneHasNoFeed() {
		assertThat(service.feed("short", AppLanguage.TR)).isEmpty();
		assertThat(service.feed(TOKEN + "'", AppLanguage.TR)).isEmpty();
		verifyNoInteractions(feedRepository);

		assertThat(service.feed(TOKEN, AppLanguage.TR)).isEmpty();
	}

	@Test
	void aStudentsFeedNamesTheAcademicTheCourseAndTheTopic() {
		when(feedRepository.findByToken(TOKEN)).thenReturn(Optional.of(feed(STUDENT, null)));
		meetingToday();

		String calendar = service.feed(TOKEN, AppLanguage.TR).orElseThrow();

		assertThat(calendar).contains("X-WR-CALNAME:Ofis saatlerim", "UID:meeting-" + MEETING + "@office-hours.menton",
				"DTSTART:20261117T110000Z", "DTEND:20261117T114500Z", "SUMMARY:Ofis saati: Ayşe Yılmaz",
				"DESCRIPTION:Ders: CENG 140 Programlama\\nKonu: Ödev 2\\, özyineleme");
		verify(meetingRepository).findForPersonFrom(STUDENT, NOW.minus(OfficeHoursCalendarFeedService.KEPT_PAST));
	}

	@Test
	void anAcademicsFeedNamesTheStudentInEnglishWhenAsked() {
		when(feedRepository.findByToken(TOKEN)).thenReturn(Optional.of(feed(ACADEMIC, null)));
		meetingToday();

		String calendar = service.feed(TOKEN, AppLanguage.EN).orElseThrow();

		assertThat(calendar).contains("X-WR-CALNAME:My office hours", "SUMMARY:Office hours: Zeynep Demir",
				"DESCRIPTION:Course: CENG 140 Programlama\\nTopic: Ödev 2\\, özyineleme");
	}

	@Test
	void aCourseFeedShowsItsAcademicsHoursAndNoStudent() {
		when(feedRepository.findByToken(TOKEN)).thenReturn(Optional.of(feed(null, COURSE)));
		when(enrollmentRepository.findByCourseIdAndRole(COURSE, OfficeHoursRole.INSTRUCTOR))
				.thenReturn(List.of(enrollment(ACADEMIC, OfficeHoursRole.INSTRUCTOR)));
		when(userRepository.findAllById(List.of(ACADEMIC))).thenReturn(List.of(person(ACADEMIC, "Ayşe Yılmaz")));
		when(slotService.hoursAhead(ACADEMIC)).thenReturn(List.of(
				new OfficeHoursSlotService.Hours(Instant.parse("2026-11-24T11:00:00Z"),
						Instant.parse("2026-11-24T13:00:00Z")),
				new OfficeHoursSlotService.Hours(Instant.parse("2026-11-17T11:00:00Z"),
						Instant.parse("2026-11-17T13:00:00Z"))));

		String calendar = service.feed(TOKEN, AppLanguage.TR).orElseThrow();

		assertThat(calendar).contains("X-WR-CALNAME:CENG 140 Programlama: ofis saatleri",
				"SUMMARY:Ofis saati: Ayşe Yılmaz", "DTEND:20261117T130000Z");
		assertThat(calendar.indexOf("DTSTART:20261117T110000Z"))
				.isLessThan(calendar.indexOf("DTSTART:20261124T110000Z"));
		assertThat(calendar).doesNotContain("Zeynep");
		verifyNoInteractions(meetingRepository, requestRepository);
	}

	@Test
	void aCancelledMeetingStaysUnderItsUidMarkedCancelled() {
		when(feedRepository.findByToken(TOKEN)).thenReturn(Optional.of(feed(STUDENT, null)));
		meetingToday(COURSE, Instant.parse("2026-11-15T09:00:00Z"));

		String calendar = service.feed(TOKEN, AppLanguage.TR).orElseThrow();

		assertThat(calendar).contains("UID:meeting-" + MEETING + "@office-hours.menton",
				"SUMMARY:İptal edildi · Ofis saati: Ayşe Yılmaz", "SEQUENCE:1", "STATUS:CANCELLED");
	}

	@Test
	void aMeetingBookedFromMentonHasNoCourseAndStillShows() {
		when(feedRepository.findByToken(TOKEN)).thenReturn(Optional.of(feed(STUDENT, null)));
		meetingToday(null, null);

		String calendar = service.feed(TOKEN, AppLanguage.EN).orElseThrow();

		assertThat(calendar).contains("SUMMARY:Office hours: Ayşe Yılmaz", "DESCRIPTION:Topic: Ödev 2\\, özyineleme",
				"STATUS:CONFIRMED");
		verify(courseRepository).findAllById(Set.of());
	}
}
