package com.dps.teknokent.dto.requests;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Addresses to add to the İŞKUR intern list, as the coordinator pasted them. Each is trimmed and lowercased; blanks,
 * repeats and addresses already on the list are skipped.
 */
public record IskurInternEmailsRequest(UUID actorId,
		@NotNull @Size(min = 1, max = IskurInternEmailsRequest.MAX_PER_REQUEST) List<@NotNull @Size(max = 320) String> emails) {

	public static final int MAX_PER_REQUEST = 500;
}
