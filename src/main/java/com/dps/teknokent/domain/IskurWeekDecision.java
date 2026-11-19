package com.dps.teknokent.domain;

/** The leader's answer to a sent week (4.1 "Onayla / Revizyon iste / Reddet"). */
public enum IskurWeekDecision {
	APPROVE,
	REVISION,
	REJECT;

	public IskurWeekStatus status() {
		return switch (this) {
		case APPROVE -> IskurWeekStatus.APPROVED;
		case REVISION -> IskurWeekStatus.REVISION_REQUESTED;
		case REJECT -> IskurWeekStatus.REJECTED;
		};
	}
}
