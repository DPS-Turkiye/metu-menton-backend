package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.OfficeHoursHistoryStep;
import com.dps.teknokent.domain.OfficeHoursRequestStatus;

/**
 * One office-hours appointment as a program coordinator sees it: the title, the time, who takes part and how it got
 * here. The student's description, the messages, the course and the meeting place stay between the two of them, so
 * they are not fields here.
 */
public record ProgramOfficeHoursAppointmentResponse(
		UUID id,
		OfficeHoursRequestStatus status,
		String topic,
		@Nullable OffsetDateTime startsAt,
		@Nullable OffsetDateTime endsAt,
		Person student,
		Person academic,
		List<HistoryEntry> history) {

	/** A participant: the name, an academic title for the academician, and the department from the records. */
	public record Person(String name, @Nullable String title, @Nullable String department) {
	}

	/** {@code at} is null when the step happened but its time was not kept (an alternative later accepted). */
	public record HistoryEntry(OfficeHoursHistoryStep step, @Nullable Instant at) {
	}
}
