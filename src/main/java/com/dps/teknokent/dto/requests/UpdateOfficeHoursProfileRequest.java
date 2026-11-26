package com.dps.teknokent.dto.requests;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The four top fields are saved as a whole; an empty one clears it. {@code details} and {@code emails} are saved
 * only when sent, each as a whole: a client that does not know them (an older screen, the "listed" switch) leaves
 * them as they are, and one that sends them with an empty field clears that field.
 */
public record UpdateOfficeHoursProfileRequest(
		boolean listed,
		@Size(max = 200) String defaultPlace,
		@Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "Online link must start with http:// or https://") String onlineLink,
		@Size(max = 320) @Email String contactEmail,
		@Valid Details details,
		@Valid Emails emails) {

	/** What students read on the academician's page; a missing list is taken as empty. */
	public record Details(
			@Size(max = 1000) @Pattern(regexp = PlainText.PATTERN, message = PlainText.MESSAGE) String about,
			@Size(max = 6) List<@NotNull @Valid Topic> topics,
			@Size(max = 10) List<@NotNull @Valid Course> courses,
			@Size(max = 5) List<@NotBlank @Size(max = 40) @Pattern(regexp = PlainText.PATTERN, message = PlainText.MESSAGE) String> expertise,
			@Min(1) @Max(14) Integer responseDays) {
	}

	public record Topic(
			@NotBlank @Size(max = 80) @Pattern(regexp = PlainText.PATTERN, message = PlainText.MESSAGE) String title,
			@Size(max = 300) @Pattern(regexp = PlainText.PATTERN, message = PlainText.MESSAGE) String body) {
	}

	public record Course(
			@NotBlank @Size(max = 20) @Pattern(regexp = PlainText.PATTERN, message = PlainText.MESSAGE) String code,
			@NotBlank @Size(max = 120) @Pattern(regexp = PlainText.PATTERN, message = PlainText.MESSAGE) String name) {
	}

	/** The academician's own Office Hours e-mails; meeting reminders always go. */
	public record Emails(boolean newRequest, boolean cancellation) {
	}
}
