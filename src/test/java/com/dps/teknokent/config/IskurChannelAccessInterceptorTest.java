package com.dps.teknokent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.dps.teknokent.service.IskurChannelRealtimeAccessService;
import com.dps.teknokent.service.RealtimeTokenService.RealtimeIdentity;

@ExtendWith(MockitoExtension.class)
class IskurChannelAccessInterceptorTest {

	private static final String SESSION = "session-1";
	private static final String SUBSCRIPTION = "sub-0";

	@Mock
	private IskurChannelRealtimeAccessService access;

	private final UUID channelId = UUID.fromString("00000000-0000-0000-0000-000000000c01");
	private final UUID otherChannelId = UUID.fromString("00000000-0000-0000-0000-000000000c02");
	private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000201");
	private final Instant connectedAt = Instant.parse("2026-11-20T09:00:00Z");
	private final MutableClock clock = new MutableClock(connectedAt.plusSeconds(1));

	@Test
	void letsSomeoneWhoReachesTheChannelSubscribe() {
		when(access.mayFollow(userId, channelId, connectedAt)).thenReturn(true);
		Message<?> subscribe = subscribe(channelId, SUBSCRIPTION, attributes());

		assertThat(interceptor().preSend(subscribe, null)).isSameAs(subscribe);
	}

	@Test
	void refusesSomeoneWhoDoesNotReachTheChannel() {
		when(access.mayFollow(userId, channelId, connectedAt)).thenReturn(false);

		assertThatThrownBy(() -> interceptor().preSend(subscribe(channelId, SUBSCRIPTION, attributes()), null))
				.isInstanceOf(MessagingException.class);
	}

	// Nothing is remembered from an earlier subscribe: someone removed in between is refused the next time.
	@Test
	void asksAgainOnEverySubscribe() {
		when(access.mayFollow(userId, channelId, connectedAt)).thenReturn(true, false);
		IskurChannelAccessInterceptor interceptor = interceptor();
		Map<String, Object> attributes = attributes();
		interceptor.preSend(subscribe(channelId, SUBSCRIPTION, attributes), null);

		assertThatThrownBy(() -> interceptor.preSend(subscribe(channelId, "sub-1", attributes), null))
				.isInstanceOf(MessagingException.class);
	}

	// The broker relays a client's SEND to a topic as it is, so one would reach the channel as a forged event.
	@Test
	void neverLetsAClientSendToAChannelTopic() {
		Message<?> send = inbound(StompCommand.SEND, "/topic/iskur/channels/" + channelId, null, attributes());

		assertThatThrownBy(() -> interceptor().preSend(send, null)).isInstanceOf(MessagingException.class);
		verifyNoInteractions(access);
	}

	@Test
	void refusesEveryOtherDestinationUnderTheIskurPrefix() {
		IskurChannelAccessInterceptor interceptor = interceptor();

		// The broker matches a subscription as a pattern, so a wildcard must never get as far as the broker.
		for (String destination : new String[] { "/topic/iskur/channels/not-a-uuid", "/topic/iskur/channels/",
				"/topic/iskur/channels/*", "/topic/iskur/**", "/topic/iskur/users/" + userId,
				"/topic/iskur/channels/" + channelId + "/receipts" }) {
			Message<?> subscribe = inbound(StompCommand.SUBSCRIBE, destination, SUBSCRIPTION, attributes());

			assertThatThrownBy(() -> interceptor.preSend(subscribe, null)).isInstanceOf(MessagingException.class);
		}
		verifyNoInteractions(access);
	}

