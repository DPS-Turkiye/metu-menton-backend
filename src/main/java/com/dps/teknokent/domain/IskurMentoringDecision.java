package com.dps.teknokent.domain;

/**
 * What the coordinator does with the intern's weekly mentoring time when answering a statement: leave it, set a value
 * (0 puts the whole week into team work), or go back to the default share.
 */
public enum IskurMentoringDecision {
	KEEP,
	SET,
	DEFAULT
}
