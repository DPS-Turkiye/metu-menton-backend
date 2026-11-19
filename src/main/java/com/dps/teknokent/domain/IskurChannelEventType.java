package com.dps.teknokent.domain;

/** What changed in an İŞKUR channel, as pushed to the people following it. */
public enum IskurChannelEventType {
	/** A new message; the channel's unread counts changed with it. */
	MESSAGE_POSTED,
	/** Someone confirmed an important announcement ("Okudum"); its receipt tally changed. */
	MESSAGE_ACKNOWLEDGED
}
