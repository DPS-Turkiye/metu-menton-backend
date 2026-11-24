package com.dps.teknokent.dto.requests;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * "Kapasitem": how many mentees the peer mentor takes at once (left out, the saved capacity stays), and whether they
 * take new requests now.
 */
public record SavePeerMentorIntakeRequest(
		UUID actorId,
		@Nullable @Min(1) @Max(50) Integer menteeCapacity,
		@NotNull Boolean acceptingRequests) {
}
