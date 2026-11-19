package com.dps.teknokent.dto.requests;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

import com.dps.teknokent.domain.IskurSkillLevel;
import com.dps.teknokent.domain.IskurTeamRolePreference;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The whole onboarding form as the intern has it on screen. Saving a draft stores it as it is; submitting also checks
 * that it is complete. Leadership answers are at most 500 characters each (Figma 04's counter).
 */
public record IskurOnboardingRequest(
		UUID actorId,
		@Min(1) @Max(3) Integer weeklyDays,
		@Size(max = 28) List<@Valid @NotNull Range> schedule,
		@Min(0) @Max(140) Integer priorIskurDays,
		@Size(max = 20) List<@NotNull UUID> unitIds,
		@Size(max = 200) List<@Valid @NotNull SkillAnswer> skills,
		@Size(max = 2) List<@NotNull IskurTeamRolePreference> teamRoles,
		@Valid Leadership leadership) {

	private static final String TIME = "([01]\\d|2[0-3]):[0-5]\\d";

	/** One stretch of work hours, "HH:mm" to "HH:mm" on one day. */
	public record Range(@NotNull DayOfWeek day, @NotNull @Pattern(regexp = TIME) String start,
			@NotNull @Pattern(regexp = TIME) String end) {
	}

	public record SkillAnswer(@NotNull UUID skillId, @NotNull IskurSkillLevel level) {
	}

	public record Leadership(boolean wants, @Size(max = 500) String motivation, @Size(max = 500) String experience,
			@Size(max = 500) String conflict) {
	}
}
