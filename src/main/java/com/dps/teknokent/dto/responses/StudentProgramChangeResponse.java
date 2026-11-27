package com.dps.teknokent.dto.responses;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** What an admin's move or removal did; {@code movedMentorRow}: the student was an Akran mentor in the old semester. */
public record StudentProgramChangeResponse(UUID userId, @Nullable UUID fromProgramId, @Nullable UUID toProgramId,
		boolean movedMentorRow) {
}
