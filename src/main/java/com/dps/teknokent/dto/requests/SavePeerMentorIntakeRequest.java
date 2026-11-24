package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** "Kapasitem": how many mentees the peer mentor takes at once, and whether they take new requests now. */
public record SavePeerMentorIntakeRequest(
		UUID actorId,
		@NotNull @Min(1) @Max(50) Integer menteeCapacity,
		@NotNull Boolean acceptingRequests) {
}
