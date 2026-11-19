package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A unit's name and its unit coordinator; a null coordinator leaves the unit without one. */
public record IskurUnitRequest(
		@NotNull UUID actorId,
		@NotBlank @Size(max = 80) String name,
		UUID coordinatorId) {
}
