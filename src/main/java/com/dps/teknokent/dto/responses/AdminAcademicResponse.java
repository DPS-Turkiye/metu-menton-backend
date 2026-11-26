package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * An academician from the institution's records, for Admin › Kullanıcılar › Akademisyenler. Read-only: the
 * institution vouches for them, so nobody approves them by hand. {@code userId} stays null until they first sign in.
 */
public record AdminAcademicResponse(
		UUID personId,
		@Nullable UUID userId,
		String fullName,
		@Nullable String email,
		String academicTitle,
		String faculty,
		String departmentCode,
		String departmentName,
		boolean active,
		boolean signedIn,
		@Nullable Instant lastLoginAt,
		long adviseeCount,
		@Nullable OfficeHours officeHours) {

	/**
	 * For the admin only (FR-OH-30): whether they have office hours set (a weekly range, or one-off hours not over
	 * yet), and their meetings this month on Istanbul's calendar, cancelled ones left out. Someone with no office-hours
	 * person yet has none of either. A program's coordinators get null.
	 */
	public record OfficeHours(boolean hoursDefined, long meetingsThisMonth) {
	}
}
