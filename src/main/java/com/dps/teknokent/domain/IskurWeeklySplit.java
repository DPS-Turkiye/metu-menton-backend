package com.dps.teknokent.domain;

import org.jspecify.annotations.Nullable;

/**
 * How an intern's week divides between peer mentoring and team work (rules frame 322:3413). A work day is 7.5 hours;
 * the default mentoring share is about 27 %, the three-day model's 6 of 22.5 hours, applied to one and two days too.
 * The coordinator may set another share per person, down to none.
 */
public record IskurWeeklySplit(int totalMinutes, int mentoringMinutes, int teamMinutes, boolean mentoringIsDefault) {

	public static final int MINUTES_PER_DAY = 450;
	public static final int MIN_DAYS = 1;
	public static final int MAX_DAYS = 3;
	/** Mentoring time is set in half-hour steps, like task estimates. */
	public static final int STEP_MINUTES = 30;
	private static final int[] DEFAULT_MENTORING = { 120, 240, 360 };

	/** The split for {@code days} work days; a null {@code mentoringOverride} means the default share. */
	public static IskurWeeklySplit of(int days, @Nullable Integer mentoringOverride) {
		if (days < MIN_DAYS || days > MAX_DAYS) {
			throw new IllegalArgumentException("An intern works 1 to 3 days a week, not " + days);
		}
		int total = days * MINUTES_PER_DAY;
		int mentoring = mentoringOverride == null ? DEFAULT_MENTORING[days - 1] : mentoringOverride;
		if (!fits(days, mentoring)) {
			throw new IllegalArgumentException("Mentoring minutes out of range: " + mentoring);
		}
		return new IskurWeeklySplit(total, mentoring, total - mentoring, mentoringOverride == null);
	}

	/** Whether {@code mentoring} minutes fit a week of {@code days} days and fall on a half-hour step. */
	public static boolean fits(int days, int mentoring) {
		return mentoring >= 0 && mentoring <= days * MINUTES_PER_DAY && mentoring % STEP_MINUTES == 0;
	}
}
