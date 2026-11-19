package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurInternAccountState;

/**
 * A peer program's İŞKUR intern list for its coordinators: every listed address, whether its owner has signed up, and
 * whether they are placed (a placed intern cannot be removed). {@code rejected} echoes the pasted entries an add call
 * could not read as e-mail addresses; it is empty on every other call.
 */
public record IskurInternListResponse(
		UUID programId,
		List<Entry> entries,
		Counts counts,
		List<String> rejected) {

	/**
	 * One listed address. The name is shown only for an intern of this program; an account outside it stays anonymous.
	 */
	public record Entry(UUID id, String email, Instant addedAt, IskurInternAccountState account,
			@Nullable UUID userId, @Nullable String fullName, boolean placed) {
	}

	public record Counts(int listed, int interns, int notSignedUp, int outsideProgram) {
	}
}
