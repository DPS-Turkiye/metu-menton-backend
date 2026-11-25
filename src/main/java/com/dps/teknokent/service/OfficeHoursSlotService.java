package com.dps.teknokent.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.OfficeHoursRequestStatus;
import com.dps.teknokent.entity.OfficeHoursMeeting;
import com.dps.teknokent.entity.OfficeHoursRequest;
import com.dps.teknokent.entity.OfficeHoursSettings;
import com.dps.teknokent.repository.OfficeHoursExceptionRepository;
import com.dps.teknokent.repository.OfficeHoursMeetingRepository;
import com.dps.teknokent.repository.OfficeHoursOneOffRepository;
import com.dps.teknokent.repository.OfficeHoursRequestRepository;
import com.dps.teknokent.repository.OfficeHoursRuleRepository;
import com.dps.teknokent.repository.OfficeHoursSettingsRepository;
import com.dps.teknokent.util.MeetingSlotTimes;

/**
 * The times a student can pick from an academic's office hours, made the way a mentor's availability turns into
 * slots: each weekly range split into its slot length over the next eight weeks, leaving out dates away, anything
 * sooner than {@link MeetingSlotTimes#MIN_BOOKING_LEAD}, and any slot that overlaps a meeting already on the
 * calendar or comes closer to it than the academic's buffer.
 */
@Service
public class OfficeHoursSlotService {

	static final int HORIZON_WEEKS = 8;
	/** Meetings never run longer than the 480-minute slot limit, so this is far enough back to catch overlaps. */
	static final Set<OfficeHoursRequestStatus> HOLDING = Set.of(OfficeHoursRequestStatus.PENDING,
			OfficeHoursRequestStatus.MORE_INFO_REQUESTED, OfficeHoursRequestStatus.COUNTER_PROPOSED);
	private static final Duration LOOK_BACK = Duration.ofHours(9);

	public record Slot(Instant startsAt, int durationMinutes) {
	}

	/** One weekly range on one date. */
	public record Hours(Instant startsAt, Instant endsAt) {
	}

	public record WeeklyRange(DayOfWeek day, LocalTime startTime, LocalTime endTime, int slotMinutes) {
	}

	public record DateRange(LocalDate startsOn, LocalDate endsOn) {

		boolean contains(LocalDate day) {
			return !day.isBefore(startsOn) && !day.isAfter(endsOn);
		}
	}

	/**
	 * An academic's hours alone, as saved or as about to be saved: weekly ranges, dates away, one-off hours and the
	 * length one-offs are cut into. No booking rule (lead time, horizon, meetings, buffer) is in it, so two setups
	 * can be compared without those changing the answer.
	 */
	public record Setup(List<WeeklyRange> weekly, List<DateRange> away, List<Hours> oneOffs, int oneOffMinutes) {

		boolean isAway(LocalDate day) {
			return away.stream().anyMatch(range -> range.contains(day));
		}
	}

	private final OfficeHoursRuleRepository ruleRepository;
	private final OfficeHoursExceptionRepository exceptionRepository;
	private final OfficeHoursMeetingRepository meetingRepository;
	private final OfficeHoursSettingsRepository settingsRepository;
	private final OfficeHoursRequestRepository requestRepository;
	private final OfficeHoursOneOffRepository oneOffRepository;
	private final Clock clock;

	@Autowired
	public OfficeHoursSlotService(OfficeHoursRuleRepository ruleRepository,
			OfficeHoursExceptionRepository exceptionRepository, OfficeHoursMeetingRepository meetingRepository,
			OfficeHoursSettingsRepository settingsRepository, OfficeHoursRequestRepository requestRepository,
			OfficeHoursOneOffRepository oneOffRepository) {
		this(ruleRepository, exceptionRepository, meetingRepository, settingsRepository, requestRepository,
				oneOffRepository, Clock.systemUTC());
	}

	OfficeHoursSlotService(OfficeHoursRuleRepository ruleRepository, OfficeHoursExceptionRepository exceptionRepository,
			OfficeHoursMeetingRepository meetingRepository, OfficeHoursSettingsRepository settingsRepository,
			OfficeHoursRequestRepository requestRepository, OfficeHoursOneOffRepository oneOffRepository,
			Clock clock) {
		this.ruleRepository = ruleRepository;
		this.exceptionRepository = exceptionRepository;
		this.meetingRepository = meetingRepository;
		this.settingsRepository = settingsRepository;
		this.requestRepository = requestRepository;
		this.oneOffRepository = oneOffRepository;
		this.clock = clock;
	}

	/** The slots a student can still ask for: the academic's hours, minus booked meetings and held times. */
	@Transactional(readOnly = true)
	public List<Slot> openSlots(UUID instructorId) {
		Set<Instant> held = heldTimes(instructorId, null);
		return calendarSlots(instructorId).stream().filter(slot -> !held.contains(slot.startsAt())).toList();
	}

