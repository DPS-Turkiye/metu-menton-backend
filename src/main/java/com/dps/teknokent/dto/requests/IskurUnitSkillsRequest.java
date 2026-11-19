package com.dps.teknokent.dto.requests;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The skills a unit asks about, in order; the list replaces the unit's current one. */
public record IskurUnitSkillsRequest(UUID actorId,
		@NotNull @Size(max = 30) List<@NotBlank @Size(max = 80) String> names) {
}
