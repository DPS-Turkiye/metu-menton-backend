package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dps.teknokent.domain.AppLanguage;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.OfficeHoursUser;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.service.OfficeHoursMailService.Event;
import com.dps.teknokent.util.Texts;

@ExtendWith(MockitoExtension.class)
class OfficeHoursInAppNotificationsTest {

	@Mock
	private NotificationService notificationService;
	@Mock
	private AppUserRepository appUserRepository;
	@InjectMocks
	private OfficeHoursInAppNotifications notifications;

	private final OfficeHoursUser student = person("Ayşe Yılmaz");
	private final OfficeHoursUser academic = person("Ali Kaya");

	private static OfficeHoursUser person(String name) {
		OfficeHoursUser user = new OfficeHoursUser();
		user.setId(UUID.randomUUID());
		user.setFullName(name);
		return user;
	}

	private AppUser linkedAccount(OfficeHoursUser person, AppLanguage language, UserAccountStatus status) {
		AppUser account = new AppUser();
		account.setId(UUID.randomUUID());
		account.setPreferredLanguage(language);
		account.setStatus(status);
		person.setAppUserId(account.getId());
		when(appUserRepository.findById(account.getId())).thenReturn(Optional.of(account));
		return account;
	}

	@Test
	void theAcademicHearsOfANewRequestInTheirLanguageWithALinkToTheirRandevularim() {
		AppUser account = linkedAccount(academic, AppLanguage.EN, UserAccountStatus.APPROVED);

		notifications.requestChanged(academic, student, "newRequest", "Ödev 2", true);

		verify(notificationService).create(account, NotificationCategory.MEETING_CHANGE, "New office hours request",
				"Ayşe Yılmaz asked you for office hours. Topic: Ödev 2", "/academician/appointments");
	}

	@Test
	void theStudentHearsOfACancelInTurkishWithALinkToTheirRandevularim() {
		AppUser account = linkedAccount(student, AppLanguage.TR, UserAccountStatus.APPROVED);

		notifications.requestChanged(student, academic, "meetingCancelled", "Ödev 2", false);

		verify(notificationService).create(account, NotificationCategory.MEETING_CHANGE,
				"Ofis saati görüşmesi iptal edildi", "Ali Kaya görüşmenizi iptal etti. Konu: Ödev 2",
				"/student/appointments");
	}

	@Test
	void aMoodleOnlyPersonGetsNothingInApp() {
		notifications.requestChanged(student, academic, "accepted", "Ödev 2", false);

		verify(notificationService, never()).create(any(), any(), anyString(), anyString(), anyString());
	}

	@Test
	void aDeletedAccountGetsNothing() {
		linkedAccount(student, AppLanguage.TR, UserAccountStatus.DELETED);

		notifications.requestChanged(student, academic, "accepted", "Ödev 2", false);

		verify(notificationService, never()).create(any(), any(), anyString(), anyString(), anyString());
	}

	@Test
	void nothingGoesOutWhenEitherSideIsGone() {
		notifications.requestChanged(null, academic, "accepted", "Ödev 2", false);
		notifications.requestChanged(student, null, "accepted", "Ödev 2", false);

		verify(notificationService, never()).create(any(), any(), anyString(), anyString(), anyString());
	}

	@Test
	void aLongNameAndTopicAreClippedToTheColumnsInsteadOfFailingTheStep() {
		linkedAccount(student, AppLanguage.TR, UserAccountStatus.APPROVED);
		academic.setFullName("A".repeat(300));

		notifications.requestChanged(student, academic, "declined", "K".repeat(300), false);

		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(notificationService).create(any(), eq(NotificationCategory.MEETING_CHANGE), anyString(),
				body.capture(), anyString());
		assertThat(body.getValue()).hasSize(OfficeHoursInAppNotifications.BODY_MAX).endsWith("…");
	}

	@Test
	void everyStepHasAShortTitleAndABodyInBothLanguages() {
		for (Event event : Event.values()) {
			for (AppLanguage language : AppLanguage.values()) {
				String key = "officeHours.notification." + event.key;
				assertThat(Texts.get(language, key + ".title")).as(key + " in " + language).isNotBlank()
						.hasSizeLessThanOrEqualTo(OfficeHoursInAppNotifications.TITLE_MAX);
				assertThat(Texts.get(language, key + ".body", "Ali Kaya", "Ödev 2")).as(key + " in " + language)
						.contains("Ali Kaya").contains("Ödev 2");
			}
		}
	}
}
