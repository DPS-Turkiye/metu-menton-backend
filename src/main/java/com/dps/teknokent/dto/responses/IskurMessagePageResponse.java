package com.dps.teknokent.dto.responses;

import java.util.List;

/** One page of a channel, oldest first; {@code hasMore} when older messages are left. */
public record IskurMessagePageResponse(IskurChannelResponse channel, List<IskurMessageResponse> messages,
		boolean hasMore) {
}
