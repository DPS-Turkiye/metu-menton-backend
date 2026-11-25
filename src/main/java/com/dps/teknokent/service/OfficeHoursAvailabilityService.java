package com.dps.teknokent.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.OfficeHoursRole;
import com.dps.teknokent.dto.requests.CreateMentorAvailabilityRuleRequest;
import com.dps.teknokent.dto.requests.MentorAvailabilityExceptionRequest;
import com.dps.teknokent.dto.requests.OfficeHoursAffectedItemRequest;
import com.dps.teknokent.dto.requests.OfficeHoursOneOffRequest;
import com.dps.teknokent.dto.requests.UpdateOfficeHoursAvailabilityRequest;
import com.dps.teknokent.dto.responses.OfficeHoursAvailabilityResponse;
import com.dps.teknokent.entity.OfficeHoursException;
import com.dps.teknokent.entity.OfficeHoursOneOff;
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
import com.dps.teknokent.service.OfficeHoursAvailabilityImpactService.Item;
import com.dps.teknokent.service.OfficeHoursSlotService.DateRange;
import com.dps.teknokent.service.OfficeHoursSlotService.Hours;
import com.dps.teknokent.service.OfficeHoursSlotService.Setup;
import com.dps.teknokent.service.OfficeHoursSlotService.WeeklyRange;
import com.dps.teknokent.util.MeetingSlotTimes;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

/**
 * An academic's office hours: weekly ranges, dates away and meeting defaults, saved as a whole and checked with
 * the same rules as a mentor's availability. Like a mentor's, they belong to the person and apply to every course
 * they teach; only someone Moodle made an instructor somewhere may set them. Bookable slots are made from these
 * when students start booking.
 */
@Service
public class OfficeHoursAvailabilityService {

	/** The 409's code when the save would touch a meeting or request the academic was not shown. */
	public static final String AFFECTED_CHANGED = "OFFICE_HOURS_AFFECTED_CHANGED";
	private static final int DEFAULT_SLOT_MINUTES = 60;
	private static final Duration SHORTEST_MEETING = Duration.ofMinutes(15);

	private final OfficeHoursEnrollmentRepository enrollmentRepository;
	private final OfficeHoursSettingsRepository settingsRepository;
	private final OfficeHoursRuleRepository ruleRepository;
	private final OfficeHoursExceptionRepository exceptionRepository;
	private final OfficeHoursOneOffRepository oneOffRepository;
	private final OfficeHoursUserRepository userRepository;
	private final OfficeHoursAvailabilityImpactService impactService;
	private final Clock clock;

	@Autowired
	public OfficeHoursAvailabilityService(OfficeHoursEnrollmentRepository enrollmentRepository,
			OfficeHoursSettingsRepository settingsRepository, OfficeHoursRuleRepository ruleRepository,
			OfficeHoursExceptionRepository exceptionRepository, OfficeHoursOneOffRepository oneOffRepository,
			OfficeHoursUserRepository userRepository,
			OfficeHoursAvailabilityImpactService impactService) {
		this(enrollmentRepository, settingsRepository, ruleRepository, exceptionRepository, oneOffRepository,
				userRepository, impactService, Clock.systemUTC());
	}

