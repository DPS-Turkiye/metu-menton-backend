package com.dps.teknokent.dto.responses;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The intern's side: whether they may ask now (placed and still in the trial, nothing pending) and their latest
 * request, if any.
 */
public record IskurTeamChangeMineResponse(
		UUID programId,
		boolean canAsk,
		@Nullable IskurTeamChangeResponse latest) {
}
