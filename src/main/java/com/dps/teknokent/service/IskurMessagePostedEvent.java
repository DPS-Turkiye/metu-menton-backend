package com.dps.teknokent.service;

import java.util.UUID;

/**
 * Published inside the transaction that saves a channel message; {@link IskurChannelNotifier} notifies after commit,
 * so a failed notification never loses the message.
 */
public record IskurMessagePostedEvent(UUID messageId) {
}
