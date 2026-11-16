package com.dps.teknokent.dto.requests;

import java.time.LocalDate;

import com.dps.teknokent.domain.OfficeHoursRequestStatus;

/**
 * The Randevular filters. {@code from} and {@code to} are days in Turkey, both given or both left out; every other
 * field may be null.
 */
public record ProgramOfficeHoursAppointmentsQuery(
		OfficeHoursRequestStatus status,
		String faculty,
		String search,
		LocalDate from,
		LocalDate to,
		int page,
		int size) {
}
