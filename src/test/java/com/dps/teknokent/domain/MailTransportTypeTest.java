package com.dps.teknokent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MailTransportTypeTest {

	@ParameterizedTest
	@ValueSource(strings = { "smtp", "SMTP", " Smtp " })
	void parsesTheTransportWhateverItsCase(String value) {
		assertThat(MailTransportType.parse(value)).isEqualTo(MailTransportType.SMTP);
	}

	@Test
	void nothingSetMeansSesAsBeforeTheSettingExisted() {
		assertThat(MailTransportType.parse(null)).isEqualTo(MailTransportType.SES);
		assertThat(MailTransportType.parse("  ")).isEqualTo(MailTransportType.SES);
	}

	@Test
	void refusesATransportItDoesNotKnow() {
		assertThatThrownBy(() -> MailTransportType.parse("sendmail"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("APP_MAIL_TRANSPORT")
				.hasMessageContaining("sendmail");
	}
}
