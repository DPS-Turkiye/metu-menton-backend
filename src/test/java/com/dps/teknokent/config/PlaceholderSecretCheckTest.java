package com.dps.teknokent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PlaceholderSecretCheckTest {

	@Test
	void aReleaseWithThePlaceholderBasicPasswordRefusesToStart() {
		MockEnvironment environment = realSecrets()
				.withProperty("APP_VERSION", "v1.42.0")
				.withProperty("spring.security.user.password", "change-me");

		assertThatThrownBy(() -> new PlaceholderSecretCheck(environment))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("v1.42.0")
				.hasMessageContaining("SECURITY_PASSWORD")
				.hasMessageNotContaining("change-me");
	}

	@Test
	void everyPlaceholderSecretIsNamedWhateverItsCaseOrSpacing() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("APP_VERSION", "v1.42.0")
				.withProperty("spring.security.user.password", " Change-Me ")
				.withProperty("spring.datasource.password", "CHANGEME")
				.withProperty("app.realtime.token-secret", "change_me");

		assertThat(PlaceholderSecretCheck.placeholderSecrets(environment))
				.containsExactly("DATABASE_PASSWORD", "REALTIME_TOKEN_SECRET", "SECURITY_PASSWORD");
		assertThatThrownBy(() -> new PlaceholderSecretCheck(environment))
				.hasMessageContaining("DATABASE_PASSWORD, REALTIME_TOKEN_SECRET, SECURITY_PASSWORD");
	}

	@Test
	void aReleaseWithRealSecretsStarts() {
		MockEnvironment environment = realSecrets().withProperty("APP_VERSION", "v1.42.0");

		assertThatCode(() -> new PlaceholderSecretCheck(environment)).doesNotThrowAnyException();
	}

	// docker compose and dev/editions run the template's values with the dev profile it sets.
	@Test
	void aLocalBuildOnTheDevProfileStartsWithThePlaceholdersFromTheTemplate() {
		MockEnvironment placeholders = placeholders();
		placeholders.setActiveProfiles("dev");

		assertThatCode(() -> new PlaceholderSecretCheck(placeholders)).doesNotThrowAnyException();
		assertThatCode(() -> new PlaceholderSecretCheck(placeholders.withProperty("APP_VERSION", "local")))
				.doesNotThrowAnyException();
		assertThatCode(() -> new PlaceholderSecretCheck(placeholders.withProperty("APP_VERSION", " ")))
				.doesNotThrowAnyException();
	}

	// An image built by hand on a server leaves APP_VERSION at "local"; only the dev profile may keep placeholders.
	@Test
	void aBuildWithoutAVersionOutsideTheDevProfileRefusesThePlaceholders() {
		assertThatThrownBy(() -> new PlaceholderSecretCheck(placeholders()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("without APP_VERSION")
				.hasMessageContaining("DATABASE_PASSWORD, REALTIME_TOKEN_SECRET, SECURITY_PASSWORD")
				.hasMessageNotContaining("change-me");
		MockEnvironment test = placeholders().withProperty("APP_VERSION", "local");
		test.setActiveProfiles("test");
		assertThatThrownBy(() -> new PlaceholderSecretCheck(test)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aBuildWithoutAVersionOutsideTheDevProfileStartsWithRealSecrets() {
		assertThatCode(() -> new PlaceholderSecretCheck(realSecrets())).doesNotThrowAnyException();
	}

	@Test
	void aReleaseRefusesThePlaceholdersEvenOnTheDevProfile() {
		MockEnvironment release = placeholders().withProperty("APP_VERSION", "v1.42.0");
		release.setActiveProfiles("dev");

		assertThatThrownBy(() -> new PlaceholderSecretCheck(release))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("v1.42.0");
	}

	@Test
	void devNextToAnotherProfileIsNoDevMachine() {
		MockEnvironment placeholders = placeholders();
		placeholders.setActiveProfiles("prod", "dev");

		assertThatThrownBy(() -> new PlaceholderSecretCheck(placeholders)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aBlankBasicPasswordOrRealtimeSecretIsRefusedButABlankDatabasePasswordIsNot() {
		MockEnvironment blanks = new MockEnvironment()
				.withProperty("spring.security.user.password", " ")
				.withProperty("spring.datasource.password", "")
				.withProperty("app.realtime.token-secret", "");

		assertThat(PlaceholderSecretCheck.placeholderSecrets(blanks))
				.containsExactly("REALTIME_TOKEN_SECRET", "SECURITY_PASSWORD");
		assertThatThrownBy(() -> new PlaceholderSecretCheck(blanks))
				.hasMessageContaining("REALTIME_TOKEN_SECRET, SECURITY_PASSWORD");
	}

	private static MockEnvironment placeholders() {
		return new MockEnvironment()
				.withProperty("spring.security.user.password", "change-me")
				.withProperty("spring.datasource.password", "change-me")
				.withProperty("app.realtime.token-secret", "change-me");
	}

	private static MockEnvironment realSecrets() {
		return new MockEnvironment()
				.withProperty("spring.security.user.password", "q8V2-long-random-value")
				.withProperty("spring.datasource.password", "another-real-one")
				.withProperty("app.realtime.token-secret", "and-a-third");
	}
}
