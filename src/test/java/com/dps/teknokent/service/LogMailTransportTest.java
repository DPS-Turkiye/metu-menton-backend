package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LogMailTransportTest {

	@Test
	void logsTheSubjectAndTheRecipientsDomainButNeverTheTextOrTheAddress(CapturedOutput output) {
		boolean sent = new LogMailTransport().send("", "student@example.test", "Görüşme onaylandı",
				"Merhaba Deniz, kodunuz 4821.");

		assertThat(sent).isTrue();
		assertThat(output).contains("\"Görüşme onaylandı\" to an address at example.test")
				.doesNotContain("student@")
				.doesNotContain("4821");
	}
}
