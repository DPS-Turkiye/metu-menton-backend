package com.dps.teknokent.dto.responses;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.PrismDecisionOutcome;

/** What a bulk Prism decision did for one address; the name only for someone of the program. */
public record PrismDecisionResultResponse(String email, PrismDecisionOutcome outcome, @Nullable String fullName) {
}
