package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An intern's team change request: a short reason for the coordinator. */
public record IskurTeamChangeAskRequest(
		@NotNull UUID actorId,
		@NotBlank @Size(max = 500) String reason) {
}
