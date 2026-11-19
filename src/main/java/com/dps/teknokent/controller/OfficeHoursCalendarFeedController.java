package com.dps.teknokent.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.domain.AppLanguage;
import com.dps.teknokent.dto.responses.OfficeHoursCalendarFeedResponse;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.service.OfficeHoursCalendarFeedService;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Calendar links for Moodle (FR-OH-23). Like the rest of office hours only the frontend's server calls this: it
 * serves the feed on its own public address, where Moodle fetches it, and passes the token on.
 */
@RestController
@RequestMapping("/api/office-hours")
@Tag(name = "Office hours")
public class OfficeHoursCalendarFeedController {

	static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

	private final OfficeHoursCalendarFeedService feedService;
	private final boolean enabled;

	public OfficeHoursCalendarFeedController(OfficeHoursCalendarFeedService feedService,
			@Value("${app.office-hours.calendar-feed.enabled:false}") boolean enabled) {
		this.feedService = feedService;
		this.enabled = enabled;
	}

	@GetMapping("/users/{userId}/calendar-feed")
	public OfficeHoursCalendarFeedResponse personalFeed(@PathVariable UUID userId) {
		requireEnabled();
		return feedService.personalFeed(userId);
	}

	@PostMapping("/users/{userId}/calendar-feed/reset")
	public OfficeHoursCalendarFeedResponse resetPersonalFeed(@PathVariable UUID userId) {
		requireEnabled();
		return feedService.resetPersonalFeed(userId);
	}

	@GetMapping("/users/{userId}/courses/{courseId}/calendar-feed")
	public OfficeHoursCalendarFeedResponse courseFeed(@PathVariable UUID userId, @PathVariable UUID courseId) {
		requireEnabled();
		return feedService.courseFeed(userId, courseId);
	}

	/** 403 for a student: the whole course shares this link. */
	@PostMapping("/users/{userId}/courses/{courseId}/calendar-feed/reset")
	public OfficeHoursCalendarFeedResponse resetCourseFeed(@PathVariable UUID userId, @PathVariable UUID courseId) {
		requireEnabled();
		return feedService.resetCourseFeed(userId, courseId);
	}

	/**
	 * The iCalendar feed behind a link, in Turkish unless {@code lang=en}: Moodle sends no language of its own, so
	 * the link carries the one its owner reads. 404 for an unknown or reset link.
	 */
	@GetMapping("/calendar-feeds/{token}")
	public ResponseEntity<String> feed(@PathVariable String token,
			@RequestParam(name = "lang", required = false) @Nullable String lang) {
		requireEnabled();
		AppLanguage language = AppLanguage.TR;
		if (AppLanguage.EN.code().equals(lang)) {
			language = AppLanguage.EN;
		}
		String calendar = feedService.feed(token, language)
				.orElseThrow(() -> new ResourceNotFoundException("Calendar not found."));
		// Always fresh: a cancelled or moved meeting must not linger in a cache between Moodle's fetches.
		return ResponseEntity.ok().contentType(TEXT_CALENDAR).cacheControl(CacheControl.noStore()).body(calendar);
	}

	/** A deployment that has not turned the links on shows no sign of them: the routes look absent. */
	private void requireEnabled() {
		if (!enabled) {
			throw new ResourceNotFoundException("Calendar not found.");
		}
	}
}
