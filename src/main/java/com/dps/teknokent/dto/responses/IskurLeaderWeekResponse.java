package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurTaskStatus;
import com.dps.teknokent.domain.IskurWeekStatus;

/**
 * One week as the leader reviews it (4.1). Team work comes in full: lines, outputs and notes. Peer mentoring comes as
 * numbers only, per day, with the titles of the support activities from the coordinators' pool. The shape has no
 * field that could carry a mentee, a meeting note or a report body ("Mentee adı, görüşme notu, rapor içeriği:
 * Görmez").
 */
public record IskurLeaderWeekResponse(
		UUID weekId,
		LocalDate weekStart,
		Person person,
		IskurWeekStatus status,
		@Nullable Instant submittedAt,
		IskurWeekResponse.@Nullable Split split,
		IskurWeekResponse.Totals totals,
		List<TeamLine> team,
		Mentoring mentoring,
		IskurWeekResponse.@Nullable Decision decision,
		IskurWeekResponse.@Nullable Objection objection,
		boolean canDecide,
		List<IskurLeaderQueueResponse.Row> queue) {

	public record Person(UUID userId, String fullName, @Nullable Integer weeklyDays) {
	}

	/** A team line; a line on a task also says where the task stands and why it was handed over. */
	public record TeamLine(LocalDate date, String title, int minutes, @Nullable String output, @Nullable String note,
			@Nullable IskurTaskStatus taskStatus, @Nullable String handoverReason) {
	}

	public record Mentoring(int minutes, int meetingCount, int reportsSubmitted, int reportsDue, List<Day> days) {
	}

	/** One day of mentoring: counts and minutes, and the pool titles of its support activities. */
	public record Day(LocalDate date, int meetingCount, int reportsSubmitted, int reportsDue, int minutes,
			List<String> supportActivityTitles) {
	}
}
