package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Who in the channel confirmed an important announcement, for the coordinators who post there. */
public record IskurMessageReceiptsResponse(UUID messageId, long read, long total, List<Reader> people) {

	public record Reader(UUID userId, String fullName, @Nullable Instant readAt) {
	}
}
