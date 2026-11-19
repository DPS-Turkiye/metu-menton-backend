package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurPlacementStatus;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.IskurWarning;
import com.dps.teknokent.domain.IskurWeeklySplit;

/**
 * A peer program's İŞKUR structure for its coordinators: units, their teams with the people placed in them, and the
 * interns not placed yet. Every change on the placement screen answers with the whole of it.
 */
public record IskurWorkspaceResponse(
		UUID programId,
		List<Unit> units,
		List<Person> pool,
		List<Coordinator> coordinators,
		Counts counts) {

	public record Unit(UUID id, String name, int sortOrder, @Nullable Coordinator coordinator, List<Team> teams) {
	}

	public record Team(UUID id, String name, int sortOrder, List<Placement> people, List<IskurWarning> warnings) {
	}

	/** Someone who can be placed: a student or peer mentor of the program. */
	public record Person(UUID userId, String fullName, String email, @Nullable String department,
			boolean iskurParticipant, boolean peerMentor) {
	}

	public record Placement(
			UUID userId,
			String fullName,
			String email,
			@Nullable String department,
			boolean iskurParticipant,
			boolean peerMentor,
			UUID teamId,
			IskurTeamRole role,
			@Nullable UUID leaderId,
			IskurPlacementStatus status,
			@Nullable Integer weeklyDays,
			@Nullable IskurWeeklySplit split,
			List<IskurWarning> warnings,
			@Nullable Instant finalizedAt) {
	}

	public record Coordinator(UUID userId, String fullName) {
	}

	public record Counts(int interns, int placed, int finalized, int leaders) {
	}
}