	/**
	 * Times open requests hold, so no one else is offered them (FR-OH-16): what a request waiting on the academic
	 * asks for, and what the academic offered on one waiting on the student. {@code exceptRequestId}'s own times
	 * don't count.
	 */
	@Transactional(readOnly = true)
	public Set<Instant> heldTimes(UUID instructorId, @Nullable UUID exceptRequestId) {
		return requestRepository.findByInstructorIdAndStatusIn(instructorId, HOLDING).stream()
				.filter(request -> !request.getId().equals(exceptRequestId))
				.flatMap(OfficeHoursSlotService::timesHeldBy)
				.collect(Collectors.toSet());
	}

	static Stream<Instant> timesHeldBy(OfficeHoursRequest request) {
		String joined = request.getStatus() == OfficeHoursRequestStatus.COUNTER_PROPOSED
				? request.getProposedSlots()
				: request.getPreferredSlots();
		if (joined == null || joined.isBlank()) {
			return Stream.empty();
		}
		return Arrays.stream(joined.split(",")).map(String::trim).map(MeetingSlotTimes::parseMachineSlot)
				.filter(Objects::nonNull);
	}

	/** The academic's hours as slots, minus booked meetings; times other requests hold are still in. */
	public List<Slot> calendarSlots(UUID instructorId) {
		Instant now = clock.instant();
		Instant earliest = now.plus(MeetingSlotTimes.MIN_BOOKING_LEAD);
		ZonedDateTime start = now.atZone(MeetingSlotTimes.APP_ZONE);
		ZonedDateTime horizon = start.plusWeeks(HORIZON_WEEKS);
		List<OfficeHoursMeeting> meetings = upcomingMeetings(instructorId, now);
		Duration buffer = buffer(instructorId);
		Setup setup = savedSetup(instructorId);

		List<Slot> slots = new ArrayList<>(weeklySlots(setup, start.toLocalDate(), horizon).stream()
				.filter(slot -> bookable(slot, earliest, meetings, buffer)).toList());
		// A one-off laid over a weekly range would offer the same start twice, so a start already offered is skipped.
		Set<Instant> offered = slots.stream().map(Slot::startsAt).collect(Collectors.toCollection(HashSet::new));
		for (Slot slot : oneOffSlots(setup, horizon.toInstant())) {
			if (bookable(slot, earliest, meetings, buffer) && offered.add(slot.startsAt())) {
				slots.add(slot);
			}
		}
		slots.sort(Comparator.comparing(Slot::startsAt));
		return slots;
	}

	/**
	 * The academic's weekly ranges and one-off hours as dated blocks, from the start of today over the same eight
	 * weeks students can book in, leaving out dates away. Meetings already booked do not cut them: these are the
	 * hours they keep.
	 */
	@Transactional(readOnly = true)
	public List<Hours> hoursAhead(UUID instructorId) {
		ZonedDateTime start = clock.instant().atZone(MeetingSlotTimes.APP_ZONE);
		return blocks(savedSetup(instructorId), start.toLocalDate(), start.plusWeeks(HORIZON_WEEKS));
	}

	/** The academic's hours as saved now. */
	@Transactional(readOnly = true)
	public Setup savedSetup(UUID instructorId) {
		return new Setup(
				ruleRepository.findByInstructorIdOrderByDayOfWeekAscStartTimeAsc(instructorId).stream()
						.map(rule -> new WeeklyRange(rule.day(), rule.getStartTime(), rule.getEndTime(),
								rule.getSlotMinutes()))
						.toList(),
				exceptionRepository.findByInstructorIdOrderByStartsOnAsc(instructorId).stream()
						.map(range -> new DateRange(range.getStartsOn(), range.getEndsOn())).toList(),
				oneOffRepository.findByInstructorIdOrderByStartsAtAsc(instructorId).stream()
						.map(oneOff -> new Hours(oneOff.getStartsAt(), oneOff.getEndsAt())).toList(),
				settings(instructorId).getDefaultMeetingDurationMinutes());
	}

	/** Each weekly range cut into its slot length, on every date from {@code firstDay} before {@code until}. */
	static List<Slot> weeklySlots(Setup setup, LocalDate firstDay, ZonedDateTime until) {
		List<Slot> slots = new ArrayList<>();
		for (WeeklyRange range : setup.weekly()) {
			for (ZonedDateTime day : dates(range, firstDay, until)) {
				if (setup.isAway(day.toLocalDate())) {
					continue;
				}
				ZonedDateTime end = day.with(range.endTime());
				for (ZonedDateTime at = day.with(range.startTime()); !at.plusMinutes(range.slotMinutes())
						.isAfter(end); at = at.plusMinutes(range.slotMinutes())) {
					slots.add(new Slot(at.toInstant(), range.slotMinutes()));
				}
			}
		}
		return slots;
	}

