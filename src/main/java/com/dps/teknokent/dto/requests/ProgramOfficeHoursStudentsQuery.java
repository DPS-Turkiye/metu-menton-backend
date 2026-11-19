package com.dps.teknokent.dto.requests;

/** Öğrenciler's filters: a name, the student's faculty, and only those with a request awaiting a decision. */
public record ProgramOfficeHoursStudentsQuery(
		String search,
		String faculty,
		boolean awaitingOnly,
		int page,
		int size) {
}
