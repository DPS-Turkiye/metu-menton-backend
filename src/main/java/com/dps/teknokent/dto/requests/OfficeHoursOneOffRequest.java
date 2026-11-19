package com.dps.teknokent.dto.requests;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One date's office hours, as ISO instants with an offset ("2026-11-24T14:00:00+03:00"). */
public record OfficeHoursOneOffRequest(
		@NotNull OffsetDateTime startsAt,
		@NotNull OffsetDateTime endsAt,
		@Size(max = 200) String place) {
}
