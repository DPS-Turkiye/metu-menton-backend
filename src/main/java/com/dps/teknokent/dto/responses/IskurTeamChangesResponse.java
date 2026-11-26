package com.dps.teknokent.dto.responses;

import java.util.List;
import java.util.UUID;

/** A program's team change requests for its coordinators, waiting ones first, then the newest. */
public record IskurTeamChangesResponse(UUID programId, List<IskurTeamChangeResponse> requests) {
}
