package com.dps.teknokent.dto.requests;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** "Mentörlük destek faaliyeti seç": one activity from the pool, on a day of the week, with its minutes. */
public record IskurSupportEntryRequest(
		@NotNull UUID actorId,
		@NotNull UUID activityId,
		@NotNull LocalDate date,
		@NotNull @Min(1) @Max(450) Integer minutes) {
}
