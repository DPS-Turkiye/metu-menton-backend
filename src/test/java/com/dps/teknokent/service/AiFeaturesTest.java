package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.dps.teknokent.domain.AiFeature;
import com.dps.teknokent.domain.Edition;
import com.dps.teknokent.dto.responses.AiFeaturesResponse;
import com.dps.teknokent.exception.AiFeatureDisabledException;

class AiFeaturesTest {

	@Test
	void everyFeatureIsOnWhenNothingIsSwitchedOff() {
		AiFeatures features = TestAiFeatures.allOn();

		for (AiFeature feature : AiFeature.values()) {
			assertThat(features.isEnabled(feature)).isTrue();
			assertThatCode(() -> features.require(feature)).doesNotThrowAnyException();
		}
		assertThat(features.pgvectorEnabled()).isTrue();
		assertThat(features.snapshot()).isEqualTo(new AiFeaturesResponse(true, true, true, true, true));
	}

	@ParameterizedTest
	@EnumSource(AiFeature.class)
	void aSwitchedOffFeatureIsRefusedWhileTheOthersStayOn(AiFeature off) {
		AiFeatures features = TestAiFeatures.allOnExcept(off);

		assertThatThrownBy(() -> features.require(off))
				.isInstanceOf(AiFeatureDisabledException.class)
				.satisfies(error -> assertThat(((AiFeatureDisabledException) error).feature()).isEqualTo(off));
		for (AiFeature other : AiFeature.values()) {
			assertThat(features.isEnabled(other)).isEqualTo(other != off);
		}
	}

	@Test
	void theSnapshotNamesEachSwitch() {
		AiFeaturesResponse snapshot = TestAiFeatures
				.allOnExcept(AiFeature.PROFILE_PARSING, AiFeature.SYLLABUS, AiFeature.TASK_HANDOVER)
				.snapshot();

		assertThat(snapshot).isEqualTo(new AiFeaturesResponse(false, false, true, true, false));
	}

	@Test
	void teknokentWithNoSwitchesSetKeepsEveryFeatureOn() {
		AiFeatures features = new AiFeatures(Edition.TEKNOKENT, Map.of(), "");

		assertThat(features.snapshot()).isEqualTo(new AiFeaturesResponse(true, true, true, true, true));
		assertThat(features.pgvectorEnabled()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "false", "FALSE", " off ", "no", "0" })
	void teknokentReadsSpringsSpellingsOfOff(String off) {
		AiFeatures features = new AiFeatures(Edition.TEKNOKENT, Map.of(AiFeature.SYLLABUS, off), off);

		assertThat(features.isEnabled(AiFeature.SYLLABUS)).isFalse();
		assertThat(features.isEnabled(AiFeature.MATCHING)).isTrue();
		assertThat(features.pgvectorEnabled()).isFalse();
	}

	@Test
	void aSwitchThatIsNeitherTrueNorFalseStopsTheStartup() {
		assertThatThrownBy(() -> new AiFeatures(Edition.TEKNOKENT, Map.of(AiFeature.MATCHING, "maybe"), ""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AI_MATCHING_ENABLED")
				.hasMessageContaining("maybe");
	}

	@Test
	void odtuWithNoSwitchesSetRunsNoAiFeatureAndNoPgvector() {
		AiFeatures features = new AiFeatures(Edition.ODTU, Map.of(), "");

		assertThat(features.snapshot()).isEqualTo(new AiFeaturesResponse(false, false, false, false, false));
		assertThat(features.pgvectorEnabled()).isFalse();
		for (AiFeature feature : AiFeature.values()) {
			assertThatThrownBy(() -> features.require(feature)).isInstanceOf(AiFeatureDisabledException.class);
		}
	}

	@Test
	void odtuAcceptsSwitchesThatSayOff() {
		Map<AiFeature, String> allOff = Map.of(
				AiFeature.PROFILE_PARSING, "false",
				AiFeature.SYLLABUS, "false",
				AiFeature.MATCHING, "false",
				AiFeature.MEETING_REPORT, "false",
				AiFeature.TASK_HANDOVER, "false");

		AiFeatures features = new AiFeatures(Edition.ODTU, allOff, "false");

		assertThat(features.snapshot()).isEqualTo(new AiFeaturesResponse(false, false, false, false, false));
		assertThat(features.pgvectorEnabled()).isFalse();
	}

	@ParameterizedTest
	@EnumSource(AiFeature.class)
	void odtuRefusesToStartWithAnyAiFeatureSwitchedOn(AiFeature feature) {
		assertThatThrownBy(() -> new AiFeatures(Edition.ODTU, Map.of(feature, "true"), ""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ODTU")
				.hasMessageContaining("_ENABLED");
	}

	@Test
	void odtuRefusesToStartWithThePgvectorShortlistSwitchedOn() {
		assertThatThrownBy(() -> new AiFeatures(Edition.ODTU, Map.of(), "true"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("MATCHING_PGVECTOR_ENABLED");
	}

	@Test
	void theSpringConstructorReadsTheEditionAndEachSwitch() {
		AiFeatures teknokent = new AiFeatures(new EditionService("TEKNOKENT"), "false", "", "true", "", "off", "no");
		AiFeatures odtu = new AiFeatures(new EditionService("ODTU"), "", "", "", "", "", "");

		assertThat(teknokent.snapshot()).isEqualTo(new AiFeaturesResponse(false, true, true, true, false));
		assertThat(teknokent.pgvectorEnabled()).isFalse();
		assertThat(odtu.snapshot()).isEqualTo(new AiFeaturesResponse(false, false, false, false, false));
		assertThatThrownBy(() -> new AiFeatures(new EditionService("ODTU"), "", "", "", "yes", "", ""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AI_MEETING_REPORT_ENABLED");
	}
}