	/** One-off hours starting before {@code until}, cut into meetings of the default length; a day off hides them. */
	static List<Slot> oneOffSlots(Setup setup, Instant until) {
		Duration length = Duration.ofMinutes(setup.oneOffMinutes());
		List<Slot> slots = new ArrayList<>();
		for (Hours oneOff : setup.oneOffs()) {
			if (!oneOff.startsAt().isBefore(until)
					|| setup.isAway(oneOff.startsAt().atZone(MeetingSlotTimes.APP_ZONE).toLocalDate())) {
				continue;
			}
			for (Instant at = oneOff.startsAt(); !at.plus(length).isAfter(oneOff.endsAt()); at = at.plus(length)) {
				slots.add(new Slot(at, setup.oneOffMinutes()));
			}
		}
		return slots;
	}

	/** The setup's hours as dated blocks from the start of {@code firstDay} until {@code until}, without dates away. */
	static List<Hours> blocks(Setup setup, LocalDate firstDay, ZonedDateTime until) {
		List<Hours> hours = new ArrayList<>();
		for (WeeklyRange range : setup.weekly()) {
			for (ZonedDateTime day : dates(range, firstDay, until)) {
				if (!setup.isAway(day.toLocalDate())) {
					hours.add(
							new Hours(day.with(range.startTime()).toInstant(), day.with(range.endTime()).toInstant()));
				}
			}
		}
		Instant from = firstDay.atStartOfDay(MeetingSlotTimes.APP_ZONE).toInstant();
		for (Hours oneOff : setup.oneOffs()) {
			if (oneOff.endsAt().isAfter(from) && oneOff.startsAt().isBefore(until.toInstant())
					&& !setup.isAway(oneOff.startsAt().atZone(MeetingSlotTimes.APP_ZONE).toLocalDate())) {
				hours.add(oneOff);
			}
		}
		hours.sort(Comparator.comparing(Hours::startsAt));
		return hours;
	}

	private static List<ZonedDateTime> dates(WeeklyRange range, LocalDate firstDay, ZonedDateTime until) {
		List<ZonedDateTime> dates = new ArrayList<>();
		for (ZonedDateTime day = firstDay.atStartOfDay(MeetingSlotTimes.APP_ZONE)
				.with(TemporalAdjusters.nextOrSame(range.day())); day.isBefore(until); day = day.plusWeeks(1)) {
			dates.add(day);
		}
		return dates;
	}

	/** The booking rules on top of the hours: far enough ahead, and clear of meetings and their buffer. */
	private static boolean bookable(Slot slot, Instant earliest, List<OfficeHoursMeeting> meetings, Duration buffer) {
		return !slot.startsAt().isBefore(earliest)
				&& !overlaps(meetings, slot.startsAt(), Duration.ofMinutes(slot.durationMinutes()), buffer);
	}

	/**
	 * The academic's slot starting at this moment, if there is one; a time typed by hand has none. Held times still
	 * count here: a request's own time has to keep its slot's length when it is accepted.
	 */
	@Transactional(readOnly = true)
	public Optional<Slot> openSlotAt(UUID instructorId, Instant startsAt) {
		return calendarSlots(instructorId).stream().filter(slot -> slot.startsAt().equals(startsAt)).findFirst();
	}

	/**
	 * True when a meeting from {@code startsAt} lasting {@code minutes} would overlap one already on the academic's
	 * calendar, or come closer to it than their buffer.
	 */
	@Transactional(readOnly = true)
	public boolean clashesWithCalendar(UUID instructorId, Instant startsAt, int minutes) {
		return overlaps(upcomingMeetings(instructorId, clock.instant()), startsAt, Duration.ofMinutes(minutes),
				buffer(instructorId));
	}

	/** The academic's default meeting length, used for a time typed by hand rather than picked from a slot. */
	@Transactional(readOnly = true)
	public int defaultMeetingMinutes(UUID instructorId) {
		return settings(instructorId).getDefaultMeetingDurationMinutes();
	}

	static boolean overlaps(List<OfficeHoursMeeting> meetings, Instant startsAt, Duration length, Duration buffer) {
		Instant end = startsAt.plus(length);
		return meetings.stream().anyMatch(meeting -> {
			Instant blockedFrom = meeting.getScheduledAt().minus(buffer);
			Instant blockedUntil = meeting.getScheduledAt().plus(Duration.ofMinutes(meeting.getDurationMinutes()))
					.plus(buffer);
			return startsAt.isBefore(blockedUntil) && blockedFrom.isBefore(end);
		});
	}

	private List<OfficeHoursMeeting> upcomingMeetings(UUID instructorId, Instant now) {
		return meetingRepository.findByInstructorIdAndCancelledAtIsNullAndScheduledAtAfter(instructorId,
				now.minus(LOOK_BACK));
	}

	private Duration buffer(UUID instructorId) {
		return Duration.ofMinutes(settings(instructorId).getMeetingBufferMinutes());
	}

	private OfficeHoursSettings settings(UUID instructorId) {
		return settingsRepository.findById(instructorId).orElseGet(OfficeHoursSettings::new);
	}
}
