package com.dps.teknokent.dto.responses;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** A program's support-activity pool, in the order the coordinators keep it. */
public record IskurSupportActivitiesResponse(UUID programId, List<Activity> activities) {

	public record Activity(UUID id, String title, @Nullable String description, boolean active, int sortOrder) {
	}
}
