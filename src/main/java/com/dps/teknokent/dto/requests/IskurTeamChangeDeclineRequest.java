package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A coordinator declining a team change request; the note goes to the intern. */
public record IskurTeamChangeDeclineRequest(
		@NotNull UUID actorId,
		@NotBlank @Size(max = 500) String note) {
}
