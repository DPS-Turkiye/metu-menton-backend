package com.dps.teknokent.domain;

/**
 * Open work that stops a semester's feature from being switched off, or a person from leaving the semester; the
 * frontend ProgramBlockerKind mirrors it.
 */
public enum ProgramBlockerKind {
	/** A mentorship that is planned, running or paused. */
	ACTIVE_MENTORSHIP,
	/** A mentorship request still waiting on the mentor or on the student. */
	OPEN_REQUEST,
	/** A provisional or final İŞKUR placement. */
	ISKUR_PLACEMENT,
	/** An Office Hours meeting that has not been cancelled and has not started yet. */
	UPCOMING_OFFICE_HOURS_MEETING,
	/** A peer report (meeting, day or week) that was started or sent back and is not sent yet. */
	UNSENT_REPORT
}
