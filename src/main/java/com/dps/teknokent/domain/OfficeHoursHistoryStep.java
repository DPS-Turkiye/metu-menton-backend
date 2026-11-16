package com.dps.teknokent.domain;

/**
 * A step in an office-hours request's history as a coordinator sees it. The steps are read from the timestamps the
 * request already keeps (made, messages, meeting booked, last change); there is no separate event log.
 */
public enum OfficeHoursHistoryStep {
	SUBMITTED,
	INFO_REQUESTED,
	STUDENT_REPLIED,
	ALTERNATIVE_PROPOSED,
	ACCEPTED,
	DECLINED,
	WITHDRAWN
}
