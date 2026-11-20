package com.dps.teknokent.config;

import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * Hands each inbound frame to the feature that owns its destination. Everything the İŞKUR topics do not own goes to
 * the meeting rooms' check, which refuses any destination it does not know: a new destination is closed until an
 * interceptor claims it.
 */
@RequiredArgsConstructor
public class RealtimeAccessRouter implements ChannelInterceptor {

	private final IskurChannelAccessInterceptor iskurChannels;
	private final MeetingRoomAccessInterceptor meetingRooms;

	@Override
	public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
		// Always asked first: it also tidies its subscriptions on UNSUBSCRIBE and DISCONNECT.
		Message<?> checked = iskurChannels.preSend(message, channel);
		return IskurChannelAccessInterceptor.owns(message) ? checked : meetingRooms.preSend(message, channel);
	}
}
