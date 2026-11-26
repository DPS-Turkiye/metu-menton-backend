package com.dps.teknokent.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ProgramFeatureTest {

	@Test
	void aSemesterRunsAtLeastOneFeature() {
		assertThat(ProgramFeature.validSet(Set.of())).isFalse();
		assertThat(ProgramFeature.validSet(EnumSet.of(ProgramFeature.OFFICE_HOURS))).isTrue();
		assertThat(ProgramFeature.validSet(EnumSet.of(ProgramFeature.PEER_MENTORING))).isTrue();
	}

	@Test
	void iskurRunsOnlyNextToPeerMentoring() {
		assertThat(ProgramFeature.validSet(EnumSet.of(ProgramFeature.ISKUR))).isFalse();
		assertThat(ProgramFeature.validSet(EnumSet.of(ProgramFeature.ISKUR, ProgramFeature.OFFICE_HOURS))).isFalse();
		assertThat(ProgramFeature.validSet(EnumSet.allOf(ProgramFeature.class))).isTrue();
	}
}
