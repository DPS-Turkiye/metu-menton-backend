package com.dps.teknokent.service;

import java.util.UUID;

import com.dps.teknokent.domain.IskurChannelEventType;

/**
 * Published inside the transaction that changes a channel; {@link IskurChannelRealtimePublisher} pushes it after
 * commit, so nobody is told about a change that was rolled back.
 */
public record IskurChannelChangedEvent(IskurChannelEventType type, UUID channelId, UUID messageId) {
}
