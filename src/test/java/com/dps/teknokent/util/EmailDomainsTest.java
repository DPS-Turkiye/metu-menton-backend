package com.dps.teknokent.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailDomainsTest {

	@Test
	void aLogLineGetsTheDomainAndNeverTheAddress() {
		assertThat(EmailDomains.domainOf("Student@METU.edu.tr ")).isEqualTo("metu.edu.tr");
		assertThat(EmailDomains.domainOf("odd@name@example.test")).isEqualTo("example.test");
	}

	@Test
	void whatIsNotAnAddressHasNoDomainToLog() {
		assertThat(EmailDomains.domainOf(null)).isEqualTo("?");
		assertThat(EmailDomains.domainOf("no-at-sign")).isEqualTo("?");
		assertThat(EmailDomains.domainOf("student@")).isEqualTo("?");
	}
}
