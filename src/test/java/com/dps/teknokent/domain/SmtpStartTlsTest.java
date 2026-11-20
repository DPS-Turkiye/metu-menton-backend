package com.dps.teknokent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SmtpStartTlsTest {

	@Test
	void parsesTheModeWhateverItsCase() {
		assertThat(SmtpStartTls.parse("optional")).isEqualTo(SmtpStartTls.OPTIONAL);
		assertThat(SmtpStartTls.parse(" OFF ")).isEqualTo(SmtpStartTls.OFF);
	}

	@Test
	void nothingSetMeansEncryptionIsRequired() {
		assertThat(SmtpStartTls.parse(null)).isEqualTo(SmtpStartTls.REQUIRED);
		assertThat(SmtpStartTls.parse("")).isEqualTo(SmtpStartTls.REQUIRED);
	}

	@Test
	void refusesAModeItDoesNotKnow() {
		assertThatThrownBy(() -> SmtpStartTls.parse("ssl"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SMTP_STARTTLS");
	}
}
