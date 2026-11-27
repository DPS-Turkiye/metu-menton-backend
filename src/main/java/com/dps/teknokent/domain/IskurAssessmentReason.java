package com.dps.teknokent.domain;

/**
 * The reason an intern picks for their statement; the free text says the rest. Neutral on purpose: nobody is asked
 * for health or other private detail. NO_MENTORING: peer mentoring is optional, so "I don't want to mentor" is a
 * valid answer that usually leads to no mentoring time at all.
 */
public enum IskurAssessmentReason {
	NOT_STARTED,
	FAILED,
	NO_ACCESS,
	PERSONAL,
	NO_MENTORING,
	OTHER
}
