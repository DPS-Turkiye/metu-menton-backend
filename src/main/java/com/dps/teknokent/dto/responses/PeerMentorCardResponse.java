package com.dps.teknokent.dto.responses;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.PeerTopicCategory;

/** A peer mentor as a student of the same program sees them (frontend PeerMentorCard). */
public record PeerMentorCardResponse(
		UUID mentorId,
		String fullName,
		@Nullable String department,
		Integer classYear,
		List<PeerTopicCategory> categories,
		String helpWith,
		List<String> recommendationReasons,
		boolean capacityFull,
		/* Off while the mentor takes no new requests ("Şu an yeni mentee almıyor"). */
		boolean acceptingRequests,
		/* Active badges, as anyone may see them; a card shows the achievements. */
		List<PublicBadgeResponse> badges,
		/* A free slot in the coming week ("Bu hafta müsait"). */
		boolean availableThisWeek) {
}
