package com.dps.teknokent.domain;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/** How the connection to the SMTP server gets encrypted ({@code SMTP_TLS}). */
public enum SmtpTls {
	/** A plain connection that upgrades with STARTTLS, as {@code SMTP_STARTTLS} says (usually port 587 or 25). */
	STARTTLS,
	/** TLS from the first byte (usually port 465); {@code SMTP_STARTTLS} plays no part. */
	IMPLICIT;

	/** Blank means STARTTLS. */
	public static SmtpTls parse(@Nullable String value) {
		if (value == null || value.isBlank()) {
			return STARTTLS;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
					"app.mail.smtp.tls (SMTP_TLS) must be starttls or implicit, not '" + value.trim() + "'.",
					exception);
		}
	}
}
