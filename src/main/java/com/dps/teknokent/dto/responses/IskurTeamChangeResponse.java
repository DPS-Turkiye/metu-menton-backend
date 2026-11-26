package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurTeamChangeStatus;

/**
 * One team change request. {@code fromTeamName} is the team the intern asked to leave, {@code currentTeamName} the one
 * they are in now (they differ once the coordinator moved them).
 */
public record IskurTeamChangeResponse(
		UUID id,
		UUID userId,
		String fullName,
		@Nullable String fromTeamName,
		@Nullable String currentTeamName,
		String reason,
		IskurTeamChangeStatus status,
		@Nullable String decisionNote,
		@Nullable String decidedByName,
		Instant createdAt,
		@Nullable Instant decidedAt) {
}
