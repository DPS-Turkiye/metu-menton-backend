package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A leader asking another team for help; {@code taskId} ties it to one of their team's tasks. */
public record IskurTeamSupportOpenRequest(
		@NotNull UUID actorId,
		@NotNull UUID toTeamId,
		UUID taskId,
		@NotBlank @Size(max = 500) String need) {
}
