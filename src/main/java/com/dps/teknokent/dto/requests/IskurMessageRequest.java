package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A new message. {@code title} and {@code important} are for coordinators in Genel Duyurular only. */
public record IskurMessageRequest(
		@NotNull UUID actorId,
		@NotBlank @Size(max = 4000) String body,
		@Size(max = 160) String title,
		Boolean important) {
}
