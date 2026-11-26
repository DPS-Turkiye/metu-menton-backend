package com.dps.teknokent.dto.responses;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * What an academician tells students about their office hours; title and department come from ODTÜ's record.
 *
 * @param listed whether Menton students see them in the list (off until they turn it on)
 */
public record OfficeHoursProfileResponse(boolean listed, @Nullable String defaultPlace, @Nullable String onlineLink,
		@Nullable String contactEmail, Details details, Emails emails) {

	/**
	 * What students read on the academician's page.
	 *
	 * @param responseDays how many days they usually take to answer; empty when they did not say
	 */
	public record Details(@Nullable String about, List<Topic> topics, List<Course> courses, List<String> expertise,
			@Nullable Integer responseDays) {
	}

	public record Topic(String title, @Nullable String body) {
	}

	public record Course(String code, String name) {
	}

	public record Emails(boolean newRequest, boolean cancellation) {
	}
}
