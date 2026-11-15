package com.dps.teknokent.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.OfficeHoursHistoryStep;
import com.dps.teknokent.domain.OfficeHoursMessageType;
import com.dps.teknokent.domain.OfficeHoursRequestStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.dto.requests.ProgramOfficeHoursAppointmentsQuery;
import com.dps.teknokent.dto.responses.OfficeHoursTimeResponse;
import com.dps.teknokent.dto.responses.PageResponse;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentResponse;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentResponse.HistoryEntry;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentResponse.Person;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentsResponse;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentsResponse.Counts;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentsResponse.Row;
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
import com.dps.teknokent.util.MeetingSlotTimes;

/**
 * An Office Hours program's Randevular for its coordinators and the admin. Office hours belong to the whole
 * university, not to a program, so the program only decides who may look: every request is in the list. What the
 * student wrote beyond the title, and the messages, are never read out.
 */
@Service
@Transactional(readOnly = true)
public class ProgramOfficeHoursService {

	static final int MAX_PAGE_SIZE = 100;
	static final int DEFAULT_PAGE_SIZE = 25;
	static final int MAX_RANGE_DAYS = 62;
	/** Without a date range the list covers the requests made in the last half year. */
	static final Duration UNBOUNDED_LOOK_BACK = Duration.ofDays(26 * 7L);
	/**
	 * A time can be asked for at most {@link OfficeHoursSlotService#HORIZON_WEEKS} ahead; one more week covers an
	 * alternative proposed a while after the request was made.
	 */
	static final Duration RANGE_LOOK_BACK = Duration.ofDays((OfficeHoursSlotService.HORIZON_WEEKS + 1) * 7L);

	private static final Set<OfficeHoursRequestStatus> AWAITING_DECISION = Set.of(OfficeHoursRequestStatus.PENDING,
			OfficeHoursRequestStatus.MORE_INFO_REQUESTED);
	private static final Locale TURKISH = Locale.forLanguageTag("tr");

	private final CoordinatorProgramService coordinatorProgramService;
	private final OfficeHoursRequestRepository requests;
	private final OfficeHoursMeetingRepository meetings;
	private final OfficeHoursRequestMessageRepository messages;
	private final OfficeHoursUserRepository officeHoursUsers;
	private final InstitutionPersonRepository institutionPeople;
	private final OfficeHoursBookingService bookingService;
	private final Clock clock;

	@Autowired
	public ProgramOfficeHoursService(CoordinatorProgramService coordinatorProgramService,
			OfficeHoursRequestRepository requests, OfficeHoursMeetingRepository meetings,
			OfficeHoursRequestMessageRepository messages, OfficeHoursUserRepository officeHoursUsers,
			InstitutionPersonRepository institutionPeople, OfficeHoursBookingService bookingService) {
		this(coordinatorProgramService, requests, meetings, messages, officeHoursUsers, institutionPeople,
				bookingService, Clock.systemUTC());
	}

	ProgramOfficeHoursService(CoordinatorProgramService coordinatorProgramService,
			OfficeHoursRequestRepository requests, OfficeHoursMeetingRepository meetings,
			OfficeHoursRequestMessageRepository messages, OfficeHoursUserRepository officeHoursUsers,
			InstitutionPersonRepository institutionPeople, OfficeHoursBookingService bookingService, Clock clock) {
		this.coordinatorProgramService = coordinatorProgramService;
		this.requests = requests;
		this.meetings = meetings;
		this.messages = messages;
		this.officeHoursUsers = officeHoursUsers;
		this.institutionPeople = institutionPeople;
		this.bookingService = bookingService;
		this.clock = clock;
	}

