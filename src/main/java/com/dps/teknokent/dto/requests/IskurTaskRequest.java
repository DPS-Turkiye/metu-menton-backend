package com.dps.teknokent.dto.requests;

import java.time.LocalDate;
import java.util.UUID;

import com.dps.teknokent.domain.IskurTaskKind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The leader's "Görev ata" form (2.1). The estimate is in half-hour steps, checked by the service. */
public record IskurTaskRequest(
		@NotNull UUID actorId,
		@NotNull UUID assigneeId,
		@NotBlank @Size(max = 200) String title,
		@NotNull IskurTaskKind kind,
		@NotNull @Min(60) @Max(450) Integer estimatedMinutes,
		@NotNull LocalDate dueDate,
		boolean needsOutput,
		@Size(max = 1000) String description) {
}
