package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The asked team's leader accepting or declining; the note is optional and goes to the asking leader. */
public record IskurTeamSupportAnswerRequest(
		@NotNull UUID actorId,
		@Size(max = 500) String note) {
}
