package com.dps.teknokent.dto.requests;

import java.util.UUID;

import com.dps.teknokent.domain.OfficeHoursRequestStatus;

import jakarta.validation.constraints.NotNull;

/**
 * A booked meeting or open request the academic was shown before saving, with the status it had then: a request
 * that has since become a meeting (the student took an offered time) counts as something they have not seen.
 */
public record OfficeHoursAffectedItemRequest(@NotNull UUID requestId, @NotNull OfficeHoursRequestStatus status) {
}
