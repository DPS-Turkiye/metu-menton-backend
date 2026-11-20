package com.dps.teknokent.domain;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/** Whether the SMTP conversation is encrypted with STARTTLS ({@code SMTP_STARTTLS}). */
public enum SmtpStartTls {
	/** Encrypted or not sent at all. */
	REQUIRED,
	/** Encrypted when the server offers it, plain otherwise. */
	OPTIONAL,
	/** Never encrypted. */
	OFF;

	/** Blank means required: plain text is something a deployment asks for, never a default. */
	public static SmtpStartTls parse(@Nullable String value) {
		if (value == null || value.isBlank()) {
			return REQUIRED;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
					"app.mail.smtp.starttls (SMTP_STARTTLS) must be required, optional or off, not '" + value.trim()
							+ "'.",
					exception);
		}
	}
}
