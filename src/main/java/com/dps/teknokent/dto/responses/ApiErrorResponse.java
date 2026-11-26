package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.ProgramBlocker;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code code} is a stable, machine-readable reason a client may branch on; most errors have none and omit it.
 * {@code blockers} lists the open work behind a {@code PROGRAM_WORK_OPEN} refusal; every other error omits it.
 */
public record ApiErrorResponse(
		Instant timestamp,
		int status,
		String error,
		@Nullable String message,
		String path,
		Map<String, String> fieldErrors,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String code,
		@JsonInclude(JsonInclude.Include.NON_EMPTY) List<ProgramBlocker> blockers) {

	public ApiErrorResponse(
			Instant timestamp, int status, String error, @Nullable String message, String path,
			Map<String, String> fieldErrors, @Nullable String code) {
		this(timestamp, status, error, message, path, fieldErrors, code, List.of());
	}

	public ApiErrorResponse(
			Instant timestamp, int status, String error, @Nullable String message, String path,
			Map<String, String> fieldErrors) {
		this(timestamp, status, error, message, path, fieldErrors, null);
	}
}
