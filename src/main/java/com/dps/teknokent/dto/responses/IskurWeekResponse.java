package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurTaskKind;
import com.dps.teknokent.domain.IskurTaskStatus;
import com.dps.teknokent.domain.IskurWeekDecision;
import com.dps.teknokent.domain.IskurWeekStatus;
import com.dps.teknokent.domain.IskurWorkEntryKind;

/**
 * An intern's own week (Bu Haftam 1.1, Çalışma kaydı 3.1/3.2): the split, what is logged on each side, the lines of
 * the log, the week's tasks, the support pool, and where the week stands with the leader. {@code weekId} is null
 * until something is saved; the status is DRAFT then.
 */
public record IskurWeekResponse(
		LocalDate weekStart,
		@Nullable UUID weekId,
		IskurWeekStatus status,
		boolean editable,
		boolean canSubmit,
		@Nullable Instant submittedAt,
		@Nullable Decision decision,
		@Nullable Objection objection,
		@Nullable Person reviewer,
		@Nullable Split split,
		Totals totals,
		Mentoring mentoring,
		boolean needsSupportActivity,
		List<Entry> entries,
		List<Task> tasks,
		List<SupportOption> supportActivities) {

	public record Person(UUID userId, String fullName) {
	}

	/** The week's targets; null while the intern's work days are not set. */
	public record Split(int totalMinutes, int mentoringMinutes, int teamMinutes) {
	}

	/** Logged so far: team lines, and mentoring (meetings, other mentoring work and support lines). */
	public record Totals(int teamMinutes, int mentoringMinutes) {
	}

	public record Mentoring(int meetingCount, int meetingMinutes, int activityMinutes, int supportMinutes,
			int reportsSubmitted, int reportsDue, boolean meetingsAhead) {
	}

	public record Entry(UUID id, LocalDate date, IskurWorkEntryKind kind, @Nullable UUID taskId,
			@Nullable UUID supportActivityId, String title, int minutes, @Nullable String output,
			@Nullable String note) {
	}

	public record Task(UUID id, String title, IskurTaskKind kind, int estimatedMinutes, LocalDate dueDate,
			IskurTaskStatus status, boolean needsOutput, @Nullable Integer actualMinutes) {
	}

	public record SupportOption(UUID id, String title, @Nullable String description) {
	}

	public record Decision(IskurWeekDecision decision, @Nullable String reason, boolean timeMatchesWork,
			boolean effortDocumented, boolean outputsSufficient, Instant decidedAt, @Nullable Person decidedBy) {
	}

	public record Objection(String text, Instant at) {
	}
}
