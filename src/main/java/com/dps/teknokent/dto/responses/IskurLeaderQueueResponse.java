package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurWeekStatus;

/** "Haftalık kayıtlar" / "Sıradaki kayıtlar" (4.1): the people whose week the leader reviews, for one week. */
public record IskurLeaderQueueResponse(LocalDate weekStart, List<Row> people) {

	/** {@code weekId} and {@code status} are null for someone who has saved nothing this week. */
	public record Row(UUID userId, String fullName, @Nullable UUID weekId, @Nullable IskurWeekStatus status,
			@Nullable Instant submittedAt, int teamMinutes, int mentoringMinutes) {
	}
}
