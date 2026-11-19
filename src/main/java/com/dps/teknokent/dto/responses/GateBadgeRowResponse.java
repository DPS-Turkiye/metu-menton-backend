package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.BadgeSource;
import com.dps.teknokent.domain.GateBadgeState;
import com.dps.teknokent.domain.PeerTopicCategory;
import com.dps.teknokent.domain.PrismDecision;

/**
 * A person and where they stand with a program's gate badge, for staff: Katılımcılar's roster and the admin's
 * student list. {@code awardId} is the latest award (the one to revoke when ACTIVE); null when never awarded.
 * {@code training} shows how far someone without the badge got in the training ("Eğitimde").
 */
public record GateBadgeRowResponse(
		UUID userId,
		String fullName,
		String email,
		UUID programId,
		String programName,
		GateBadgeState state,
		@Nullable UUID awardId,
		@Nullable BadgeSource source,
		@Nullable Instant awardedAt,
		@Nullable Instant revokedAt,
		@Nullable String reason,
		Training training,
		@Nullable Profile profile,
		@Nullable Prism prism) {

	/**
	 * The person's Prism training in the program, for its coordinators and admins only: when they said they finished
	 * it, the latest decision and the score the Prism admin reported. Null when nothing was recorded, and on the
	 * admin's student list.
	 */
	public record Prism(@Nullable Instant completedAt, @Nullable PrismDecision decision, @Nullable Instant decidedAt,
			@Nullable String score) {
	}

	/**
	 * Katılımcılar's columns for a peer program: Bölüm · sınıf from the institution's records, and for a peer mentor
	 * the categories and the mentee load of their profile. Null on the admin's student list. {@code categories} is
	 * empty and {@code mentorProfile} false for someone who has not set up a peer mentor profile.
	 */
	public record Profile(@Nullable String department, @Nullable Integer classYear, boolean mentorProfile,
			List<PeerTopicCategory> categories, long activeMentees, @Nullable Integer menteeCapacity) {
	}

	/** The person's progress on the program's current training version (4T); NONE without a training. */
	public record Training(boolean videoWatched, int attempts, @Nullable Integer bestScore) {
		public static final Training NONE = new Training(false, 0, null);

		public boolean started() {
			return videoWatched || attempts > 0;
		}
	}
}
