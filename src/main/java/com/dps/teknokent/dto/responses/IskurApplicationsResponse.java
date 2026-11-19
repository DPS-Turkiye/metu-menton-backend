package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurTeamRolePreference;

/**
 * What a peer program's coordinators see of the interns' onboarding: each submitted form (the "Tercihler" panel of
 * the placement screen), how many are still drafts, and the skills each unit asks about.
 */
public record IskurApplicationsResponse(
		UUID programId,
		List<Unit> units,
		List<Application> applications,
		int drafts) {

	public record Unit(UUID id, String name, List<IskurOnboardingResponse.Skill> skills) {
	}

	public record Application(
			UUID userId,
			String fullName,
			Instant submittedAt,
			@Nullable Integer weeklyDays,
			List<IskurOnboardingResponse.Range> schedule,
			@Nullable Integer priorIskurDays,
			@Nullable Integer remainingIskurDays,
			List<UUID> unitIds,
			List<IskurOnboardingResponse.SkillAnswer> skills,
			List<IskurTeamRolePreference> teamRoles,
			IskurOnboardingResponse.Documents documents,
			IskurOnboardingResponse.Leadership leadership) {
	}
}
