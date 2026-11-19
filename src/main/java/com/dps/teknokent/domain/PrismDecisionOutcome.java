package com.dps.teknokent.domain;

/**
 * What a bulk Prism decision did for one address. NOT_FOUND also covers an account outside the program, so the list
 * does not tell a coordinator whether an address has an account elsewhere. ALREADY_MENTOR changes nothing: taking a
 * badge back stays with "Rozeti geri al".
 */
public enum PrismDecisionOutcome {
	APPROVED,
	RETAKE,
	ALREADY_MENTOR,
	NOT_FOUND,
	NOT_ELIGIBLE
}
