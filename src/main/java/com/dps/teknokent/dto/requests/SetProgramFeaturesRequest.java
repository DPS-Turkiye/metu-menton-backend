package com.dps.teknokent.dto.requests;

import java.util.Set;
import java.util.UUID;

import com.dps.teknokent.domain.ProgramFeature;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The whole set of a semester's features after the change; a feature left out is switched off. */
public record SetProgramFeaturesRequest(UUID actorId, @NotNull @Size(max = 3) Set<@NotNull ProgramFeature> features) {
}
