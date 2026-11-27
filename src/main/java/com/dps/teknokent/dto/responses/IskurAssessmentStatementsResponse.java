package com.dps.teknokent.dto.responses;

import java.util.List;
import java.util.UUID;

/** A program's statements for its coordinators, waiting ones first, then the newest. */
public record IskurAssessmentStatementsResponse(UUID programId, long pendingCount,
		List<IskurAssessmentStatementResponse> statements) {
}
