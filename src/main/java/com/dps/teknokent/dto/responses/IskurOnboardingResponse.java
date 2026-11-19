package com.dps.teknokent.dto.responses;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurSkillLevel;
import com.dps.teknokent.domain.IskurTeamRolePreference;

/** The intern's onboarding form, and what the form needs to ask its questions (units, skills, ODTÜ record, limits). */
public record IskurOnboardingResponse(Form form, Context context) {

	public record Form(
			@Nullable Integer weeklyDays,
			List<Range> schedule,
			@Nullable Integer priorIskurDays,
			List<UUID> unitIds,
			List<SkillAnswer> skills,
			List<IskurTeamRolePreference> teamRoles,
			Documents documents,
			Leadership leadership,
			@Nullable Instant submittedAt) {
	}

	/** "HH:mm" in Turkish time. */
	public record Range(DayOfWeek day, String start, String end) {
	}

	public record SkillAnswer(UUID skillId, IskurSkillLevel level) {
	}

	public record Documents(@Nullable FileRef cv, @Nullable FileRef transcript) {
	}

	/** What the intern sees of an uploaded file; the file itself is only for the program's coordinators. */
	public record FileRef(String fileName, long sizeBytes, Instant uploadedAt) {
	}

	public record Leadership(boolean wants, @Nullable String motivation, @Nullable String experience,
			@Nullable String conflict) {
	}

	/**
	 * {@code documentsEnabled} is false while file storage is not set up: the documents step says so and the form goes
	 * without a CV. A term needs about {@code weeklyDays × termWeeks} İŞKUR days of the {@code maxIskurDays}.
	 */
	public record Context(
			UUID programId,
			String programName,
			@Nullable String department,
			@Nullable Integer classYear,
			List<Unit> units,
			boolean documentsEnabled,
			int maxIskurDays,
			int termWeeks,
			int minimumLeaderDays,
			int maxTeamRoles,
			int answerMaxLength) {
	}

	public record Unit(UUID id, String name, List<String> teamNames, List<Skill> skills) {
	}

	public record Skill(UUID id, String name) {
	}
}
