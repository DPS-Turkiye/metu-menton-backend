package com.dps.teknokent.dto.responses;

/**
 * The secret part of a calendar link. The frontend serves the feed on its own public address, so it builds the
 * URL Moodle subscribes to from this token.
 */
public record OfficeHoursCalendarFeedResponse(String token) {
}
