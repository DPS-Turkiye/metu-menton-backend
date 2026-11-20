package com.dps.teknokent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SmtpTlsTest {

	@Test
	void parsesTheModeWhateverItsCase() {
		assertThat(SmtpTls.parse(" Implicit ")).isEqualTo(SmtpTls.IMPLICIT);
		assertThat(SmtpTls.parse("starttls")).isEqualTo(SmtpTls.STARTTLS);
	}

	@Test
	void nothingSetMeansStartTls() {
		assertThat(SmtpTls.parse(null)).isEqualTo(SmtpTls.STARTTLS);
		assertThat(SmtpTls.parse("")).isEqualTo(SmtpTls.STARTTLS);
	}

	@Test
	void refusesAModeItDoesNotKnow() {
		assertThatThrownBy(() -> SmtpTls.parse("ssl"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SMTP_TLS");
	}
}
