package com.dps.teknokent.dto.responses;

import java.util.UUID;

import com.dps.teknokent.domain.IskurChannelEventType;

/**
 * The live signal for an İŞKUR channel: which channel and which message changed, and nothing of what they hold. A
 * message, a name or a tally differs per reader, so each reader fetches it again through the REST read, which checks
 * their reach on that call. Both ids are ones every reader of the channel already has.
 */
public record IskurChannelEvent(IskurChannelEventType type, UUID channelId, UUID messageId) {
}