	@Test
	void refusesASubscribeWithoutAnIdentityASessionOrASubscriptionId() {
		IskurChannelAccessInterceptor interceptor = interceptor();
		Map<String, Object> noIdentity = new HashMap<>();
		noIdentity.put(RealtimeHandshakeInterceptor.CONNECTED_AT_ATTRIBUTE, connectedAt);
		Map<String, Object> noConnectTime = attributes();
		noConnectTime.remove(RealtimeHandshakeInterceptor.CONNECTED_AT_ATTRIBUTE);

		assertThatThrownBy(() -> interceptor.preSend(subscribe(channelId, SUBSCRIPTION, noIdentity), null))
				.isInstanceOf(MessagingException.class);
		assertThatThrownBy(() -> interceptor.preSend(subscribe(channelId, SUBSCRIPTION, noConnectTime), null))
				.isInstanceOf(MessagingException.class);
		assertThatThrownBy(() -> interceptor.preSend(subscribe(channelId, SUBSCRIPTION, null), null))
				.isInstanceOf(MessagingException.class);
		assertThatThrownBy(() -> interceptor.preSend(subscribe(channelId, null, attributes()), null))
				.isInstanceOf(MessagingException.class);
		Message<?> noSession = MessageBuilder.createMessage(new byte[0],
				accessor(StompCommand.SUBSCRIBE, "/topic/iskur/channels/" + channelId, SUBSCRIPTION, attributes(),
						null).getMessageHeaders());
		assertThatThrownBy(() -> interceptor.preSend(noSession, null)).isInstanceOf(MessagingException.class);
		verifyNoInteractions(access);
	}

	// The browser reconnects within seconds of being closed, so the new socket starts after the revoke.
	@Test
	void judgesARevokeAgainstTheSignInRatherThanTheSocket() {
		Instant signedInAt = connectedAt.minusSeconds(600);
		when(access.mayFollow(userId, channelId, signedInAt)).thenReturn(true);
		Map<String, Object> attributes = attributes();
		attributes.put(RealtimeHandshakeInterceptor.IDENTITY_ATTRIBUTE, new RealtimeIdentity(userId, null, signedInAt));

		interceptor().preSend(subscribe(channelId, SUBSCRIPTION, attributes), null);

		verify(access).mayFollow(userId, channelId, signedInAt);
	}

	@Test
	void leavesFramesItDoesNotOwnAlone() {
		Message<?> connect = inbound(StompCommand.CONNECT, null, null, attributes());
		Message<?> meeting = inbound(StompCommand.SUBSCRIBE, "/topic/meetings/" + channelId, SUBSCRIPTION,
				attributes());
		Message<?> noSession = MessageBuilder.createMessage(new byte[0],
				accessor(StompCommand.DISCONNECT, null, null, null, null).getMessageHeaders());
		Message<?> unsubscribeNoSession = MessageBuilder.createMessage(new byte[0],
				accessor(StompCommand.UNSUBSCRIBE, null, SUBSCRIPTION, null, null).getMessageHeaders());

		assertThat(interceptor().preSend(connect, null)).isSameAs(connect);
		assertThat(interceptor().preSend(meeting, null)).isSameAs(meeting);
		assertThat(interceptor().preSend(noSession, null)).isSameAs(noSession);
		assertThat(interceptor().preSend(unsubscribeNoSession, null)).isSameAs(unsubscribeNoSession);
		assertThat(IskurChannelAccessInterceptor.owns(connect)).isFalse();
		assertThat(IskurChannelAccessInterceptor.owns(meeting)).isFalse();
		verifyNoInteractions(access);
	}

	@Test
	void deliversAnEventToASubscriptionItOpened() {
		when(access.mayFollow(userId, channelId, connectedAt)).thenReturn(true);
		IskurChannelAccessInterceptor interceptor = interceptor();
		interceptor.preSend(subscribe(channelId, SUBSCRIPTION, attributes()), null);
		Message<?> event = event(channelId, SESSION, SUBSCRIPTION);

		assertThat(interceptor.delivery().beforeHandle(event, null, null)).isSameAs(event);
		verify(access, times(1)).mayFollow(userId, channelId, connectedAt);
	}

