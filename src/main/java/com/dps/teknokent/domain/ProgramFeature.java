package com.dps.teknokent.domain;

import java.util.Set;

/**
 * What an ODTÜ semester runs (Hocam, 26 Nov): Akran Mentörlüğü, Office Hours and İŞKUR, switched on or off per
 * semester. İŞKUR builds on peer mentoring, so it cannot run without it.
 */
public enum ProgramFeature {
	PEER_MENTORING,
	OFFICE_HOURS,
	ISKUR;

	/** The error code of a refused feature set, for the frontend to branch on. */
	public static final String INVALID_SET_CODE = "PROGRAM_FEATURES_INVALID";

	/** A semester runs at least one feature, and İŞKUR only next to peer mentoring. */
	public static boolean validSet(Set<ProgramFeature> features) {
		return !features.isEmpty() && (!features.contains(ISKUR) || features.contains(PEER_MENTORING));
	}
}
