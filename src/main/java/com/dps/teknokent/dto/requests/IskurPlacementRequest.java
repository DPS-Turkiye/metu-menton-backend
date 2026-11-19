package com.dps.teknokent.dto.requests;

import java.util.UUID;

import com.dps.teknokent.domain.IskurTeamRole;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Where an intern goes. {@code leaderId} names a leader of the same team and only a member has one;
 * {@code mentoringMinutes} overrides the default share and needs {@code weeklyDays}.
 */
public record IskurPlacementRequest(
		@NotNull UUID actorId,
		@NotNull UUID teamId,
		@NotNull IskurTeamRole role,
		UUID leaderId,
		@Min(1) @Max(3) Integer weeklyDays,
		@Min(0) @Max(1350) Integer mentoringMinutes) {
}
