package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dps.teknokent.domain.IskurTaskStatus;

/**
 * The files handed in with a program's İŞKUR tasks, as its coordinators read them: one row per task that has any, the
 * task with the newest file first. {@code enabled} is false while file storage is not set up.
 */
public record IskurTaskOutputsResponse(UUID programId, boolean enabled, List<Task> tasks) {

	public record Task(
			UUID taskId,
			String title,
			String teamName,
			IskurTaskResponse.Person assignee,
			IskurTaskStatus status,
			LocalDate dueDate,
			List<File> files) {
	}

	public record File(UUID id, String fileName, String contentType, long sizeBytes, Instant uploadedAt) {
	}
}
