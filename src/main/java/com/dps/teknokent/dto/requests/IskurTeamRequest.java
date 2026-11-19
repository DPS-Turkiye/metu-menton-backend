package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IskurTeamRequest(
		@NotNull UUID actorId,
		@NotBlank @Size(max = 80) String name) {
}