	public ProgramOfficeHoursAppointmentsResponse list(UUID actorId, UUID programId,
			ProgramOfficeHoursAppointmentsQuery query) {
		requireOfficeHoursProgram(actorId, programId);
		int size = query.size() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(query.size(), MAX_PAGE_SIZE);
		int page = Math.max(query.page(), 0);
		@Nullable
		DayRange range = rangeOf(query.from(), query.to());

		Instant now = clock.instant();
		List<OfficeHoursRequest> candidates = candidates(query.status(), range, now);
		Map<UUID, OfficeHoursMeeting> meetingByRequest = meetingsOf(candidates);
		People people = peopleOf(candidates);
		@Nullable
		String search = normalized(query.search());
		@Nullable
		String faculty = query.faculty() == null || query.faculty().isBlank() ? null : query.faculty().trim();

		List<Row> rows = candidates.stream()
				.filter(request -> faculty == null || faculty.equals(people.facultyOf(request.getInstructorId())))
				.map(request -> row(request, meetingByRequest.get(request.getId()), people))
				.filter(row -> range == null || range.contains(row.startsAt()))
				.filter(row -> search == null || matches(row, search))
				.sorted(order(range != null))
				.toList();
		List<Row> pageRows = rows.stream().skip((long) page * size).limit(size).toList();

		return new ProgramOfficeHoursAppointmentsResponse(counts(now), institutionPeople.findAcademicFaculties(),
				PageResponse.of(pageRows, page, size, rows.size()));
	}

	public ProgramOfficeHoursAppointmentResponse detail(UUID actorId, UUID programId, UUID requestId) {
		requireOfficeHoursProgram(actorId, programId);
		OfficeHoursRequest request = requests.findById(requestId)
				.orElseThrow(() -> new ResourceNotFoundException("Office hours request not found: " + requestId));
		@Nullable
		OfficeHoursMeeting meeting = meetings.findByRequestId(requestId).orElse(null);
		People people = peopleOf(List.of(request));
		@Nullable
		OfficeHoursTimeResponse time = timeOf(request, meeting);
		return new ProgramOfficeHoursAppointmentResponse(
				request.getId(),
				request.getStatus(),
				request.getTopic(),
				time == null ? null : time.startsAt(),
				time == null ? null : time.endsAt(),
				people.student(request),
				people.academic(request),
				history(request, meeting, messages.findByRequestIdInOrderByCreatedAtAsc(List.of(requestId))));
	}

	private void requireOfficeHoursProgram(UUID actorId, UUID programId) {
		Program program = coordinatorProgramService.requireManagedProgram(actorId, programId);
		if (program.getProgramType() != ProgramType.OFFICE_HOURS) {
			throw new InvalidRequestException("Appointments are listed for Office Hours programs only.");
		}
	}

	private List<OfficeHoursRequest> candidates(@Nullable OfficeHoursRequestStatus status, @Nullable DayRange range,
			Instant now) {
		Instant from = range == null ? now.minus(UNBOUNDED_LOOK_BACK) : range.start().minus(RANGE_LOOK_BACK);
		Instant to = range == null ? now.plus(Duration.ofDays(1)) : range.end();
		return status == null
				? requests.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(from, to)
				: requests.findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(status,
						from, to);
	}

