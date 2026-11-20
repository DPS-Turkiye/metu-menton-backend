package com.dps.teknokent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.dps.teknokent.service.IskurChannelRealtimeAccessService;
import com.dps.teknokent.service.MeetingRoomAccessService;
import com.dps.teknokent.service.RealtimeTokenService.RealtimeIdentity;

@ExtendWith(MockitoExtension.class)
class RealtimeAccessRouterTest {

	@Mock
	private IskurChannelRealtimeAccessService iskurAccess;
	@Mock
	private MeetingRoomAccessService meetingAccess;

	private final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000c01");
	private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000201");
	private final UUID organizationId = UUID.fromString("00000000-0000-0000-0000-000000000101");
	private final Instant connectedAt = Instant.parse("2026-11-20T09:00:00Z");

	// An ODTÜ account has no team: its token opens a channel it reaches, and the meeting rooms are never asked.
	@Test
	void sendsAnIskurSubscribeToTheChannelCheckOnly() {
		when(iskurAccess.mayFollow(userId, id, connectedAt)).thenReturn(true);
		Message<?> subscribe = frame(StompCommand.SUBSCRIBE, "/topic/iskur/channels/" + id, null);

		assertThat(router().preSend(subscribe, null)).isSameAs(subscribe);
		verifyNoInteractions(meetingAccess);
	}

	@Test
	void sendsAMeetingSubscribeToTheMeetingRoomCheckOnly() {
		when(meetingAccess.isStillMemberOf(userId, organizationId, connectedAt)).thenReturn(true);
		when(meetingAccess.isMeetingVisibleTo(id, organizationId)).thenReturn(true);
		Message<?> subscribe = frame(StompCommand.SUBSCRIBE, "/topic/meetings/" + id, organizationId);

		assertThat(router().preSend(subscribe, null)).isSameAs(subscribe);
		verifyNoInteractions(iskurAccess);
	}

	@Test
	void refusesADestinationNobodyOwns() {
		for (String destination : new String[] { "/topic/notifications", "/topic/iskur", "/topic/iskurx/channels/" + id,
				"/queue/iskur/channels/" + id, "/topic/**", "/topic/*/channels/" + id, "/topic/iskur/channels/*" }) {
			Message<?> subscribe = frame(StompCommand.SUBSCRIBE, destination, organizationId);

			assertThatThrownBy(() -> router().preSend(subscribe, null)).isInstanceOf(MessagingException.class);
		}
		verifyNoInteractions(iskurAccess, meetingAccess);
	}

	@Test
	void refusesAClientSendToAnIskurTopic() {
		Message<?> send = frame(StompCommand.SEND, "/topic/iskur/channels/" + id, organizationId);

		assertThatThrownBy(() -> router().preSend(send, null)).isInstanceOf(MessagingException.class);
		verifyNoInteractions(iskurAccess, meetingAccess);
	}

	// A token without a team is an ODTÜ account's: it opens no meeting room, whatever the room.
	@Test
	void aTokenWithoutATeamOpensNoMeetingRoom() {
		Message<?> subscribe = frame(StompCommand.SUBSCRIBE, "/topic/meetings/" + id, null);

		assertThatThrownBy(() -> router().preSend(subscribe, null)).isInstanceOf(MessagingException.class);
		verifyNoInteractions(iskurAccess, meetingAccess);
	}

	private RealtimeAccessRouter router() {
		return new RealtimeAccessRouter(new IskurChannelAccessInterceptor(iskurAccess),
				new MeetingRoomAccessInterceptor(meetingAccess));
	}

	private Message<?> frame(StompCommand command, String destination, UUID organization) {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(RealtimeHandshakeInterceptor.IDENTITY_ATTRIBUTE,
				new RealtimeIdentity(userId, organization, null));
		attributes.put(RealtimeHandshakeInterceptor.CONNECTED_AT_ATTRIBUTE, connectedAt);
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setDestination(destination);
		accessor.setSubscriptionId("sub-0");
		accessor.setSessionId("session-1");
		accessor.setSessionAttributes(attributes);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}
}
