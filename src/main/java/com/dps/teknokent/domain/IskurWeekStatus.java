package com.dps.teknokent.domain;

/** Where an intern's weekly log stands with their leader. A draft or a week sent back can still change. */
public enum IskurWeekStatus {
	DRAFT,
	SUBMITTED,
	APPROVED,
	REVISION_REQUESTED,
	REJECTED;

	public boolean editable() {
		return this == DRAFT || this == REVISION_REQUESTED;
	}
}
