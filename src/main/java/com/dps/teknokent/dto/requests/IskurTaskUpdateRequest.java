package com.dps.teknokent.dto.requests;

import java.util.UUID;

import com.dps.teknokent.domain.IskurTaskStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * "Görev detayı": the assignee records the actual time and the output, and may complete the task or hand it over to
 * next week; a leader may reopen it. A null {@code status} saves the fields and keeps the status.
 */
public record IskurTaskUpdateRequest(
		@NotNull UUID actorId,
		IskurTaskStatus status,
		@Min(1) @Max(1350) Integer actualMinutes,
		@Size(max = 500) String outputLink,
		@Size(max = 500) String outputNote,
		@Size(max = 500) String handoverReason) {
}
