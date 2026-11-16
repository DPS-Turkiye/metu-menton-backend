package com.dps.teknokent.dto.responses;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.OfficeHoursRequestStatus;

/**
 * An Office Hours program's Randevular: the status cards, the faculties to filter by and one page of rows. A row
 * carries only what the table shows.
 */
public record ProgramOfficeHoursAppointmentsResponse(
		Counts counts,
		List<String> faculties,
		PageResponse<Row> appointments) {

	/**
	 * The cards. Open requests are counted whatever their date; accepted ones by the meeting's day this week, declined
	 * ones by the day they were declined this week.
	 */
	public record Counts(long awaitingDecision, long alternativeProposed, long acceptedThisWeek,
			long declinedThisWeek) {
	}

	public record Row(
			UUID id,
			OfficeHoursRequestStatus status,
			String topic,
			@Nullable OffsetDateTime startsAt,
			ProgramOfficeHoursAppointmentResponse.Person student,
			ProgramOfficeHoursAppointmentResponse.Person academic) {
	}
}
