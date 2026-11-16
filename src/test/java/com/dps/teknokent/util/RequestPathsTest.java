package com.dps.teknokent.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestPathsTest {

	private static final String TOKEN = "Abc_def-123".repeat(3) + "xyz0123456";

	@Test
	void hidesACalendarFeedsToken() {
		assertThat(RequestPaths.redacted("/api/office-hours/calendar-feeds/" + TOKEN))
				.isEqualTo("/api/office-hours/calendar-feeds/[redacted]");
		assertThat(RequestPaths.redacted("/api/office-hours/calendar-feeds/" + TOKEN + ";jsessionid=1"))
				.isEqualTo("/api/office-hours/calendar-feeds/[redacted];jsessionid=1")
				.doesNotContain(TOKEN);
	}

	@Test
	void leavesEveryOtherPathAsItIs() {
		assertThat(RequestPaths.redacted("/api/office-hours/users/u-1/calendar-feed"))
				.isEqualTo("/api/office-hours/users/u-1/calendar-feed");
		assertThat(RequestPaths.redacted("/api/office-hours/calendar-feeds/"))
				.isEqualTo("/api/office-hours/calendar-feeds/");
	}
}