	OfficeHoursAvailabilityService(OfficeHoursEnrollmentRepository enrollmentRepository,
			OfficeHoursSettingsRepository settingsRepository, OfficeHoursRuleRepository ruleRepository,
			OfficeHoursExceptionRepository exceptionRepository, OfficeHoursOneOffRepository oneOffRepository,
			OfficeHoursUserRepository userRepository,
			OfficeHoursAvailabilityImpactService impactService, Clock clock) {
		this.enrollmentRepository = enrollmentRepository;
		this.settingsRepository = settingsRepository;
		this.ruleRepository = ruleRepository;
		this.exceptionRepository = exceptionRepository;
		this.oneOffRepository = oneOffRepository;
		this.userRepository = userRepository;
		this.impactService = impactService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public OfficeHoursAvailabilityResponse find(UUID instructorId) {
		requireInstructor(instructorId);
		return read(instructorId);
	}

	/** Someone's office hours, once the caller is known to be an academic (Moodle or Menton). */
	@Transactional(readOnly = true)
	public OfficeHoursAvailabilityResponse read(UUID instructorId) {
		OfficeHoursSettings settings = settingsRepository.findById(instructorId).orElseGet(OfficeHoursSettings::new);
		return response(instructorId, settings);
	}

	@Transactional
	public OfficeHoursAvailabilityResponse update(UUID instructorId, UpdateOfficeHoursAvailabilityRequest request) {
		requireInstructor(instructorId);
		return write(instructorId, request);
	}

	/**
	 * What saving {@code request} would leave outside the academic's hours, without saving anything (FR-OH-29), once
	 * the caller is known to be an academic.
	 */
	@Transactional(readOnly = true)
	public Impact preview(UUID instructorId, UpdateOfficeHoursAvailabilityRequest request) {
		validate(request);
		return impactService.affectedBy(instructorId, proposedSetup(instructorId, request));
	}

	/**
	 * Saves someone's office hours as a whole, once the caller is known to be an academic. When the request says
	 * what the academic was shown, anything else this save would leave out refuses it (409), so a request that came
	 * in while they were editing is never passed over unseen.
	 */
	@Transactional
	public OfficeHoursAvailabilityResponse write(UUID instructorId, UpdateOfficeHoursAvailabilityRequest request) {
		validate(request);
		List<OfficeHoursOneOffRequest> oneOffs = request.oneOffs();
		// The same lock a new request takes before it reads the open slots: one of the two waits for the other, so
		// what is checked below is what the save really replaces. Taken before the deletes, and after the Menton
		// side has made the person's row.
		userRepository.findForUpdate(instructorId);
		List<OfficeHoursAffectedItemRequest> confirmed = request.confirmedAffected();
		if (confirmed != null) {
			requireOnlyConfirmed(instructorId, request, confirmed);
		}
		Instant now = clock.instant();

		ruleRepository.deleteAll(ruleRepository.findByInstructorIdOrderByDayOfWeekAscStartTimeAsc(instructorId));
		exceptionRepository.deleteAll(exceptionRepository.findByInstructorIdOrderByStartsOnAsc(instructorId));
		// Deletes must reach the database first: the replacements may reuse the same unique day/time and dates.
		// One-offs left out of the request stay as saved, so a client that doesn't know them loses none.
		if (oneOffs != null) {
			oneOffRepository.deleteAll(oneOffRepository.findByInstructorIdOrderByStartsAtAsc(instructorId));
			oneOffRepository.flush();
		}
		ruleRepository.flush();
		exceptionRepository.flush();

		OfficeHoursSettings settings = settingsRepository.findById(instructorId).orElseGet(() -> {
			OfficeHoursSettings created = new OfficeHoursSettings();
			created.setInstructorId(instructorId);
			return created;
		});
		settings.setStudentCapacity(request.studentCapacity());
		settings.setDefaultMeetingDurationMinutes(request.defaultMeetingDurationMinutes());
		settings.setMeetingBufferMinutes(request.meetingBufferMinutes());
		settings.setUpdatedAt(now);
		settingsRepository.save(settings);

		ruleRepository.saveAll(request.rules().stream().map(rule -> newRule(instructorId, rule, now)).toList());
		exceptionRepository.saveAll(request.exceptions().stream()
				.map(exception -> newException(instructorId, exception)).toList());
		if (oneOffs != null) {
			oneOffRepository.saveAll(oneOffs.stream().map(oneOff -> newOneOff(instructorId, oneOff)).toList());
			oneOffRepository.flush();
		}
		ruleRepository.flush();
		exceptionRepository.flush();
		return response(instructorId, settings);
	}

	private void requireOnlyConfirmed(UUID instructorId, UpdateOfficeHoursAvailabilityRequest request,
			List<OfficeHoursAffectedItemRequest> confirmed) {
		Set<Item> shown = confirmed.stream().map(item -> new Item(item.requestId(), item.status()))
				.collect(Collectors.toSet());
		Set<Item> affected = impactService.affectedBy(instructorId, proposedSetup(instructorId, request)).items();
		if (!shown.containsAll(affected)) {
			throw new ConflictException(Texts.forRequest("error.officeHoursAffectedChanged"), AFFECTED_CHANGED);
		}
	}

	static void validate(UpdateOfficeHoursAvailabilityRequest request) {
		AvailabilityRules.validateRules(request.rules());
		AvailabilityRules.validateExceptions(request.exceptions());
		List<OfficeHoursOneOffRequest> oneOffs = request.oneOffs();
		if (oneOffs != null) {
			validateOneOffs(oneOffs, request.exceptions());
		}
	}

	/** The hours as they would be once {@code request} is saved; one-offs left out keep the saved ones. */
	private Setup proposedSetup(UUID instructorId, UpdateOfficeHoursAvailabilityRequest request) {
		List<OfficeHoursOneOffRequest> oneOffs = request.oneOffs();
		List<Hours> oneOffHours;
		if (oneOffs == null) {
			oneOffHours = oneOffRepository.findByInstructorIdOrderByStartsAtAsc(instructorId).stream()
					.map(oneOff -> new Hours(oneOff.getStartsAt(), oneOff.getEndsAt())).toList();
		} else {
			oneOffHours = oneOffs.stream()
					.map(oneOff -> new Hours(oneOff.startsAt().toInstant(), oneOff.endsAt().toInstant())).toList();
		}
		return new Setup(
				request.rules().stream().map(rule -> new WeeklyRange(rule.dayOfWeek(), rule.startTime(),
						rule.endTime(), slotMinutes(rule))).toList(),
				request.exceptions().stream().map(range -> new DateRange(range.startsOn(), range.endsOn())).toList(),
				oneOffHours, request.defaultMeetingDurationMinutes());
	}

	private static int slotMinutes(CreateMentorAvailabilityRuleRequest rule) {
		Integer minutes = rule.slotMinutes();
		if (minutes == null) {
			return DEFAULT_SLOT_MINUTES;
		}
		return minutes;
	}

	private void requireInstructor(UUID userId) {
		if (!enrollmentRepository.existsByUserIdAndRole(userId, OfficeHoursRole.INSTRUCTOR)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only academics set office hours.");
		}
	}

	private OfficeHoursAvailabilityResponse response(UUID instructorId, OfficeHoursSettings settings) {
		return new OfficeHoursAvailabilityResponse(
				ruleRepository.findByInstructorIdOrderByDayOfWeekAscStartTimeAsc(instructorId)
						.stream().map(OfficeHoursAvailabilityResponse.Rule::fromEntity).toList(),
				exceptionRepository.findByInstructorIdOrderByStartsOnAsc(instructorId)
						.stream().map(OfficeHoursAvailabilityResponse.DateOff::fromEntity).toList(),
				settings.getStudentCapacity(),
				settings.getDefaultMeetingDurationMinutes(),
				settings.getMeetingBufferMinutes(),
				oneOffRepository.findByInstructorIdOrderByStartsAtAsc(instructorId)
						.stream().map(OfficeHoursAvailabilityResponse.OneOff::fromEntity).toList());
	}

	/**
	 * One-off hours end after they start, last at least the shortest meeting, end on the day they start (so a stray
	 * end date can't turn one entry into months of slots), don't overlap each other, and don't fall on a day off: the
	 * day off would hide them, so the academic lifts it first. Past ones are kept as sent, since every save sends the
	 * whole list back.
	 */
	static void validateOneOffs(List<OfficeHoursOneOffRequest> oneOffs,
			List<MentorAvailabilityExceptionRequest> exceptions) {
		for (OfficeHoursOneOffRequest oneOff : oneOffs) {
			if (Duration.between(oneOff.startsAt(), oneOff.endsAt()).compareTo(SHORTEST_MEETING) < 0) {
				throw new InvalidRequestException("One-off hours must last at least 15 minutes");
			}
			LocalDate day = oneOff.startsAt().atZoneSameInstant(MeetingSlotTimes.APP_ZONE).toLocalDate();
			Instant nextMidnight = day.plusDays(1).atStartOfDay(MeetingSlotTimes.APP_ZONE).toInstant();
			if (oneOff.endsAt().toInstant().isAfter(nextMidnight)) {
				throw new InvalidRequestException("One-off hours must end on the day they start");
			}
			boolean away = exceptions.stream()
					.anyMatch(range -> !day.isBefore(range.startsOn()) && !day.isAfter(range.endsOn()));
			if (away) {
				throw new InvalidRequestException("One-off hours cannot fall on a day off");
			}
		}
		for (int first = 0; first < oneOffs.size(); first++) {
			for (int second = first + 1; second < oneOffs.size(); second++) {
				OfficeHoursOneOffRequest left = oneOffs.get(first);
				OfficeHoursOneOffRequest right = oneOffs.get(second);
				if (left.startsAt().isBefore(right.endsAt()) && right.startsAt().isBefore(left.endsAt())) {
					throw new InvalidRequestException("One-off hours cannot overlap");
				}
			}
		}
	}

	private static OfficeHoursOneOff newOneOff(UUID instructorId, OfficeHoursOneOffRequest request) {
		OfficeHoursOneOff oneOff = new OfficeHoursOneOff();
		oneOff.setId(UUID.randomUUID());
		oneOff.setInstructorId(instructorId);
		oneOff.setStartsAt(request.startsAt().toInstant());
		oneOff.setEndsAt(request.endsAt().toInstant());
		oneOff.setPlace(TextUtils.normalizeOptional(request.place()));
		return oneOff;
	}

	private static OfficeHoursRule newRule(UUID instructorId, CreateMentorAvailabilityRuleRequest request,
			Instant now) {
		OfficeHoursRule rule = new OfficeHoursRule();
		rule.setId(UUID.randomUUID());
		rule.setInstructorId(instructorId);
		rule.setDay(request.dayOfWeek());
		rule.setStartTime(request.startTime());
		rule.setEndTime(request.endTime());
		rule.setSlotMinutes(slotMinutes(request));
		rule.setCreatedAt(now);
		return rule;
	}

	private static OfficeHoursException newException(UUID instructorId, MentorAvailabilityExceptionRequest request) {
		OfficeHoursException exception = new OfficeHoursException();
		exception.setId(UUID.randomUUID());
		exception.setInstructorId(instructorId);
		exception.setStartsOn(request.startsOn());
		exception.setEndsOn(request.endsOn());
		exception.setReason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());
		return exception;
	}
}
