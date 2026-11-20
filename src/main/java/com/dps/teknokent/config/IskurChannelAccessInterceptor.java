package com.dps.teknokent.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;

import com.dps.teknokent.domain.IskurChannelDestinations;
import com.dps.teknokent.service.IskurChannelRealtimeAccessService;
import com.dps.teknokent.service.RealtimeTokenService.RealtimeIdentity;

/**
 * Guards the İŞKUR channels' live topics, on the way in and on the way out.
 * <ul>
 * <li>In: a SUBSCRIBE is checked against the same rules as the REST reads, on every subscribe; nothing is remembered
 * from an earlier one. A client can never SEND to these topics: the broker would relay it to the channel as if the
 * server had published it.</li>
 * <li>Out ({@link #delivery()}): a subscription outlives the check that opened it, so before an event is delivered
 * the reach is asked again once it is older than {@link #RECHECK}. Someone moved out of a team, a coordinator taken
 * off the program or an account whose sign-ins were ended stops hearing about the channel within that time.</li>
 * </ul>
 */
public class IskurChannelAccessInterceptor implements ChannelInterceptor {

	static final Duration RECHECK = Duration.ofSeconds(60);
	/** An admin follows every channel of a program at once; past this a socket is not the app's own client. */
	static final int MAX_SUBSCRIPTIONS = 200;

	private final IskurChannelRealtimeAccessService access;
	private final Clock clock;
	/** Per socket session, its open channel subscriptions by subscription id. */
	private final Map<String, Map<String, Following>> sessions = new ConcurrentHashMap<>();

	public IskurChannelAccessInterceptor(IskurChannelRealtimeAccessService access) {
		this(access, Clock.systemUTC());
	}

	IskurChannelAccessInterceptor(IskurChannelRealtimeAccessService access, Clock clock) {
		this.access = access;
		this.clock = clock;
	}

	/** True for a frame the İŞKUR topics own: a subscribe or a send whose destination is under their prefix. */
	static boolean owns(Message<?> message) {
		MessageHeaders headers = message.getHeaders();
		SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(headers);
		String destination = SimpMessageHeaderAccessor.getDestination(headers);
		return (type == SimpMessageType.SUBSCRIBE || type == SimpMessageType.MESSAGE)
				&& destination != null
				&& destination.startsWith(IskurChannelDestinations.PREFIX);
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		MessageHeaders headers = message.getHeaders();
		SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(headers);
		String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
		if (type == SimpMessageType.DISCONNECT && sessionId != null) {
			sessions.remove(sessionId);
		} else if (type == SimpMessageType.UNSUBSCRIBE && sessionId != null) {
			forget(sessionId, SimpMessageHeaderAccessor.getSubscriptionId(headers));
		} else if (owns(message)) {
			String destination = SimpMessageHeaderAccessor.getDestination(headers);
			if (type != SimpMessageType.SUBSCRIBE || !follow(headers, sessionId, destination)) {
				throw new MessagingException("Access to " + destination + " is not allowed.");
			}
		}
		return message;
	}

	/** The outbound side: drops an event for a subscription that is unknown or no longer allowed. */
	public ExecutorChannelInterceptor delivery() {
		return new ExecutorChannelInterceptor() {

			// On the outbound executor, not in preSend: the publisher's thread is the request that posted the message.
			@Override
			public @Nullable Message<?> beforeHandle(Message<?> message, MessageChannel channel,
					MessageHandler handler) {
				return mayDeliver(message) ? message : null;
			}
		};
	}

	boolean mayDeliver(Message<?> message) {
		MessageHeaders headers = message.getHeaders();
		String destination = SimpMessageHeaderAccessor.getDestination(headers);
		if (SimpMessageHeaderAccessor.getMessageType(headers) != SimpMessageType.MESSAGE
				|| destination == null
				|| !destination.startsWith(IskurChannelDestinations.PREFIX)) {
			return true;
		}
		String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
		String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(headers);
		if (sessionId == null || subscriptionId == null) {
			return false;
		}
		Map<String, Following> following = sessions.get(sessionId);
		Following followed = following == null ? null : following.get(subscriptionId);
		if (following == null
				|| followed == null
				|| !destination.equals(IskurChannelDestinations.channel(followed.channelId))) {
			return false;
		}
		Instant now = clock.instant();
		if (followed.isFresh(now)) {
			return true;
		}
		if (!access.mayFollow(followed.userId, followed.channelId, followed.signedInAt)) {
			// Only this entry: the same id may have been subscribed again, and checked, in the meantime.
			following.remove(subscriptionId, followed);
			return false;
		}
		followed.checked(now);
		return true;
	}

	private boolean follow(MessageHeaders headers, @Nullable String sessionId, @Nullable String destination) {
		UUID channelId = channelIdOf(destination);
		String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(headers);
		Map<String, Object> attributes = SimpMessageHeaderAccessor.getSessionAttributes(headers);
		RealtimeIdentity identity = attributes != null
				&& attributes.get(RealtimeHandshakeInterceptor.IDENTITY_ATTRIBUTE) instanceof RealtimeIdentity found
						? found
						: null;
		if (channelId == null || sessionId == null || subscriptionId == null || attributes == null
				|| identity == null) {
			return false;
		}
		Instant signedInAt = signedInAt(identity, attributes);
		if (signedInAt == null || !access.mayFollow(identity.userId(), channelId, signedInAt)) {
			return false;
		}
		Map<String, Following> following = sessions.computeIfAbsent(sessionId, id -> new ConcurrentHashMap<>());
		if (following.size() >= MAX_SUBSCRIPTIONS && !following.containsKey(subscriptionId)) {
			return false;
		}
		following.put(subscriptionId, new Following(identity.userId(), channelId, signedInAt, clock.instant()));
		return true;
	}

	private void forget(String sessionId, @Nullable String subscriptionId) {
		Map<String, Following> following = sessions.get(sessionId);
		if (following != null && subscriptionId != null) {
			following.remove(subscriptionId);
		}
	}

	/**
	 * The sign-in the token was issued for; the socket's own start is only the fallback for a token without one (see
	 * {@link MeetingRoomAccessInterceptor}).
	 */
	private static @Nullable Instant signedInAt(RealtimeIdentity identity, Map<String, Object> attributes) {
		if (identity.signedInAt() != null) {
			return identity.signedInAt();
		}
		return attributes.get(RealtimeHandshakeInterceptor.CONNECTED_AT_ATTRIBUTE) instanceof Instant connectedAt
				? connectedAt
				: null;
	}

	private static @Nullable UUID channelIdOf(@Nullable String destination) {
		if (destination == null || !destination.startsWith(IskurChannelDestinations.CHANNEL_PREFIX)) {
			return null;
		}
		try {
			return UUID.fromString(destination.substring(IskurChannelDestinations.CHANNEL_PREFIX.length()));
		} catch (IllegalArgumentException error) {
			return null;
		}
	}

	private static final class Following {

		private final UUID userId;
		private final UUID channelId;
		private final Instant signedInAt;
		private volatile Instant checkedAt;

		private Following(UUID userId, UUID channelId, Instant signedInAt, Instant checkedAt) {
			this.userId = userId;
			this.channelId = channelId;
			this.signedInAt = signedInAt;
			this.checkedAt = checkedAt;
		}

		private boolean isFresh(Instant now) {
			return now.isBefore(checkedAt.plus(RECHECK));
		}

		private void checked(Instant now) {
			checkedAt = now;
		}
	}
}
