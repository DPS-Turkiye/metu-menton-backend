package com.dps.teknokent.service;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.OfficeHoursUser;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;

/**
 * The in-app side of each office hours step (FR-OH-17): the person who did not act gets a notification in Menton,
 * written in the step's own transaction, so a step that rolls back leaves none (push goes out after the commit). Only
 * a person linked to a Menton account has an inbox; a Moodle-only person hears by e-mail until FR-OH-12 brings
 * notifications into the course.
 */
@Service
@RequiredArgsConstructor
public class OfficeHoursInAppNotifications {

	/** The notification table's column sizes; a long name or topic is clipped instead of failing the step. */
	static final int TITLE_MAX = 160;
	static final int BODY_MAX = 500;

	private final NotificationService notificationService;
	private final AppUserRepository appUserRepository;

	/**
	 * Notifies {@code to} that {@code from} moved the request on; {@code event} is the step's text key, the same one
	 * its e-mail uses.
	 */
	public void requestChanged(@Nullable OfficeHoursUser to, @Nullable OfficeHoursUser from, String event,
			String topic, boolean toInstructor) {
		if (to == null || from == null) {
			return;
		}
		Optional<AppUser> account = account(to.getAppUserId());
		if (account.isEmpty()) {
			return;
		}
		AppUser reader = account.get();
		String key = "officeHours.notification." + event;
		notificationService.create(reader, NotificationCategory.MEETING_CHANGE,
				TextUtils.clip(Texts.get(reader, key + ".title"), TITLE_MAX),
				TextUtils.clip(Texts.get(reader, key + ".body", from.getFullName(), topic), BODY_MAX),
				page(toInstructor));
	}

	private Optional<AppUser> account(@Nullable UUID appUserId) {
		if (appUserId == null) {
			return Optional.empty();
		}
		return appUserRepository.findById(appUserId).filter(found -> found.getStatus() != UserAccountStatus.DELETED);
	}

	/** Where the step shows up in Menton: the academician's or the student's Randevularım. */
	static String page(boolean instructor) {
		if (instructor) {
			return "/academician/appointments";
		}
		return "/student/appointments";
	}
}