	@Test
	void dropsAnEventForASubscriptionItNeverOpened() {
		when(access.mayFollow(userId, channelId, connectedAt)).thenReturn(true);
		IskurChannelAccessInterceptor interceptor = interceptor();
		interceptor.preSend(subscribe(channelId, SUBSCRIPTION, attributes()), null);

		assertThat(interceptor.delivery().beforeHandle(event(channelId, "another-session", SUBSCRIPTION), null, null))
				.isNull();
		assertThat(interceptor.mayDeliver(event(channelId, SESSION, "another-subscription"))).isFalse();
		assertThat(interceptor.mayDeliver(event(otherChannelId, SESSION, SUBSCRIPTION))).isFalse();
		assertThat(interceptor.mayDeliver(event(channelId, null, SUBSCRIPTION))).isFalse();
		assertThat(interceptor.mayDeliver(event(channelId, SESSION, null))).isFalse();
	}

	// A subscription outlives the check that opened it: someone removed from the channel stops hearing about it.
	@Test
	void stopsDeliveringOnceTheSubscriberNoLongerReachesTheChannel() {
		when(access.mayFollow(userId, channelId, connectedAt)).thenReturn(true, true, false);
		IskurChannelAccessInterceptor interceptor = interceptor();
		interceptor.preSend(subscribe(channelId, SUBSCRIPTION, attributes()), null);

		clock.advance(IskurChannelAccessInterceptor.RECHECK.minusSeconds(2));
		assertThat(interceptor.mayDeliver(event(channelId, SESSION, SUBSCRIPTION))).isTrue();
		verify(access, times(1)).mayFollow(userId, channelId, connectedAt);

		clock.advance(Duration.ofSeconds(2));
		assertThat(interceptor.mayDeliver(event(channelId, SESSION, SUBSCRIPTION))).isTrue();
		assertThat(interceptor.mayDeliver(event(channelId, SESSION, SUBSCRIPTION))).isTrue();
		verify(access, times(2)).mayFollow(userId, channelId, connectedAt);

		clock.advance(IskurChannelAccessInterceptor.RECHECK);
		assertThat(interceptor.mayDeliver(event(channelId, SESSION, SUBSCRIPTION))).isFalse();
		assertThat(interceptor.mayDeliver(event(channelId, SESSION, SUBSCRIPTION))).isFalse();
		verify(access, times(3)).mayFollow(userId, channelId, connectedAt);
	}

	@Test
	void forgetsASubscriptionOnUnsubscribeAndASessionOnDisconnect() {
		when(access.mayFollow(userId, channelId, connectedAt)).thenReturn(true);
		when(access.mayFollow(userId, otherChannelId, connectedAt)).thenReturn(true);
		IskurChannelAccessInterceptor interceptor = interceptor();
		interceptor.preSend(subscribe(channelId, SUBSCRIPTION, attributes()), null);
		interceptor.preSend(subscribe(otherChannelId, "sub-1", attributes()), null);

		interceptor.preSend(inbound(StompCommand.UNSUBSCRIBE, null, SUBSCRIPTION, attributes()), null);
		interceptor.preSend(inbound(StompCommand.UNSUBSCRIBE, null, null, attributes()), null);
		assertThat(interceptor.mayDeliver(event(channelId, SESSION, SUBSCRIPTION))).isFalse();
		assertThat(interceptor.mayDeliver(event(otherChannelId, SESSION, "sub-1"))).isTrue();

		interceptor.preSend(inbound(StompCommand.DISCONNECT, null, null, attributes()), null);
		assertThat(interceptor.mayDeliver(event(otherChannelId, SESSION, "sub-1"))).isFalse();
	}

