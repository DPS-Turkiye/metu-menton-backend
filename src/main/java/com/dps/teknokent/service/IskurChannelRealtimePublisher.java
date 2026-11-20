package com.dps.teknokent.service;

import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.dps.teknokent.domain.IskurChannelDestinations;
import com.dps.teknokent.dto.responses.IskurChannelEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Pushes İŞKUR channel changes to the channel's live topic. The push is a hint: the screens also poll. */
@Slf4j
@Service
@RequiredArgsConstructor
public class IskurChannelRealtimePublisher {

	private final SimpMessagingTemplate messagingTemplate;

	/** After the change is committed; a failed push is logged and the change stays. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onChannelChanged(IskurChannelChangedEvent event) {
		try {
			messagingTemplate.convertAndSend(IskurChannelDestinations.channel(event.channelId()),
					new IskurChannelEvent(event.type(), event.channelId(), event.messageId()));
		} catch (MessagingException failure) {
			log.warn("İŞKUR channel push failed for channel {}", event.channelId(), failure);
		}
	}
}
