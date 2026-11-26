package com.dps.teknokent.domain;

/**
 * Decides which structures a program has. Teknokent runs startup mentoring (teams and matching). An ODTÜ program is
 * a {@link #SEMESTER} whose {@link ProgramFeature}s are switched on one by one; {@link #PEER_MENTORING} and
 * {@link #OFFICE_HOURS} are the single-purpose types ODTÜ used before semesters and are no longer created.
 */
public enum ProgramType {
	STARTUP_MENTORING,
	PEER_MENTORING,
	OFFICE_HOURS,
	SEMESTER
}
