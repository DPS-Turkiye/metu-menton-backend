package com.dps.teknokent.dto.responses;

import java.util.List;

/** Every channel the reader can open, with the unread total for the header's Mesajlar icon. */
public record IskurChannelListResponse(List<IskurChannelResponse> channels, long totalUnread) {
}
