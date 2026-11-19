package com.dps.teknokent.dto.requests;

import java.util.UUID;

import com.dps.teknokent.domain.IskurWeekDecision;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The leader's "Değerlendirme" (4.1): three checks, a decision, and a reason (required unless approving). */
public record IskurWeekDecisionRequest(
		@NotNull UUID actorId,
		@NotNull IskurWeekDecision decision,
		boolean timeMatchesWork,
		boolean effortDocumented,
		boolean outputsSufficient,
		@Size(max = 500) String reason) {
}