	// A subscription id used again for another channel hears only that channel.
	@Test
	void aReusedSubscriptionIdFollowsOnlyTheChannelItWasLastOpenedFor() {
		when(access.mayFollow(userId, channelId, connectedAt)).thenReturn(true);
		when(access.mayFollow(userId, otherChannelId, connectedAt)).thenReturn(true);
		IskurChannelAccessInterceptor interceptor = interceptor();
		interceptor.preSend(subscribe(channelId, SUBSCRIPTION, attributes()), null);
		interceptor.preSend(subscribe(otherChannelId, SUBSCRIPTION, attributes()), null);

		assertThat(interceptor.mayDeliver(event(channelId, SESSION, SUBSCRIPTION))).isFalse();
		assertThat(interceptor.mayDeliver(event(otherChannelId, SESSION, SUBSCRIPTION))).isTrue();
	}

	@Test
	void capsTheSubscriptionsOfOneSocket() {
		when(access.mayFollow(userId, channelId, connectedAt)).thenReturn(true);
		IskurChannelAccessInterceptor interceptor = interceptor();
		Map<String, Object> attributes = attributes();
		for (int index = 0; index < IskurChannelAccessInterceptor.MAX_SUBSCRIPTIONS; index++) {
			interceptor.preSend(subscribe(channelId, "sub-" + index, attributes), null);
		}

		assertThatThrownBy(() -> interceptor.preSend(subscribe(channelId, "one-too-many", attributes), null))
				.isInstanceOf(MessagingException.class);
		Message<?> again = subscribe(channelId, "sub-0", attributes);
		assertThat(interceptor.preSend(again, null)).isSameAs(again);
	}

	@Test
	void leavesEveryOtherOutboundMessageAlone() {
		IskurChannelAccessInterceptor interceptor = interceptor();
		SimpMessageHeaderAccessor meeting = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		meeting.setDestination("/topic/meetings/" + channelId);
		SimpMessageHeaderAccessor heartbeat = SimpMessageHeaderAccessor.create(SimpMessageType.HEARTBEAT);
		SimpMessageHeaderAccessor noDestination = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);

		assertThat(interceptor.mayDeliver(message(meeting))).isTrue();
		assertThat(interceptor.mayDeliver(message(heartbeat))).isTrue();
		assertThat(interceptor.mayDeliver(message(noDestination))).isTrue();
	}

	private IskurChannelAccessInterceptor interceptor() {
		return new IskurChannelAccessInterceptor(access, clock);
	}

	private Map<String, Object> attributes() {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(RealtimeHandshakeInterceptor.IDENTITY_ATTRIBUTE, new RealtimeIdentity(userId, null, null));
		attributes.put(RealtimeHandshakeInterceptor.CONNECTED_AT_ATTRIBUTE, connectedAt);
		return attributes;
	}

	private Message<?> subscribe(UUID channel, String subscriptionId, Map<String, Object> attributes) {
		return inbound(StompCommand.SUBSCRIBE, "/topic/iskur/channels/" + channel, subscriptionId, attributes);
	}

	private static Message<?> inbound(StompCommand command, String destination, String subscriptionId,
			Map<String, Object> attributes) {
		return MessageBuilder.createMessage(new byte[0],
				accessor(command, destination, subscriptionId, attributes, SESSION).getMessageHeaders());
	}

	private static StompHeaderAccessor accessor(StompCommand command, String destination, String subscriptionId,
			Map<String, Object> attributes, String sessionId) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setDestination(destination);
		if (subscriptionId != null) {
			accessor.setSubscriptionId(subscriptionId);
		}
		accessor.setSessionAttributes(attributes);
		accessor.setSessionId(sessionId);
		return accessor;
	}

	/** An event as the broker hands it to one subscriber. */
	private static Message<?> event(UUID channel, String sessionId, String subscriptionId) {
		SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		accessor.setDestination("/topic/iskur/channels/" + channel);
		accessor.setSessionId(sessionId);
		if (subscriptionId != null) {
			accessor.setSubscriptionId(subscriptionId);
		}
		return message(accessor);
	}

	private static Message<?> message(SimpMessageHeaderAccessor accessor) {
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		private MutableClock(Instant now) {
			this.now = now;
		}

		private void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}
}
