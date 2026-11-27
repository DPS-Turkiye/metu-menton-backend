package com.dps.teknokent.dto.responses;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The intern's side: whether they may send a statement now (placed, no badge, nothing waiting) and their latest one.
 */
public record IskurAssessmentMineResponse(
		UUID programId,
		boolean canSend,
		@Nullable IskurAssessmentStatementResponse latest) {
}
