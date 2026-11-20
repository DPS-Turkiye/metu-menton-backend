package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.dps.teknokent.domain.IskurChannelEventType;
import com.dps.teknokent.dto.responses.IskurChannelEvent;

@ExtendWith(MockitoExtension.class)
class IskurChannelRealtimePublisherTest {

	@Mock
	private SimpMessagingTemplate messagingTemplate;
	@InjectMocks
	private IskurChannelRealtimePublisher publisher;

	private final UUID channelId = UUID.fromString("00000000-0000-0000-0000-000000000c01");
	private final UUID messageId = UUID.fromString("00000000-0000-0000-0000-000000000d01");

	@Test
	void pushesTheChangeToTheChannelsOwnTopic() {
		publisher.onChannelChanged(new IskurChannelChangedEvent(IskurChannelEventType.MESSAGE_ACKNOWLEDGED, channelId,
				messageId));

		verify(messagingTemplate).convertAndSend("/topic/iskur/channels/" + channelId,
				new IskurChannelEvent(IskurChannelEventType.MESSAGE_ACKNOWLEDGED, channelId, messageId));
	}

	// The message is already committed; a broker that cannot take the event must not fail the request.
	@Test
	void aFailedPushDoesNotReachTheCaller() {
		doThrow(new MessageDeliveryException("broker is down")).when(messagingTemplate)
				.convertAndSend(anyString(), any(Object.class));

		assertThatCode(() -> publisher.onChannelChanged(new IskurChannelChangedEvent(
				IskurChannelEventType.MESSAGE_POSTED, channelId, messageId))).doesNotThrowAnyException();
	}
}
