package com.dps.teknokent.domain;

/** One step in a week's history: the intern sends it or objects, a leader or coordinator decides. */
public enum IskurWeekEventKind {
	SUBMITTED,
	APPROVED,
	REVISION,
	REJECTED,
	OBJECTION;

	public static IskurWeekEventKind of(IskurWeekDecision decision) {
		return switch (decision) {
		case APPROVE -> APPROVED;
		case REVISION -> REVISION;
		case REJECT -> REJECTED;
		};
	}
}
