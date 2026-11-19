package com.dps.teknokent.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.AppLanguage;
import com.dps.teknokent.domain.OfficeHoursRole;
import com.dps.teknokent.dto.responses.OfficeHoursCalendarFeedResponse;
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
import com.dps.teknokent.util.ICalendarWriter;
import com.dps.teknokent.util.Texts;

/**
 * Calendar links for Moodle's calendar (FR-OH-23). LTI alone cannot write to Moodle's calendar, so Moodle subscribes
 * to a feed instead and fetches it from its own server, with no sign-in: the random token in the link is the key.
 * <ul>
 * <li><b>A person's feed</b>: the meetings they are in, as the student or the academic.</li>
 * <li><b>A course's feed</b>: its academics' office hours, with no student in it, for the whole course to see.</li>
 * </ul>
 */
@Service
public class OfficeHoursCalendarFeedService {

	/** Recent meetings stay in a person's calendar for a while instead of vanishing the moment they end. */
	static final Duration KEPT_PAST = Duration.ofDays(30);
	private static final int TOKEN_BYTES = 32;
	/** 32 random bytes in URL-safe Base64 with no padding. */
	private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
	private static final String UID_DOMAIN = "@office-hours.menton";

	private final OfficeHoursCalendarFeedRepository feedRepository;
	private final OfficeHoursUserRepository userRepository;
	private final OfficeHoursCourseRepository courseRepository;
	private final OfficeHoursEnrollmentRepository enrollmentRepository;
	private final OfficeHoursMeetingRepository meetingRepository;
	private final OfficeHoursRequestRepository requestRepository;
	private final OfficeHoursSlotService slotService;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	@Autowired
	public OfficeHoursCalendarFeedService(OfficeHoursCalendarFeedRepository feedRepository,
			OfficeHoursUserRepository userRepository, OfficeHoursCourseRepository courseRepository,
			OfficeHoursEnrollmentRepository enrollmentRepository, OfficeHoursMeetingRepository meetingRepository,
			OfficeHoursRequestRepository requestRepository, OfficeHoursSlotService slotService) {
		this(feedRepository, userRepository, courseRepository, enrollmentRepository, meetingRepository,
				requestRepository, slotService, Clock.systemUTC());
	}

	OfficeHoursCalendarFeedService(OfficeHoursCalendarFeedRepository feedRepository,
			OfficeHoursUserRepository userRepository, OfficeHoursCourseRepository courseRepository,
			OfficeHoursEnrollmentRepository enrollmentRepository, OfficeHoursMeetingRepository meetingRepository,
			OfficeHoursRequestRepository requestRepository, OfficeHoursSlotService slotService, Clock clock) {
		this.feedRepository = feedRepository;
		this.userRepository = userRepository;
		this.courseRepository = courseRepository;
		this.enrollmentRepository = enrollmentRepository;
		this.meetingRepository = meetingRepository;
		this.requestRepository = requestRepository;
		this.slotService = slotService;
		this.clock = clock;
	}

	/** The person's own link, made the first time they ask for it. */
	@Transactional
	public OfficeHoursCalendarFeedResponse personalFeed(UUID userId) {
		return new OfficeHoursCalendarFeedResponse(personalRow(userId).getToken());
	}

	/** A new link for the person; the old one stops working, so a link they shared by mistake can be closed. */
	@Transactional
	public OfficeHoursCalendarFeedResponse resetPersonalFeed(UUID userId) {
		OfficeHoursCalendarFeed feed = personalRow(userId);
		feed.setToken(newToken());
		return new OfficeHoursCalendarFeedResponse(feed.getToken());
	}

	/** The course's link, for anyone in the course; it holds no student's data. */
	@Transactional
	public OfficeHoursCalendarFeedResponse courseFeed(UUID userId, UUID courseId) {
		requireEnrollment(userId, courseId);
		return new OfficeHoursCalendarFeedResponse(courseRow(courseId).getToken());
	}

