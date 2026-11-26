package com.dps.teknokent.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramType;

class ProgramTest {

	@Test
	void aSemesterRunsWhatItsSwitchesSay() {
		Program semester = program(ProgramType.SEMESTER);
		assertThat(semester.features()).isEmpty();

		semester.setFeatures(EnumSet.of(ProgramFeature.PEER_MENTORING, ProgramFeature.ISKUR));

		assertThat(semester.features()).containsExactly(ProgramFeature.PEER_MENTORING, ProgramFeature.ISKUR);
		assertThat(semester.has(ProgramFeature.OFFICE_HOURS)).isFalse();

		semester.setFeatures(EnumSet.of(ProgramFeature.OFFICE_HOURS));

		assertThat(semester.features()).containsExactly(ProgramFeature.OFFICE_HOURS);
	}

	@Test
	void theOlderTypesKeepWhatTheyAlwaysRanWhateverTheSwitches() {
		Program peer = program(ProgramType.PEER_MENTORING);
		peer.setFeatures(EnumSet.of(ProgramFeature.OFFICE_HOURS));
		Program officeHours = program(ProgramType.OFFICE_HOURS);
		Program startup = program(ProgramType.STARTUP_MENTORING);
		startup.setFeatures(EnumSet.allOf(ProgramFeature.class));

		assertThat(peer.features()).containsExactly(ProgramFeature.PEER_MENTORING, ProgramFeature.ISKUR);
		assertThat(officeHours.features()).containsExactly(ProgramFeature.OFFICE_HOURS);
		assertThat(startup.features()).isEmpty();
	}

	private static Program program(ProgramType type) {
		Program program = new Program();
		program.setProgramType(type);
		return program;
	}
}
