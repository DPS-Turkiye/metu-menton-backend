package com.dps.teknokent.security.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class InstitutionLoginUidsTest {

	@Test
	void aUidIsTheProviderThenThePersonsId() {
		InstitutionLoginProvider provider = mock(InstitutionLoginProvider.class);
		when(provider.id()).thenReturn("odtu-cas");

		assertThat(InstitutionLoginUids.of(provider, "e123456")).isEqualTo("odtu-cas_e123456");
	}

	// The dev stub's "odtu_" uids and ordinary Firebase uids are bound the usual ways.
	@Test
	void onlyOtgAndCasUidsAreBoundByTheirLoginAlone() {
		assertThat(InstitutionLoginUids.boundByLoginOnly("otg_a72ab40d")).isTrue();
		assertThat(InstitutionLoginUids.boundByLoginOnly("odtu-cas_e123456")).isTrue();
		assertThat(InstitutionLoginUids.boundByLoginOnly("odtu_dev-ogrenci-1")).isFalse();
		assertThat(InstitutionLoginUids.boundByLoginOnly("Zk3firebaseUid")).isFalse();
		assertThat(InstitutionLoginUids.boundByLoginOnly("otg")).isFalse();
	}
}
