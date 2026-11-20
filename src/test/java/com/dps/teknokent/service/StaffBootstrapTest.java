package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;

import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.repository.AppUserRepository;

class StaffBootstrapTest {

	private static final String ADMIN = "ADMIN:Yonetici@Metu.edu.tr:uidAdmin0000000000000000001";
	private static final String COORDINATOR = "coordinator:koordinator@metu.edu.tr:uidCoord0000000000000000001";

	private static final ApplicationArguments NO_ARGUMENTS = new DefaultApplicationArguments();

	private final AppUserRepository users = mock(AppUserRepository.class);

	private static AppUser existing(UserRole role, String email, String uid) {
		AppUser user = new AppUser();
		user.setId(UUID.randomUUID());
		user.setEmail(email);
		user.setFullName("Mevcut Kişi");
		user.setRole(role);
		user.setStatus(UserAccountStatus.PENDING);
		user.setFirebaseUid(uid);
		return user;
	}

	@Test
	void createsEachMissingStaffAccountApprovedAndLinkedByItsUid() {
		when(users.findByFirebaseUid(any())).thenReturn(Optional.empty());
		when(users.existsByEmailIgnoreCase(any())).thenReturn(false);

		new StaffBootstrap(users, ADMIN + ", " + COORDINATOR + ",").run(NO_ARGUMENTS);

		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(users, times(2)).save(saved.capture());
		AppUser admin = saved.getAllValues().get(0);
		AppUser coordinator = saved.getAllValues().get(1);
		assertThat(admin.getEmail()).isEqualTo("yonetici@metu.edu.tr");
		assertThat(admin.getFirebaseUid()).isEqualTo("uidAdmin0000000000000000001");
		assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(admin.has(UserRole.ADMIN)).isTrue();
		assertThat(admin.getStatus()).isEqualTo(UserAccountStatus.APPROVED);
		assertThat(admin.getFullName()).isEqualTo("Platform Yöneticisi");
		assertThat(coordinator.getRole()).isEqualTo(UserRole.COORDINATOR);
		assertThat(coordinator.getFullName()).isEqualTo("Program Koordinatörü");
		assertThat(coordinator.getFirebaseUid()).isEqualTo("uidCoord0000000000000000001");
	}

	@Test
	void leavesARowThatAlreadyHasTheUidExactlyAsItIs() {
		AppUser mentee = existing(UserRole.MENTEE, "someone.else@metu.edu.tr", "uidAdmin0000000000000000001");
		when(users.findByFirebaseUid("uidAdmin0000000000000000001")).thenReturn(Optional.of(mentee));

		new StaffBootstrap(users, ADMIN).run(NO_ARGUMENTS);

		verify(users, never()).save(any());
		assertThat(mentee.getRole()).isEqualTo(UserRole.MENTEE);
		assertThat(mentee.has(UserRole.ADMIN)).isFalse();
		assertThat(mentee.getStatus()).isEqualTo(UserAccountStatus.PENDING);
		assertThat(mentee.getEmail()).isEqualTo("someone.else@metu.edu.tr");
	}

	@Test
	void neverPromotesOrRelinksARowThatAlreadyHasTheAddress() {
		AppUser mentee = existing(UserRole.MENTEE, "yonetici@metu.edu.tr", "anotherUid");
		when(users.findByFirebaseUid("uidAdmin0000000000000000001")).thenReturn(Optional.empty());
		when(users.existsByEmailIgnoreCase("yonetici@metu.edu.tr")).thenReturn(true);

		new StaffBootstrap(users, ADMIN).run(NO_ARGUMENTS);

		verify(users, never()).save(any());
		assertThat(mentee.getRole()).isEqualTo(UserRole.MENTEE);
		assertThat(mentee.getFirebaseUid()).isEqualTo("anotherUid");
		assertThat(mentee.getStatus()).isEqualTo(UserAccountStatus.PENDING);
	}

	@Test
	void createsOnlyTheEntriesThatHaveNoRowYet() {
		when(users.findByFirebaseUid("uidAdmin0000000000000000001"))
				.thenReturn(
						Optional.of(existing(UserRole.ADMIN, "yonetici@metu.edu.tr", "uidAdmin0000000000000000001")));
		when(users.findByFirebaseUid("uidCoord0000000000000000001")).thenReturn(Optional.empty());
		when(users.existsByEmailIgnoreCase("koordinator@metu.edu.tr")).thenReturn(false);

		new StaffBootstrap(users, ADMIN + "," + COORDINATOR).run(NO_ARGUMENTS);

		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(users).save(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("koordinator@metu.edu.tr");
		assertThat(saved.getValue().getRole()).isEqualTo(UserRole.COORDINATOR);
	}

	@Test
	void stopsTheStartupOnAMalformedEntryWithNothingCreated() {
		List<String> malformed = List.of(
				ADMIN + ",COORDINATOR:koordinator@metu.edu.tr",
				ADMIN + ",MENTOR:mentor@metu.edu.tr:uidMentor",
				ADMIN + ",ADMIN:not-an-address:uidOther",
				ADMIN + ",ADMIN:other@metu.edu.tr:uid with space",
				ADMIN + ",ADMIN:yonetici@metu.edu.tr:uidOther",
				ADMIN + ",COORDINATOR:other@metu.edu.tr:uidAdmin0000000000000000001",
				ADMIN + ",COORDINATOR:other@metu.edu.tr:otg_1234");

		for (String staff : malformed) {
			assertThatThrownBy(() -> new StaffBootstrap(users, staff))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageStartingWith("APP_BOOTSTRAP_STAFF entry 2 ")
					.hasMessageEndingWith("; nothing was created.")
					.hasMessageNotContaining("@");
		}
		verifyNoInteractions(users);
	}

	@Test
	void namesWhatIsWrongWithAnEntry() {
		assertThatThrownBy(() -> new StaffBootstrap(users, "ADMIN:a@metu.edu.tr"))
				.hasMessage("APP_BOOTSTRAP_STAFF entry 1 must be ROLE:email:firebaseUid; nothing was created.");
		assertThatThrownBy(() -> new StaffBootstrap(users, "MENTEE:a@metu.edu.tr:uid1"))
				.hasMessageContaining("entry 1 must start with ADMIN or COORDINATOR");
		assertThatThrownBy(() -> new StaffBootstrap(users, "OWNER:a@metu.edu.tr:uid1"))
				.hasMessageContaining("entry 1 must start with ADMIN or COORDINATOR");
		assertThatThrownBy(() -> new StaffBootstrap(users, "ADMIN:a@metu.edu.tr:"))
				.hasMessageContaining("entry 1 needs a Firebase uid");
		assertThatThrownBy(() -> new StaffBootstrap(users, "ADMIN:a@metu.edu.tr:uid1,ADMIN:b@metu.edu.tr:uid1"))
				.hasMessageContaining("entry 2 repeats the e-mail address or uid of an earlier entry");
		assertThatThrownBy(() -> new StaffBootstrap(users, "COORDINATOR:a@metu.edu.tr:otg_1234"))
				.hasMessageContaining("entry 1 has an ODTÜ-login uid");
		assertThatThrownBy(() -> new StaffBootstrap(users, "COORDINATOR:a@metu.edu.tr:odtu-cas_e123456"))
				.hasMessageContaining("entry 1 has an ODTÜ-login uid");
	}

	@Test
	void doesNothingWhenNoStaffIsConfigured() {
		new StaffBootstrap(users, "").run(NO_ARGUMENTS);
		new StaffBootstrap(users, " , ").run(NO_ARGUMENTS);

		verifyNoInteractions(users);
	}
}
