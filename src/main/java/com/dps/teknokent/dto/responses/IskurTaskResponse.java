package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurTaskKind;
import com.dps.teknokent.domain.IskurTaskStatus;

/**
 * One team task as its assignee and their team's leaders see it. {@code canWork}: the caller is the assignee and may
 * record time and output; {@code canManage}: the caller leads the task's team and may reopen or delete it.
 */
public record IskurTaskResponse(
		UUID id,
		UUID teamId,
		String teamName,
		Person assignee,
		@Nullable Person assignedBy,
		String title,
		IskurTaskKind kind,
		int estimatedMinutes,
		LocalDate dueDate,
		boolean needsOutput,
		@Nullable String description,
		IskurTaskStatus status,
		@Nullable Integer actualMinutes,
		@Nullable String outputLink,
		@Nullable String outputNote,
		@Nullable String handoverReason,
		@Nullable Instant completedAt,
		Instant createdAt,
		boolean canWork,
		boolean canManage) {

	public record Person(UUID userId, String fullName) {
	}
}
