package com.dps.teknokent.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.OfficeHoursRequestStatus;
import com.dps.teknokent.entity.OfficeHoursMeeting;
import com.dps.teknokent.entity.OfficeHoursRequest;
import com.dps.teknokent.repository.OfficeHoursMeetingRepository;
import com.dps.teknokent.repository.OfficeHoursRequestRepository;
import com.dps.teknokent.service.OfficeHoursSlotService.Hours;
import com.dps.teknokent.service.OfficeHoursSlotService.Setup;
import com.dps.teknokent.util.MeetingSlotTimes;

/**
 * What a change to an academic's hours would leave outside them (FR-OH-29): booked meetings and the times open
 * requests ask for or were offered. Only what this change takes away counts, old hours against new: a time that
 * was already outside (an alternative typed by hand, say) is not raised again on every save. Nothing is cancelled
 * here; the academic decides through the usual cancel and answer steps.
 */
@Service
public class OfficeHoursAvailabilityImpactService {

	/** One affected item as the academic saw it; a status that moved on since means they saw something else. */
	public record Item(UUID requestId, OfficeHoursRequestStatus status) {
	}

	/** An open request with the times this change takes away and the ones it keeps. */
	public record AffectedRequest(OfficeHoursRequest request, List<Instant> lostTimes, List<Instant> keptTimes) {
	}

	public record Impact(List<OfficeHoursMeeting> meetings, List<AffectedRequest> requests) {

		public Set<Item> items() {
			return Stream.concat(
					meetings.stream()
							.map(meeting -> new Item(meeting.getRequestId(), OfficeHoursRequestStatus.ACCEPTED)),
					requests.stream().map(affected -> new Item(affected.request().getId(),
							affected.request().getStatus())))
					.collect(Collectors.toSet());
		}
	}

	private final OfficeHoursSlotService slotService;
	private final OfficeHoursMeetingRepository meetingRepository;
	private final OfficeHoursRequestRepository requestRepository;
	private final Clock clock;

	@Autowired
	public OfficeHoursAvailabilityImpactService(OfficeHoursSlotService slotService,
			OfficeHoursMeetingRepository meetingRepository, OfficeHoursRequestRepository requestRepository) {
		this(slotService, meetingRepository, requestRepository, Clock.systemUTC());
	}

	OfficeHoursAvailabilityImpactService(OfficeHoursSlotService slotService,
			OfficeHoursMeetingRepository meetingRepository, OfficeHoursRequestRepository requestRepository,
			Clock clock) {
		this.slotService = slotService;
		this.meetingRepository = meetingRepository;
		this.requestRepository = requestRepository;
		this.clock = clock;
	}

	/**
	 * What saving {@code proposed} over the academic's saved hours would leave out. Meetings that have started and
	 * times already past are left alone; there is no eight-week or lead-time cut, so a request inside the next few
	 * hours or a meeting far ahead is still seen.
	 */
	@Transactional(readOnly = true)
	public Impact affectedBy(UUID instructorId, Setup proposed) {
		Instant now = clock.instant();
		List<OfficeHoursMeeting> meetings = meetingRepository
				.findByInstructorIdAndCancelledAtIsNullAndScheduledAtAfterOrderByScheduledAtAsc(instructorId, now);
		List<OfficeHoursRequest> open = requestRepository.findByInstructorIdAndStatusIn(instructorId,
				OfficeHoursSlotService.HOLDING);
		if (meetings.isEmpty() && open.isEmpty()) {
			return new Impact(List.of(), List.of());
		}
		Setup saved = slotService.savedSetup(instructorId);

		// Only the days the meetings and times fall on are read, never the range up to the latest one: a time years
		// ahead (typed by hand, or sent from Moodle) costs no more than one tomorrow.
		List<OfficeHoursMeeting> leftOut = meetings.stream()
				.filter(meeting -> covers(hoursAround(saved, meeting), meeting)
						&& !covers(hoursAround(proposed, meeting), meeting))
				.toList();

		Map<LocalDate, Set<Instant>> savedStarts = new HashMap<>();
		Map<LocalDate, Set<Instant>> proposedStarts = new HashMap<>();
		List<AffectedRequest> requests = new ArrayList<>();
		for (OfficeHoursRequest request : open) {
			List<Instant> times = OfficeHoursSlotService.timesHeldBy(request).filter(at -> at.isAfter(now)).sorted()
					.toList();
			List<Instant> lost = times.stream()
					.filter(at -> startsOn(saved, at, savedStarts).contains(at)
							&& !startsOn(proposed, at, proposedStarts).contains(at))
					.toList();
			if (!lost.isEmpty()) {
				requests.add(new AffectedRequest(request, lost,
						times.stream().filter(at -> !lost.contains(at)).toList()));
			}
		}
		requests.sort(Comparator.comparing(affected -> affected.lostTimes().getFirst()));
		return new Impact(leftOut, requests);
	}

	/** The setup's hours, merged, on the day or days a meeting spans. */
	private static List<Hours> hoursAround(Setup setup, OfficeHoursMeeting meeting) {
		LocalDate first = dayOf(meeting.getScheduledAt());
		LocalDate last = dayOf(endOf(meeting));
		return merged(OfficeHoursSlotService.blocks(setup, first, startOf(last.plusDays(1))));
	}

	/**
	 * Slot starts on the day of {@code at}, from the hours alone: no lead time, meetings or buffer, which a change to
	 * hours doesn't move. Each day is worked out once.
	 */
	private static Set<Instant> startsOn(Setup setup, Instant at, Map<LocalDate, Set<Instant>> known) {
		return known.computeIfAbsent(dayOf(at), day -> {
			ZonedDateTime until = startOf(day.plusDays(1));
			Set<Instant> starts = new HashSet<>();
			OfficeHoursSlotService.weeklySlots(setup, day, until).forEach(slot -> starts.add(slot.startsAt()));
			OfficeHoursSlotService.oneOffSlots(setup, until.toInstant()).forEach(slot -> starts.add(slot.startsAt()));
			return starts;
		});
	}

	private static LocalDate dayOf(Instant at) {
		return at.atZone(MeetingSlotTimes.APP_ZONE).toLocalDate();
	}

	private static ZonedDateTime startOf(LocalDate day) {
		return day.atStartOfDay(MeetingSlotTimes.APP_ZONE);
	}

	private static Instant endOf(OfficeHoursMeeting meeting) {
		return meeting.getScheduledAt().plusSeconds(meeting.getDurationMinutes() * 60L);
	}

	/**
	 * Blocks that touch or overlap become one, so a meeting across 10:00–11:00 and 11:00–12:00 counts as inside.
	 */
	static List<Hours> merged(List<Hours> blocks) {
		List<Hours> sorted = blocks.stream().sorted(Comparator.comparing(Hours::startsAt)).toList();
		List<Hours> merged = new ArrayList<>();
		for (Hours block : sorted) {
			if (merged.isEmpty() || block.startsAt().isAfter(merged.getLast().endsAt())) {
				merged.add(block);
				continue;
			}
			Hours last = merged.getLast();
			if (block.endsAt().isAfter(last.endsAt())) {
				merged.set(merged.size() - 1, new Hours(last.startsAt(), block.endsAt()));
			}
		}
		return merged;
	}

	private static boolean covers(List<Hours> merged, OfficeHoursMeeting meeting) {
		Instant start = meeting.getScheduledAt();
		Instant end = endOf(meeting);
		return merged.stream().anyMatch(block -> !start.isBefore(block.startsAt()) && !end.isAfter(block.endsAt()));
	}
}