	/**
	 * A new course link. Only an academic of the course may reset it: everyone in the course shares the link, so a
	 * student resetting it would cut it off for the whole class.
	 */
	@Transactional
	public OfficeHoursCalendarFeedResponse resetCourseFeed(UUID userId, UUID courseId) {
		if (requireEnrollment(userId, courseId).getRole() != OfficeHoursRole.INSTRUCTOR) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the course's academics can reset its link.");
		}
		OfficeHoursCalendarFeed feed = courseRow(courseId);
		feed.setToken(newToken());
		return new OfficeHoursCalendarFeedResponse(feed.getToken());
	}

	/** The feed behind a link, or nothing for a link that never existed or was reset. */
	@Transactional(readOnly = true)
	public Optional<String> feed(@Nullable String token, AppLanguage language) {
		if (token == null || !TOKEN.matcher(token).matches()) {
			return Optional.empty();
		}
		return feedRepository.findByToken(token).map(feed -> {
			if (feed.getUserId() != null) {
				return personalCalendar(feed.getUserId(), language);
			}
			return courseCalendar(feed.getCourseId(), language);
		});
	}

	private String personalCalendar(UUID userId, AppLanguage language) {
		Instant now = clock.instant();
		List<OfficeHoursMeeting> meetings = meetingRepository.findForPersonFrom(userId, now.minus(KEPT_PAST));
		Map<UUID, OfficeHoursUser> people = byId(userRepository.findAllById(meetings.stream()
				.flatMap(meeting -> Stream.of(meeting.getStudentId(), meeting.getInstructorId()))
				.collect(Collectors.toSet())), OfficeHoursUser::getId);
		// A meeting booked from Menton has no course, and findAllById refuses a null id.
		Map<UUID, OfficeHoursCourse> courses = byId(courseRepository.findAllById(
				meetings.stream().map(OfficeHoursMeeting::getCourseId).filter(Objects::nonNull)
						.collect(Collectors.toSet())),
				OfficeHoursCourse::getId);
		Map<UUID, OfficeHoursRequest> requests = byId(requestRepository.findAllById(
				meetings.stream().map(OfficeHoursMeeting::getRequestId).collect(Collectors.toSet())),
				OfficeHoursRequest::getId);

		List<ICalendarWriter.Event> events = new ArrayList<>();
		for (OfficeHoursMeeting meeting : meetings) {
			// The other side of the meeting: the academic in a student's calendar, the student in an academic's.
			UUID otherId = meeting.getStudentId();
			if (userId.equals(meeting.getStudentId())) {
				otherId = meeting.getInstructorId();
			}
			Instant startsAt = meeting.getScheduledAt();
			boolean cancelled = meeting.getCancelledAt() != null;
			// The status line is not shown by every calendar (Moodle's import, for one), so the title says it too.
			String summaryKey = cancelled ? "officeHoursCalendar.meeting.cancelledSummary"
					: "officeHoursCalendar.meeting.summary";
			events.add(new ICalendarWriter.Event("meeting-" + meeting.getId() + UID_DOMAIN, startsAt,
					startsAt.plus(Duration.ofMinutes(meeting.getDurationMinutes())),
					Texts.get(language, summaryKey, nameOf(people.get(otherId))),
					description(language, meeting, courses, requests), cancelled));
		}
		return ICalendarWriter.write(Texts.get(language, "officeHoursCalendar.personal.name"), now, events);
	}

	private String courseCalendar(UUID courseId, AppLanguage language) {
		Instant now = clock.instant();
		List<UUID> academics = enrollmentRepository.findByCourseIdAndRole(courseId, OfficeHoursRole.INSTRUCTOR)
				.stream().map(OfficeHoursEnrollment::getUserId).toList();
		Map<UUID, OfficeHoursUser> people = byId(userRepository.findAllById(academics), OfficeHoursUser::getId);

		List<ICalendarWriter.Event> events = new ArrayList<>();
		for (UUID academicId : academics) {
			String summary = Texts.get(language, "officeHoursCalendar.hours.summary", nameOf(people.get(academicId)));
			for (OfficeHoursSlotService.Hours hours : slotService.hoursAhead(academicId)) {
				events.add(new ICalendarWriter.Event(
						"hours-" + academicId + "-" + hours.startsAt().getEpochSecond() + UID_DOMAIN,
						hours.startsAt(), hours.endsAt(), summary,
						Texts.get(language, "officeHoursCalendar.hours.description"), false));
			}
		}
		events.sort(Comparator.comparing(ICalendarWriter.Event::startsAt));
		String title = titleOf(courseRepository.findById(courseId).orElse(null));
		return ICalendarWriter.write(Texts.get(language, "officeHoursCalendar.course.name", title), now, events);
	}

	private static String description(AppLanguage language, OfficeHoursMeeting meeting,
			Map<UUID, OfficeHoursCourse> courses, Map<UUID, OfficeHoursRequest> requests) {
		String topic = topicOf(requests.get(meeting.getRequestId()));
		UUID courseId = meeting.getCourseId();
		if (courseId == null) {
			return Texts.get(language, "officeHoursCalendar.meeting.mentonDescription", topic);
		}
		return Texts.get(language, "officeHoursCalendar.meeting.description", titleOf(courses.get(courseId)), topic);
	}

	private OfficeHoursCalendarFeed personalRow(UUID userId) {
		if (!userRepository.existsById(userId)) {
			throw new ResourceNotFoundException("Person not found.");
		}
		feedRepository.insertForUserIfAbsent(UUID.randomUUID(), userId, newToken(), clock.instant());
		return feedRepository.findByUserId(userId).orElseThrow();
	}

	private OfficeHoursCalendarFeed courseRow(UUID courseId) {
		feedRepository.insertForCourseIfAbsent(UUID.randomUUID(), courseId, newToken(), clock.instant());
		return feedRepository.findByCourseId(courseId).orElseThrow();
	}

	/** A course the person never launched from does not exist for them, as everywhere else in office hours. */
	private OfficeHoursEnrollment requireEnrollment(UUID userId, UUID courseId) {
		return enrollmentRepository.findByUserIdAndCourseId(userId, courseId)
				.orElseThrow(() -> new ResourceNotFoundException("Course not found."));
	}

	private String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static <T> Map<UUID, T> byId(Collection<T> rows, Function<T, UUID> id) {
		return rows.stream().collect(Collectors.toMap(id, Function.identity()));
	}

	// The rows below are deleted with their meeting (ON DELETE CASCADE), so an empty one only shows up in a race
	// with a delete; the feed then says less instead of failing for everyone subscribed.
	private static String nameOf(@Nullable OfficeHoursUser person) {
		if (person == null) {
			return "";
		}
		return person.getFullName();
	}

	private static String titleOf(@Nullable OfficeHoursCourse course) {
		if (course == null) {
			return "";
		}
		return course.getTitle();
	}

	private static String topicOf(@Nullable OfficeHoursRequest request) {
		if (request == null) {
			return "";
		}
		return request.getTopic();
	}
}