	private Counts counts(Instant now) {
		LocalDate today = now.atZone(MeetingSlotTimes.APP_ZONE).toLocalDate();
		LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		Instant weekStart = startOf(monday);
		Instant weekEnd = startOf(monday.plusWeeks(1));
		return new Counts(
				requests.countByStatusIn(AWAITING_DECISION),
				requests.countByStatusIn(Set.of(OfficeHoursRequestStatus.COUNTER_PROPOSED)),
				meetings.countByScheduledAtGreaterThanEqualAndScheduledAtLessThan(weekStart, weekEnd),
				requests.countByStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
						OfficeHoursRequestStatus.DECLINED, weekStart, weekEnd));
	}

	private static @Nullable DayRange rangeOf(@Nullable LocalDate from, @Nullable LocalDate to) {
		if (from == null && to == null) {
			return null;
		}
		if (from == null || to == null) {
			throw new InvalidRequestException("Give both ends of the date range, or neither.");
		}
		if (to.isBefore(from)) {
			throw new InvalidRequestException("The date range cannot end before it starts.");
		}
		if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
			throw new InvalidRequestException("The date range can cover at most " + MAX_RANGE_DAYS + " days.");
		}
		return new DayRange(startOf(from), startOf(to.plusDays(1)));
	}

	private static Instant startOf(LocalDate day) {
		return day.atStartOfDay(MeetingSlotTimes.APP_ZONE).toInstant();
	}

	/** Chronological inside a date range (what comes next), newest first otherwise; a request without a time last. */
	private static Comparator<Row> order(boolean ranged) {
		Comparator<OffsetDateTime> byTime = ranged ? Comparator.naturalOrder() : Comparator.reverseOrder();
		return Comparator.comparing(Row::startsAt, Comparator.nullsLast(byTime));
	}

	private static boolean matches(Row row, String search) {
		return row.student().name().toLowerCase(TURKISH).contains(search)
				|| row.academic().name().toLowerCase(TURKISH).contains(search);
	}

	private static @Nullable String normalized(@Nullable String search) {
		if (search == null || search.isBlank()) {
			return null;
		}
		return search.trim().toLowerCase(TURKISH);
	}

	private static Row row(OfficeHoursRequest request, @Nullable OfficeHoursMeeting meeting, People people) {
		@Nullable
		Instant start = startOf(request, meeting);
		return new Row(request.getId(), request.getStatus(), request.getTopic(),
				start == null ? null : OfficeHoursTimeResponse.of(start, 0).startsAt(),
				people.student(request), people.academic(request));
	}

	/** The booked meeting's time; else the alternative the academician proposed; else the student's first choice. */
	static @Nullable Instant startOf(OfficeHoursRequest request, @Nullable OfficeHoursMeeting meeting) {
		if (meeting != null) {
			return meeting.getScheduledAt();
		}
		if (request.getStatus() == OfficeHoursRequestStatus.COUNTER_PROPOSED) {
			@Nullable
			Instant proposed = firstSlot(request.getProposedSlots());
			if (proposed != null) {
				return proposed;
			}
		}
		return firstSlot(request.getPreferredSlots());
	}

	/** A booked meeting keeps its own length; a time not booked yet is as long as the meeting it would book. */
	private @Nullable OfficeHoursTimeResponse timeOf(OfficeHoursRequest request, @Nullable OfficeHoursMeeting meeting) {
		if (meeting != null) {
			return OfficeHoursTimeResponse.of(meeting.getScheduledAt(), meeting.getDurationMinutes());
		}
		@Nullable
		Instant start = startOf(request, null);
		if (start == null) {
			return null;
		}
		return OfficeHoursTimeResponse.of(start,
				bookingService.meetingLengths(request.getInstructorId()).apply(start));
	}

	private static @Nullable Instant firstSlot(@Nullable String joined) {
		if (joined == null || joined.isBlank()) {
			return null;
		}
		return Arrays.stream(joined.split(","))
				.map(String::trim)
				.map(MeetingSlotTimes::parseMachineSlot)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	/**
	 * Made, then the questions and answers in order, an alternative if one was proposed, and how it ended. The time of
	 * a proposal is kept only while it is the latest change.
	 */
	static List<HistoryEntry> history(OfficeHoursRequest request, @Nullable OfficeHoursMeeting meeting,
			List<OfficeHoursRequestMessage> exchange) {
		List<HistoryEntry> steps = new ArrayList<>();
		steps.add(new HistoryEntry(OfficeHoursHistoryStep.SUBMITTED, request.getCreatedAt()));
		for (OfficeHoursRequestMessage message : exchange) {
			steps.add(new HistoryEntry(message.getType() == OfficeHoursMessageType.INSTRUCTOR_QUESTION
					? OfficeHoursHistoryStep.INFO_REQUESTED
					: OfficeHoursHistoryStep.STUDENT_REPLIED, message.getCreatedAt()));
		}
		OfficeHoursRequestStatus status = request.getStatus();
		@Nullable
		String proposedSlots = request.getProposedSlots();
		if (status == OfficeHoursRequestStatus.COUNTER_PROPOSED) {
			steps.add(new HistoryEntry(OfficeHoursHistoryStep.ALTERNATIVE_PROPOSED, request.getUpdatedAt()));
		} else if (proposedSlots != null && !proposedSlots.isBlank()) {
			steps.add(new HistoryEntry(OfficeHoursHistoryStep.ALTERNATIVE_PROPOSED, null));
		}
		if (meeting != null) {
			steps.add(new HistoryEntry(OfficeHoursHistoryStep.ACCEPTED, meeting.getCreatedAt()));
		} else if (status == OfficeHoursRequestStatus.DECLINED) {
			steps.add(new HistoryEntry(OfficeHoursHistoryStep.DECLINED, request.getUpdatedAt()));
		} else if (status == OfficeHoursRequestStatus.WITHDRAWN) {
			steps.add(new HistoryEntry(OfficeHoursHistoryStep.WITHDRAWN, request.getUpdatedAt()));
		}
		return steps;
	}

	private Map<UUID, OfficeHoursMeeting> meetingsOf(Collection<OfficeHoursRequest> candidates) {
		Set<UUID> accepted = candidates.stream()
				.filter(request -> request.getStatus() == OfficeHoursRequestStatus.ACCEPTED)
				.map(OfficeHoursRequest::getId)
				.collect(Collectors.toSet());
		if (accepted.isEmpty()) {
			return Map.of();
		}
		return meetings.findByRequestIdIn(accepted).stream()
				.collect(Collectors.toMap(OfficeHoursMeeting::getRequestId, Function.identity(), (a, b) -> a));
	}

	private People peopleOf(Collection<OfficeHoursRequest> candidates) {
		Set<UUID> ids = new HashSet<>();
		for (OfficeHoursRequest request : candidates) {
			ids.add(request.getStudentId());
			ids.add(request.getInstructorId());
		}
		Map<UUID, OfficeHoursUser> users = ids.isEmpty() ? Map.of()
				: officeHoursUsers.findAllById(ids).stream()
						.collect(Collectors.toMap(OfficeHoursUser::getId, Function.identity()));
		Set<UUID> accountIds = users.values().stream()
				.map(OfficeHoursUser::getAppUserId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<UUID, InstitutionPerson> records = new HashMap<>();
		if (!accountIds.isEmpty()) {
			for (InstitutionPerson person : institutionPeople.findByUserIdIn(accountIds)) {
				if (person.getUser() != null) {
					records.merge(person.getUser().getId(), person, People::newer);
				}
			}
		}
		return new People(users, records);
	}

	/** The people behind a set of requests, looked up once. */
	private record People(Map<UUID, OfficeHoursUser> users, Map<UUID, InstitutionPerson> records) {

		Person student(OfficeHoursRequest request) {
			return person(request.getStudentId(), false);
		}

		Person academic(OfficeHoursRequest request) {
			return person(request.getInstructorId(), true);
		}

		@Nullable
		String facultyOf(UUID officeHoursUserId) {
			@Nullable
			InstitutionPerson record = recordOf(officeHoursUserId);
			return record == null ? null : record.getFaculty();
		}

		private Person person(UUID officeHoursUserId, boolean academic) {
			@Nullable
			OfficeHoursUser user = users.get(officeHoursUserId);
			@Nullable
			InstitutionPerson record = recordOf(officeHoursUserId);
			String name = user != null ? user.getFullName() : record != null ? record.getFullName() : "";
			return new Person(name, academic && record != null ? record.getAcademicTitle() : null,
					record == null ? null : record.getDepartmentName());
		}

		private @Nullable InstitutionPerson recordOf(UUID officeHoursUserId) {
			@Nullable
			OfficeHoursUser user = users.get(officeHoursUserId);
			@Nullable
			UUID accountId = user == null ? null : user.getAppUserId();
			return accountId == null ? null : records.get(accountId);
		}

		private static InstitutionPerson newer(InstitutionPerson a, InstitutionPerson b) {
			if (a.getSyncedAt() == null) {
				return b;
			}
			return b.getSyncedAt() != null && b.getSyncedAt().isAfter(a.getSyncedAt()) ? b : a;
		}
	}

	private record DayRange(Instant start, Instant end) {

		boolean contains(@Nullable OffsetDateTime moment) {
			if (moment == null) {
				return false;
			}
			Instant instant = moment.toInstant();
			return !instant.isBefore(start) && instant.isBefore(end);
		}
	}
}
