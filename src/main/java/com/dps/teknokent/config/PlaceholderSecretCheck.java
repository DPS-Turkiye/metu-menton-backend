package com.dps.teknokent.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start while a secret still holds the {@code change-me} placeholder that {@code .env.example} and
 * {@code application.yml} ship with: on the shared Basic credential that would let anyone act as the frontend, and on
 * the realtime secret anyone could mint room tokens. Those two may not be blank either.
 *
 * <p>Only a developer's machine may keep them: an unreleased build ({@code APP_VERSION} unset or {@code local}, as
 * {@code docker compose up}, {@code dev/} and CI leave it) whose only profile is {@code dev}. A released build (the
 * deploy workflow sets {@code APP_VERSION} to the tag) refuses even then, since {@code .env.example} sets {@code dev}
 * and a copy of it is the likeliest way a placeholder reaches a server. An image built by hand without
 * {@code APP_VERSION} refuses too, unless {@code dev} is its only profile, which a server never should have.
 */
@Component
public class PlaceholderSecretCheck {

	/** Property, and the environment variable that sets it, for the message. */
	static final Map<String, String> SECRETS = Map.of(
			"spring.security.user.password", "SECURITY_PASSWORD",
			"spring.datasource.password", "DATABASE_PASSWORD",
			"app.realtime.token-secret", "REALTIME_TOKEN_SECRET");

	private static final Set<String> PLACEHOLDERS = Set.of("change-me", "changeme", "change_me");
	/** Secrets that must not be blank either; a blank database password is fine for a local or trust-auth database. */
	private static final Set<String> NEVER_BLANK = Set.of("spring.security.user.password", "app.realtime.token-secret");
	private static final String UNRELEASED_VERSION = "local";

	public PlaceholderSecretCheck(Environment environment) {
		String version = environment.getProperty("APP_VERSION", UNRELEASED_VERSION).trim();
		boolean released = !version.isEmpty() && !UNRELEASED_VERSION.equals(version);
		if (!released && AppProfiles.onlyDev(environment)) {
			return;
		}
		List<String> placeholders = placeholderSecrets(environment);
		if (placeholders.isEmpty()) {
			return;
		}
		String who = released ? "Release " + version : "A build without APP_VERSION outside the dev profile";
		throw new IllegalStateException(who + " refuses to start with a placeholder or blank secret in "
				+ String.join(", ", placeholders) + "; set a real value in the server's environment.");
	}

	static List<String> placeholderSecrets(Environment environment) {
		List<String> found = new ArrayList<>();
		SECRETS.forEach((property, variable) -> {
			String value = environment.getProperty(property);
			boolean blank = value != null && value.isBlank() && NEVER_BLANK.contains(property);
			if (blank || (value != null && PLACEHOLDERS.contains(value.trim().toLowerCase(Locale.ROOT)))) {
				found.add(variable);
			}
		});
		found.sort(Comparator.naturalOrder());
		return found;
	}
}
