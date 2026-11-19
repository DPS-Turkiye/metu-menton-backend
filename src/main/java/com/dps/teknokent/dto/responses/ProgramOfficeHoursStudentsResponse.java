package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * An Office Hours program's Öğrenciler: the students' faculties to filter by and one page of students. A row carries
 * the name, department and class year and how the student's requests stand; never a topic, a description or contact
 * details.
 */
public record ProgramOfficeHoursStudentsResponse(
		List<String> faculties,
		PageResponse<StudentRow> students) {

	public record StudentRow(
			UUID id,
			ProgramOfficeHoursAppointmentResponse.Person student,
			@Nullable Integer classYear,
			StudentRequests requests,
			long academicians,
			Instant lastRequestedAt) {
	}

	/** The student's requests in the window by status; awaiting = PENDING plus MORE_INFO_REQUESTED. */
	public record StudentRequests(long awaitingDecision, long alternativeProposed, long accepted, long declined,
			long withdrawn) {
	}
}
