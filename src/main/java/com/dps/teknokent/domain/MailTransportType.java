package com.dps.teknokent.domain;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/** How e-mail leaves the backend ({@code APP_MAIL_TRANSPORT}). */
public enum MailTransportType {
	/** Amazon SES, the only way before this setting existed. */
	SES,
	/** An SMTP server, such as a university's own mail relay. */
	SMTP,
	/** Nothing is sent; each mail's subject and recipient domain are logged. For development. */
	LOG,
	/** Nothing is sent. */
	OFF;

	/** Blank means SES, so a deployment that has never set it keeps sending as it did. */
	public static MailTransportType parse(@Nullable String value) {
		if (value == null || value.isBlank()) {
			return SES;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
					"app.mail.transport (APP_MAIL_TRANSPORT) must be ses, smtp, log or off, not '" + value.trim()
							+ "'.",
					exception);
		}
	}
}
