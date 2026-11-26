package com.dps.teknokent.domain;

/**
 * The İŞKUR channels of rules frame 322:3413 §4, in the order the Mesajlar list shows them: Genel Duyurular (the whole
 * program; only coordinators post), a unit's channel, the unit's Liderler odası (its team leaders and their unit
 * coordinator, student handbook p.17), a thematic team's channel, and a member's 1:1 with their leader.
 */
public enum IskurChannelKind {
	ANNOUNCEMENTS,
	UNIT,
	LEADERS,
	TEAM,
	LEADER_DIRECT;

	/** Genel Duyurular always notifies; the other kinds can be muted per person. */
	public boolean mutable() {
		return this != ANNOUNCEMENTS;
	}
}
