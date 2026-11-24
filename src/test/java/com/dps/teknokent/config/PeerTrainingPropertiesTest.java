package com.dps.teknokent.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class PeerTrainingPropertiesTest {

	@Test
	void byDefaultEveryActiveStudentMayBecomeAnAkranMentor() {
		assertThat(bind(Map.of())).isEqualTo(new PeerTrainingProperties(false, false));
	}

	@Test
	void theInternsOnlyRuleCanBeTurnedOn() {
		assertThat(bind(Map.of("app.peer-training.interns-only", "true")).internsOnly()).isTrue();
	}

	private static PeerTrainingProperties bind(Map<String, String> values) {
		return new Binder(new MapConfigurationPropertySource(values))
				.bindOrCreate("app.peer-training", PeerTrainingProperties.class);
	}
}
