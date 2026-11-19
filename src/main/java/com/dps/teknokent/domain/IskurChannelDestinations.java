package com.dps.teknokent.domain;

import java.util.UUID;

/** Where the İŞKUR channels push their live events. Clients only listen here; posting stays on the REST API. */
public final class IskurChannelDestinations {

	/** Everything İŞKUR pushes. No destination under it accepts a client's SEND. */
	public static final String PREFIX = MeetingRoomDestinations.BROKER_PREFIX + "/iskur/";

	public static final String CHANNEL_PREFIX = PREFIX + "channels/";

	private IskurChannelDestinations() {
	}

	public static String channel(UUID channelId) {
		return CHANNEL_PREFIX + channelId;
	}
}
