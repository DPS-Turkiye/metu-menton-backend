package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A channel message.
 *
 * @param sender         null when the sender's account is gone
 * @param acknowledgedAt when the reader confirmed an important announcement ("Okudum"), if they did
 * @param receipts       how many confirmed it, only for the coordinators who post in the channel
 */
public record IskurMessageResponse(
		UUID id,
		UUID channelId,
		@Nullable Sender sender,
		@Nullable String title,
		String body,
		boolean important,
		Instant createdAt,
		boolean mine,
		@Nullable Instant acknowledgedAt,
		@Nullable Receipts receipts) {

	/**
	 * Who sent it.
	 *
	 * @param coordinator true for a coordinator of the program or an admin (6.1 "Koordinatör" tag)
	 */
	public record Sender(UUID userId, String fullName, boolean coordinator) {
	}

	public record Receipts(long read, long total) {
	}
}
