package com.dps.teknokent.config;

import java.util.Locale;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.dps.teknokent.domain.SmtpStartTls;
import com.dps.teknokent.domain.SmtpTls;

/**
 * The SMTP server e-mail goes through when {@code APP_MAIL_TRANSPORT=smtp}. An empty username and password mean a
 * relay that accepts this server without a login. Blank counts as unset here, because an env var declared empty
 * never reaches a placeholder's default.
 */
@ConfigurationProperties(prefix = "app.mail.smtp")
public record SmtpProperties(String host, String port, String username, String password, String tls, String starttls) {

	private static final int STARTTLS_PORT = 587;
	private static final int IMPLICIT_TLS_PORT = 465;
	private static final int HIGHEST_PORT = 65_535;
	private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9.-]+$");

	public SmtpProperties(
			@Nullable String host,
			@Nullable String port,
			@Nullable String username,
			@Nullable String password,
			@Nullable String tls,
			@Nullable String starttls) {
		this.host = trimmed(host);
		this.port = trimmed(port);
		this.username = trimmed(username);
		this.password = trimmed(password);
		this.tls = trimmed(tls);
		this.starttls = trimmed(starttls);
	}

	public int portNumber() {
		if (port.isEmpty()) {
			return tlsMode() == SmtpTls.IMPLICIT ? IMPLICIT_TLS_PORT : STARTTLS_PORT;
		}
		int number;
		try {
			number = Integer.parseInt(port);
		} catch (NumberFormatException invalid) {
			throw new IllegalStateException("SMTP_PORT must be a port number, such as 587.", invalid);
		}
		if (number < 1 || number > HIGHEST_PORT) {
			throw new IllegalStateException("SMTP_PORT must be between 1 and 65535.");
		}
		return number;
	}

	public SmtpTls tlsMode() {
		return SmtpTls.parse(tls);
	}

	public SmtpStartTls startTls() {
		return SmtpStartTls.parse(starttls);
	}

	/** Whether nothing can leave unencrypted: TLS from the first byte, or STARTTLS that is not allowed to fail. */
	public boolean alwaysEncrypted() {
		return tlsMode() == SmtpTls.IMPLICIT || startTls() == SmtpStartTls.REQUIRED;
	}

	/** The encryption in words, for the start-up log. */
	public String encryption() {
		if (tlsMode() == SmtpTls.IMPLICIT) {
			return "TLS from the first byte";
		}
		return "STARTTLS " + startTls().name().toLowerCase(Locale.ROOT);
	}

	public boolean hasLogin() {
		return !username.isEmpty();
	}

	/** Stops the start-up on settings that could never send. The messages name a variable, never its value. */
	public void requireUsable() {
		if (host.isEmpty()) {
			throw new IllegalStateException("APP_MAIL_TRANSPORT=smtp needs SMTP_HOST, the mail server's address.");
		}
		if (!HOST.matcher(host).matches()) {
			throw new IllegalStateException(
					"SMTP_HOST must be a host name or an IP address alone: no scheme, port or login.");
		}
		if (username.isEmpty() != password.isEmpty()) {
			throw new IllegalStateException("SMTP_USERNAME and SMTP_PASSWORD go together: set both, or neither for"
					+ " a server that needs no login.");
		}
		tlsMode();
		portNumber();
		startTls();
	}

	/** A record prints every component; this one must never print the password. */
	@Override
	public String toString() {
		return "SmtpProperties[host=" + host + ", port=" + port + ", tls=" + tls + ", starttls=" + starttls + ", login="
				+ (hasLogin() ? "set" : "none") + "]";
	}

	private static String trimmed(@Nullable String value) {
		return value == null ? "" : value.strip();
	}
}
