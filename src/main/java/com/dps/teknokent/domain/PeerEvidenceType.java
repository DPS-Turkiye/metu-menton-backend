package com.dps.teknokent.domain;

/**
 * What a piece of evidence in the wizard's "Belgeler ve kanıtlar" step is. {@link #PORTFOLIO} is a link; every other
 * type is an uploaded file. The last four are the "Diğer materyaller" card's kinds.
 */
public enum PeerEvidenceType {
	TRANSCRIPT,
	CV,
	CERTIFICATE,
	PORTFOLIO,
	REFERENCE_LETTER,
	AWARD,
	VOLUNTEERING,
	PROJECT_REPORT;

	public boolean isLink() {
		return this == PORTFOLIO;
	}

	/** Whether the mentor may show it to students on their profile. A transcript is only ever for verification. */
	public boolean shareable() {
		return this != TRANSCRIPT;
	}
}
