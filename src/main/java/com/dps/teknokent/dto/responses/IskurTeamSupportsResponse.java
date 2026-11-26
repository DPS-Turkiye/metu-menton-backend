package com.dps.teknokent.dto.responses;

import java.util.List;
import java.util.UUID;

/**
 * A leader's view: the teams they can ask ({@code teams}, every other team of the program), what their team was asked
 * ({@code incoming}) and what it asked ({@code outgoing}), newest first.
 */
public record IskurTeamSupportsResponse(
		UUID programId,
		UUID teamId,
		String teamName,
		List<Team> teams,
		List<IskurTeamSupportResponse> incoming,
		List<IskurTeamSupportResponse> outgoing) {

	public record Team(UUID id, String name, String unitName) {
	}
}
