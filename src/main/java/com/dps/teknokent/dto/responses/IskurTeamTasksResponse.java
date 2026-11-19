package com.dps.teknokent.dto.responses;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurTeamRole;

/**
 * Ekibim (2.1) for one week: the caller's team, its people, and the week's tasks per person. A leader sees everyone's
 * tasks and may assign to their own sub-group and themselves; a member sees only their own tasks. The roster carries
 * names and roles only, which every team member may see.
 */
public record IskurTeamTasksResponse(
		LocalDate weekStart,
		Team team,
		IskurTeamRole viewerRole,
		boolean canAssign,
		List<Member> members,
		List<RosterPerson> roster) {

	public record Team(UUID id, String name, String unitName) {
	}

	/**
	 * One person's week. {@code teamMinutes} is their team-work target (null while their work days are not set);
	 * {@code plannedMinutes} sums the estimates of their open and done tasks due in the week.
	 */
	public record Member(
			UUID userId,
			String fullName,
			IskurTeamRole role,
			@Nullable Integer weeklyDays,
			@Nullable Integer teamMinutes,
			int plannedMinutes,
			boolean overTarget,
			boolean assignable,
			List<IskurTaskResponse> tasks) {
	}

	public record RosterPerson(UUID userId, String fullName, IskurTeamRole role, @Nullable UUID leaderId) {
	}
}
