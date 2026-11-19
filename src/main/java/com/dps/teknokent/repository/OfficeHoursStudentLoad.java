package com.dps.teknokent.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * One student's office-hours requests in a window, counted by status: a row of an Office Hours program's Öğrenciler.
 */
public interface OfficeHoursStudentLoad {

	UUID getStudentId();

	Long getAwaitingDecision();

	Long getAlternativeProposed();

	Long getAccepted();

	Long getDeclined();

	Long getWithdrawn();

	Long getAcademicians();

	Instant getLastRequestedAt();
}
