package com.dps.teknokent.dto.requests;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurMentoringDecision;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The coordinator's answer to a statement. {@code mentoringMinutes} is read only with {@code SET}; {@code openRetake}
 * asks the intern to take the Prism test again; {@code note} goes to the intern.
 */
public record IskurAssessmentDecisionRequest(
		@NotNull UUID actorId,
		@NotNull IskurMentoringDecision mentoring,
		@Min(0) @Max(1350) @Nullable Integer mentoringMinutes,
		boolean openRetake,
		@Size(max = 500) @Nullable String note) {
}
