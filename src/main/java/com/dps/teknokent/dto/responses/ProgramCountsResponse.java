package com.dps.teknokent.dto.responses;

import org.jspecify.annotations.Nullable;

/**
 * Who takes part in a peer or Office Hours program, for its card and its row in the admin's list; a startup
 * program's numbers are the response's own team and mentor counts, so both parts are null there.
 */
public record ProgramCountsResponse(@Nullable Peer peer, @Nullable OfficeHours officeHours) {

	public static final ProgramCountsResponse NONE = new ProgramCountsResponse(null, null);

	/**
	 * {@code students}: approved accounts in the program; {@code peerMentors}: holders of its active gate badge;
	 * {@code openRequests}: requests to its peer mentors still open, waiting on the mentor or on the student's answer
	 * to
	 * a counter-proposal; {@code activeCategories}: the topic categories its coordinator switched on.
	 */
	public record Peer(int students, int peerMentors, int openRequests, int activeCategories) {
	}

	/**
	 * An Office Hours program spans the university: {@code academicians} are the active academician records the
	 * program's Akademisyenler list shows, signed in or not; {@code withAvailability} those of them whose account set
	 * weekly hours; {@code awaitingDecision} the requests the Randevular tab shows as waiting for the academician.
	 */
	public record OfficeHours(int academicians, int withAvailability, int awaitingDecision) {
	}
}
