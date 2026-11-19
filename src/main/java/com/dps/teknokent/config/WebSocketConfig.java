package com.dps.teknokent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.dps.teknokent.domain.MeetingRoomDestinations;
import com.dps.teknokent.service.IskurChannelRealtimeAccessService;
import com.dps.teknokent.service.MeetingRoomAccessService;
import com.dps.teknokent.service.RealtimeTokenService;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final RealtimeTokenService realtimeTokenService;
	private final MeetingRoomAccessService meetingRoomAccessService;
	/** One instance for both directions: the outbound side reads the subscriptions the inbound side recorded. */
	private final IskurChannelAccessInterceptor iskurChannelAccess;

	@Value("${app.realtime.allowed-origins}")
	private String[] allowedOrigins;

	public WebSocketConfig(RealtimeTokenService realtimeTokenService,
			MeetingRoomAccessService meetingRoomAccessService,
			IskurChannelRealtimeAccessService iskurChannelRealtimeAccessService) {
		this.realtimeTokenService = realtimeTokenService;
		this.meetingRoomAccessService = meetingRoomAccessService;
		this.iskurChannelAccess = new IskurChannelAccessInterceptor(iskurChannelRealtimeAccessService);
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
				.setAllowedOriginPatterns(allowedOrigins)
				.addInterceptors(new RealtimeHandshakeInterceptor(realtimeTokenService));
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker(MeetingRoomDestinations.BROKER_PREFIX);
		registry.setApplicationDestinationPrefixes(MeetingRoomDestinations.APPLICATION_PREFIX);
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(new RealtimeAccessRouter(iskurChannelAccess,
				new MeetingRoomAccessInterceptor(meetingRoomAccessService)));
	}

	@Override
	public void configureClientOutboundChannel(ChannelRegistration registration) {
		registration.interceptors(iskurChannelAccess.delivery());
	}
}
