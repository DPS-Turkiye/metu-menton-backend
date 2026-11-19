package com.dps.teknokent.dto.responses;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurPlacementStatus;
import com.dps.teknokent.domain.IskurTeamRole;

/**
 * "Ekibin belli oldu" (Figma 05): the intern's own team, its leaders and members, the unit and its coordinator. Names
 * and roles only; no e-mails, no other team. {@code placement} is null until a coordinator places them.
 */
public record IskurTeamRevealResponse(
		UUID programId,
		String programName,
		boolean onboardingSubmitted,
		@Nullable Placement placement) {

	/** {@code trialWeek} is 1 to 4 during the trial weeks of a provisional placement, otherwise null. */
	public record Placement(
			IskurPlacementStatus status,
			@Nullable Integer trialWeek,
			IskurTeamRole myRole,
			@Nullable Person myLeader,
			Unit unit,
			Team team,
			List<DayOfWeek> commonDays) {
	}

	public record Unit(String name, @Nullable Person coordinator) {
	}

	/** Leaders first, each followed by their sub-group, then members without a leader. */
	public record Team(String name, List<Member> people) {
	}

	public record Member(UUID userId, String fullName, IskurTeamRole role, @Nullable UUID leaderId, boolean me) {
	}

	public record Person(UUID userId, String fullName) {
	}
}
