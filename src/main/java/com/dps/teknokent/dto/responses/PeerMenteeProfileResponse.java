package com.dps.teknokent.dto.responses;

/**
 * A mentee as the peer mentor they asked sees them, and no more than Profilim tells students it shares: their name and
 * department, and their interests and goal.
 */
public record PeerMenteeProfileResponse(PeerMentoringRequestResponse.Person mentee,
		AdviseeProfileResponse.Interests interests, String goal) {
}
