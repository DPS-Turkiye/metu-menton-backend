package com.dps.teknokent.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.dps.teknokent.config.LtiProperties;

class LtiToolKeysTest {

	private static LtiProperties withKey(String pem) {
		return new LtiProperties(null, null, null, null, null, null, pem);
	}

	private static LtiProperties configuredWithKey(String pem) {
		return new LtiProperties("https://moodle.example.invalid", "client", "1",
				"https://moodle.example.invalid/mod/lti/auth.php", "https://moodle.example.invalid/mod/lti/certs.php",
				"https://menton.example.invalid/lti/launch", pem);
	}

	private static String freshPem() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----";
	}

	@SuppressWarnings("unchecked") // A JWK set is a JSON map; its "keys" entry is a list of maps.
	private static Map<String, Object> onlyKey(LtiToolKeys keys) {
		List<Map<String, Object>> list = (List<Map<String, Object>>) keys.publicKeySet().get("keys");
		assertThat(list).hasSize(1);
		return list.get(0);
	}

	@Test
	void publishesOnlyThePublicHalfOfAConfiguredKey() throws Exception {
		String pem = freshPem();

		Map<String, Object> key = onlyKey(new LtiToolKeys(withKey(pem), false));

		assertThat(key).containsEntry("kty", "RSA").containsEntry("alg", "RS256").containsEntry("use", "sig")
				.containsKeys("n", "e", "kid").doesNotContainKeys("d", "p", "q");
		assertThat(onlyKey(new LtiToolKeys(withKey(pem), false)).get("kid")).isEqualTo(key.get("kid"));
	}

	@Test
	void makesAThrowawayKeyWhenNoneIsConfiguredAndLtiIsOff() {
		assertThat(onlyKey(new LtiToolKeys(withKey(""), false)))
				.containsEntry("kty", "RSA").doesNotContainKey("d");
	}

	@Test
	void aDevMachineMayRunConfiguredLtiOnAThrowawayKey() {
		assertThat(onlyKey(new LtiToolKeys(configuredWithKey(" "), true))).containsEntry("kty", "RSA");
	}

	@Test
	void configuredLtiWithoutAKeyRefusesToStartOutsideTheDevProfile() {
		assertThatThrownBy(() -> new LtiToolKeys(configuredWithKey(""), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("LTI_TOOL_PRIVATE_KEY");
	}

	@Test
	void configuredLtiWithAKeyStartsOnAServer() throws Exception {
		assertThat(onlyKey(new LtiToolKeys(configuredWithKey(freshPem()), false))).containsEntry("kty", "RSA");
	}

	@Test
	void theSpringConstructorReadsTheDevProfile() {
		MockEnvironment dev = new MockEnvironment();
		dev.setActiveProfiles("dev");

		assertThat(onlyKey(new LtiToolKeys(configuredWithKey(""), dev))).containsEntry("kty", "RSA");
		assertThatThrownBy(() -> new LtiToolKeys(configuredWithKey(""), new MockEnvironment()))
				.isInstanceOf(IllegalStateException.class);
		MockEnvironment devAndProd = new MockEnvironment();
		devAndProd.setActiveProfiles("dev", "prod");
		assertThatThrownBy(() -> new LtiToolKeys(configuredWithKey(""), devAndProd))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void refusesSomethingThatIsNotAPrivateKey() {
		assertThatThrownBy(() -> new LtiToolKeys(withKey("not a key"), false))
				.isInstanceOf(IllegalStateException.class);
	}
}
