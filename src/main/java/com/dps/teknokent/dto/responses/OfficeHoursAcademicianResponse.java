package com.dps.teknokent.dto.responses;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * An academician a Menton student may ask for office hours: only those who chose to be listed. Name, title,
 * department and faculty come from ODTÜ's record; the rest is what they set. Where to meet and the online link are
 * not here: a student sees them only on their own request, once it is a meeting. Nor are their e-mail choices.
 *
 * @param id             their office-hours id, used to ask for their slots and to send a request
 * @param accountId      their Menton account, so a student's screen can tell which entry is their advisor
 * @param meetingMinutes their usual meeting length
 * @param contactEmail   only when they filled it in
 * @param responseDays   how many days they usually take to answer; empty when they did not say
 */
public record OfficeHoursAcademicianResponse(
		UUID id,
		UUID accountId,
		String fullName,
		String academicTitle,
		String department,
		String faculty,
		@Nullable String contactEmail,
		int meetingMinutes,
		@Nullable String about,
		List<OfficeHoursProfileResponse.Topic> topics,
		List<OfficeHoursProfileResponse.Course> courses,
		List<String> expertise,
		@Nullable Integer responseDays) {
}
