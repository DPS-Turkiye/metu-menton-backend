package com.dps.teknokent.dto.requests;

import java.util.UUID;

import com.dps.teknokent.domain.IskurAssessmentReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An intern's statement to the coordinator: a reason from the list and a short text. */
public record IskurAssessmentStatementRequest(
		@NotNull UUID actorId,
		@NotNull IskurAssessmentReason reason,
		@NotBlank @Size(max = 1000) String text) {
}
