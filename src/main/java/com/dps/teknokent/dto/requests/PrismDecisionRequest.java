package com.dps.teknokent.dto.requests;

import java.util.List;
import java.util.UUID;

import com.dps.teknokent.domain.PrismDecision;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Approve (the Akran Mentoru badge) or ask to retake the test, for the people on the Prism admin's list or picked on
 * the roster. {@code score} is what the Prism admin reported, free text; {@code actorId} comes from the proxy's
 * session.
 */
public record PrismDecisionRequest(
		UUID actorId,
		@NotNull PrismDecision decision,
		@NotNull @Size(min = 1, max = 500) List<@Valid @NotNull Entry> entries) {

	public record Entry(@NotBlank @Size(max = 320) String email, @Size(max = 32) String score) {
	}
}
