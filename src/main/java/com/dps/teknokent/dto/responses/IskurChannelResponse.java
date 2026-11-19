package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurChannelKind;

/**
 * One channel in the Mesajlar list, as its reader sees it. {@code coordinatorView} is true when the reader reaches it
 * through their program role rather than as a member; {@code unopened} when they have never opened it (5.1 "Yeni").
 *
 * @param unitName    the unit a team channel belongs to
 * @param counterpart the other side of a 1:1
 * @param memberCount the interns in it, plus the coordinators who post there
 */
public record IskurChannelResponse(
		UUID id,
		UUID programId,
		String programName,
		IskurChannelKind kind,
		String name,
		@Nullable String unitName,
		@Nullable Counterpart counterpart,
		int memberCount,
		boolean canWrite,
		boolean coordinatorView,
		long unreadCount,
		boolean unopened,
		@Nullable Instant lastMessageAt) {

	/**
	 * The other side of a 1:1.
	 *
	 * @param leader true when the other side is the reader's team leader
	 */
	public record Counterpart(UUID userId, String fullName, boolean leader) {
	}
}
