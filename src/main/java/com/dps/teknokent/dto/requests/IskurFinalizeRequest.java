package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Which provisional placements become final: one team's, one unit's, or (neither given) the whole program's. */
public record IskurFinalizeRequest(
		@NotNull UUID actorId,
		UUID teamId,
		UUID unitId) {
}
