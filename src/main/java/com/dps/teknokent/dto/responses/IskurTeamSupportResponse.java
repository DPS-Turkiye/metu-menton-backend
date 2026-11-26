package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurTeamSupportStatus;

/** One support request between two teams; {@code canAnswer}: the caller leads the asked team and it is still open. */
public record IskurTeamSupportResponse(
		UUID id,
		UUID fromTeamId,
		String fromTeamName,
		UUID toTeamId,
		String toTeamName,
		@Nullable UUID taskId,
		@Nullable String taskTitle,
		@Nullable String openedByName,
		String need,
		IskurTeamSupportStatus status,
		@Nullable String answerNote,
		@Nullable String answeredByName,
		Instant createdAt,
		@Nullable Instant answeredAt,
		boolean canAnswer) {
}
