package com.dps.teknokent.dto.responses;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.OfficeHoursRequestStatus;

/**
 * What a change to the academician's hours would leave outside them, before it is saved (FR-OH-29). Only what
 * they need to decide: who, the request's title and the times. The student's note and the messages are not in it.
 * Each item's request id and status go back in the save's confirmedAffected.
 */
public record OfficeHoursAvailabilityImpactResponse(List<Meeting> meetings, List<Request> requests) {

	public static OfficeHoursAvailabilityImpactResponse empty() {
		return new OfficeHoursAvailabilityImpactResponse(List.of(), List.of());
	}

	/** A booked meeting the new hours no longer cover. It stays booked unless the academician cancels it. */
	public record Meeting(UUID requestId, OfficeHoursRequestStatus status, OfficeHoursTimeResponse time,
			@Nullable String studentName, @Nullable String topic, @Nullable String courseName) {
	}

	/** An open request some of whose times the new hours no longer have. */
	public record Request(UUID requestId, OfficeHoursRequestStatus status, @Nullable String studentName,
			String topic, @Nullable String courseName, List<OfficeHoursTimeResponse> lostTimes,
			List<OfficeHoursTimeResponse> keptTimes) {
	}
}
