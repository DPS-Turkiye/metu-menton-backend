package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.PeerMeetingFormat;
import com.dps.teknokent.domain.PeerMeetingType;
import com.dps.teknokent.domain.PeerReportStatus;

/**
 * A held peer meeting as the reports list it: type, format and duration are the report's when there is one, else
 * what the calendar says. {@code durationMinutes} is everything the meeting counts for as mentoring work;
 * {@code meetingMinutes}, {@code preparationMinutes} and {@code followUpMinutes} are its parts, so nobody has to read
 * the total as the meeting's length. {@code reportStatus} is null while no report was started.
 */
public record PeerReportMeetingResponse(
		UUID meetingId,
		UUID menteeId,
		String menteeName,
		Instant scheduledAt,
		int durationMinutes,
		int meetingMinutes,
		int preparationMinutes,
		int followUpMinutes,
		@Nullable PeerMeetingType meetingType,
		@Nullable PeerMeetingFormat meetingFormat,
		@Nullable PeerReportStatus reportStatus) {
}
