package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.config.RealtimeHandshakeInterceptor;
import com.dps.teknokent.domain.IskurChannelEventType;
import com.dps.teknokent.domain.IskurChannelKind;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurChannel;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinator;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurChannelRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.service.IskurChannelChangedEvent;
import com.dps.teknokent.service.IskurChannelMembershipService;
import com.dps.teknokent.service.IskurChannelRealtimePublisher;
import com.dps.teknokent.service.RealtimeTokenService.RealtimeIdentity;
import com.jayway.jsonpath.JsonPath;

/**
 * The İŞKUR channels' live topics, through the interceptors the application really registers on the STOMP channels
 * and the channel rules on an H2 database.
 */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class IskurChannelRealtimeIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	@Qualifier("clientInboundChannel")
	private AbstractSubscribableChannel inbound;
	@Autowired
	@Qualifier("clientOutboundChannel")
	private AbstractSubscribableChannel outbound;
	@Autowired
	@Qualifier("brokerChannel")
	private AbstractSubscribableChannel broker;
	@Autowired
	private IskurChannelRealtimePublisher publisher;
	@Autowired
	private IskurChannelMembershipService membership;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private ProgramRepository programRepository;
	@Autowired
	private ProgramCoordinatorRepository programCoordinatorRepository;
	@Autowired
	private IskurUnitRepository unitRepository;
	@Autowired
	private IskurTeamRepository teamRepository;
	@Autowired
	private IskurPlacementRepository placementRepository;
	@Autowired
	private IskurChannelRepository channelRepository;
	@Autowired
	private ApplicationEvents recorded;

	private AppUser admin;
	private AppUser selin;
	private AppUser kaan;
	private AppUser otherCoordinator;
	private Program program;
	private IskurTeam media;
	private AppUser ayse;
	private AppUser burak;
	private AppUser ceren;
	private IskurChannel teamChannel;
	private IskurChannel direct;
	private IskurChannel announcements;

	@BeforeEach
	void setUp() {
		admin = user("Yönetici Test", UserRole.ADMIN, null);
		selin = user("Selin Er", UserRole.COORDINATOR, null);
		kaan = user("Kaan Yurt", UserRole.COORDINATOR, null);
		otherCoordinator = user("Başka Koordinatör", UserRole.COORDINATOR, null);
		program = program("İŞKUR Güz 2026", selin);
		coordinatorOf(program, kaan);
		program("Başka Program", otherCoordinator);

		IskurUnit creative = unit("Kreatif ve Dijital Üretim", kaan);
		media = team(creative, "Medya ve Kreatif Üretim");
		IskurTeam digital = team(creative, "Dijital Dönüşüm ve Teknoloji");
		ayse = user("Ayşe Yıldız", UserRole.MENTEE, program);
		burak = user("Burak Tan", UserRole.MENTEE, program);
		ceren = user("Ceren Su", UserRole.MENTEE, program);
		place(ayse, media, IskurTeamRole.LEADER, null);
		place(burak, media, IskurTeamRole.MEMBER, ayse);
		place(ceren, digital, IskurTeamRole.MEMBER, null);
		membership.reconcile(program.getId());

		List<IskurChannel> opened = channelRepository.findByProgramId(program.getId());
		teamChannel = opened.stream()
				.filter(channel -> channel.getKind() == IskurChannelKind.TEAM)
				.filter(channel -> media.getId().equals(channel.getTeamId()))
				.findFirst()
				.orElseThrow();
		direct = opened.stream()
				.filter(channel -> channel.getKind() == IskurChannelKind.LEADER_DIRECT)
				.findFirst()
				.orElseThrow();
		announcements = opened.stream()
				.filter(channel -> channel.getKind() == IskurChannelKind.ANNOUNCEMENTS)
				.findFirst()
				.orElseThrow();
	}

	@Test
	void theChannelsMembersAndItsCoordinatorsMaySubscribe() {
		for (AppUser allowed : List.of(ayse, burak, kaan, admin)) {
			assertThatCode(() -> subscribe(allowed, teamChannel)).doesNotThrowAnyException();
		}
		assertThatCode(() -> subscribe(selin, announcements)).doesNotThrowAnyException();
		assertThatCode(() -> subscribe(burak, direct)).doesNotThrowAnyException();
	}

	@Test
	void someoneOutsideTheChannelMayNotSubscribe() {
		// Another team's intern, another program's coordinator, and a coordinator of this program whose unit it is not.
		for (AppUser refused : List.of(ceren, otherCoordinator, selin)) {
			assertThatThrownBy(() -> subscribe(refused, teamChannel)).isInstanceOf(MessagingException.class);
		}
	}

	// A member's 1:1 with their leader is nobody else's: not the unit's coordinator's, not the admin's.
	@Test
	void nobodyButTheTwoOfThemFollowsALeadersDirectChannel() {
		for (AppUser refused : List.of(kaan, admin, ceren)) {
			assertThatThrownBy(() -> subscribe(refused, direct)).isInstanceOf(MessagingException.class);
		}
	}

	@Test
	void aRemovedMemberMayNotSubscribeAgain() {
		subscribe(burak, teamChannel);
		placementRepository.delete(placementRepository.findByProgramIdAndUserId(program.getId(), burak.getId())
				.orElseThrow());
		placementRepository.flush();
		membership.reconcile(program.getId());

		assertThatThrownBy(() -> subscribe(burak, teamChannel)).isInstanceOf(MessagingException.class);
		assertThatThrownBy(() -> subscribe(burak, announcements)).isInstanceOf(MessagingException.class);
	}

	@Test
	void anAccountThatIsNoLongerApprovedOrWhoseSignInsWereEndedMayNotSubscribe() {
		burak.setStatus(UserAccountStatus.PENDING);
		userRepository.saveAndFlush(burak);
		assertThatThrownBy(() -> subscribe(burak, teamChannel)).isInstanceOf(MessagingException.class);

		ayse.setSignInsRevokedAt(Instant.now().plusSeconds(5));
		userRepository.saveAndFlush(ayse);
		assertThatThrownBy(() -> subscribe(ayse, teamChannel)).isInstanceOf(MessagingException.class);
	}

	@Test
	void nobodyMaySendToAChannelTopicOrSubscribeWithoutAnIdentity() {
		assertThatThrownBy(() -> frame(StompCommand.SEND, burak, teamChannel)).isInstanceOf(MessagingException.class);
		assertThatThrownBy(() -> through(inbound, message(StompCommand.SUBSCRIBE, new HashMap<>(), teamChannel)))
				.isInstanceOf(MessagingException.class);
	}

	// The outbound channel carries the delivery check, so a subscription the application never opened hears nothing.
	@Test
	void anEventIsOnlyDeliveredToASubscriptionTheApplicationOpened() {
		String session = subscribe(burak, teamChannel);

		assertThat(delivered(session, "sub-0", teamChannel)).isTrue();
		assertThat(delivered("a-session-that-never-subscribed", "sub-0", teamChannel)).isFalse();
		assertThat(delivered(session, "sub-0", announcements)).isFalse();
	}

	@Test
	void aPostedMessageIsPushedToItsChannelAsIdsOnly() throws Exception {
		String messageId = JsonPath.read(mockMvc.perform(post("/api/iskur/channels/{id}/messages", direct.getId())
				.with(httpBasic("test", "test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"%s\",\"body\":\"Danışanım Zeynep Kaya ile görüşme notum\"}"
						.formatted(burak.getId())))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.id");

		IskurChannelChangedEvent change = recorded.stream(IskurChannelChangedEvent.class).findFirst().orElseThrow();
		assertThat(change).isEqualTo(new IskurChannelChangedEvent(IskurChannelEventType.MESSAGE_POSTED,
				direct.getId(), UUID.fromString(messageId)));

		Pushed pushed = push(change);
		assertThat(pushed.destination()).isEqualTo("/topic/iskur/channels/" + direct.getId());
		Map<String, Object> payload = JsonPath.read(pushed.json(), "$");
		assertThat(payload).containsOnlyKeys("type", "channelId", "messageId")
				.containsEntry("type", "MESSAGE_POSTED")
				.containsEntry("channelId", direct.getId().toString())
				.containsEntry("messageId", messageId);
		// The leader on the other end is told that something changed, never what: no text, no name, no note.
		assertThat(pushed.json()).doesNotContain("Zeynep", "Burak", "görüşme", "body", "fullName", "sender");
	}

	@Test
	void confirmingAnImportantAnnouncementIsPushedOnceAndReadingAChannelNever() throws Exception {
		String messageId = JsonPath.read(mockMvc.perform(post("/api/iskur/channels/{id}/messages",
				announcements.getId())
				.with(httpBasic("test", "test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"%s\",\"body\":\"Yarın toplantı var\",\"important\":true}"
						.formatted(selin.getId())))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.id");
		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(post("/api/iskur/messages/{id}/acknowledge", messageId)
					.with(httpBasic("test", "test"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"actorId\":\"%s\"}".formatted(burak.getId())))
					.andExpect(status().isOk());
		}
		mockMvc.perform(post("/api/iskur/channels/{id}/read", announcements.getId())
				.with(httpBasic("test", "test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"%s\"}".formatted(burak.getId())))
				.andExpect(status().is2xxSuccessful());

		assertThat(recorded.stream(IskurChannelChangedEvent.class).map(IskurChannelChangedEvent::type))
				.containsExactly(IskurChannelEventType.MESSAGE_POSTED, IskurChannelEventType.MESSAGE_ACKNOWLEDGED);
		IskurChannelChangedEvent acknowledged = recorded.stream(IskurChannelChangedEvent.class).toList().get(1);
		// Who confirmed it stays with the coordinators' receipts read.
		assertThat(push(acknowledged).json()).doesNotContain(burak.getId().toString());
	}

	private record Pushed(String destination, String json) {
	}

	/** Publishes one change and returns what reached the broker; no socket is subscribed, so it goes no further. */
	private Pushed push(IskurChannelChangedEvent change) {
		List<Message<?>> sent = new ArrayList<>();
		ChannelInterceptor capture = new ChannelInterceptor() {

			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
				sent.add(message);
				return message;
			}
		};
		broker.addInterceptor(capture);
		try {
			publisher.onChannelChanged(change);
		} finally {
			broker.removeInterceptor(capture);
		}
		assertThat(sent).hasSize(1);
		Message<?> message = sent.getFirst();
		return new Pushed(SimpMessageHeaderAccessor.getDestination(message.getHeaders()),
				new String((byte[]) message.getPayload(), StandardCharsets.UTF_8));
	}

	/** Subscribes as this user on a socket of their own and returns its session id. */
	private String subscribe(AppUser user, IskurChannel channel) {
		return frame(StompCommand.SUBSCRIBE, user, channel);
	}

	private String frame(StompCommand command, AppUser user, IskurChannel channel) {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(RealtimeHandshakeInterceptor.IDENTITY_ATTRIBUTE, new RealtimeIdentity(user.getId(), null, null));
		attributes.put(RealtimeHandshakeInterceptor.CONNECTED_AT_ATTRIBUTE, Instant.now());
		Message<?> message = message(command, attributes, channel);
		through(inbound, message);
		return SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
	}

	private static Message<?> message(StompCommand command, Map<String, Object> attributes, IskurChannel channel) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setDestination("/topic/iskur/channels/" + channel.getId());
		accessor.setSubscriptionId("sub-0");
		accessor.setSessionId(UUID.randomUUID().toString());
		accessor.setSessionAttributes(attributes);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	/** Runs the frame past the channel's registered interceptors only; no handler, so the broker keeps no state. */
	private static void through(AbstractSubscribableChannel channel, Message<?> message) {
		for (ChannelInterceptor interceptor : channel.getInterceptors()) {
			assertThat(interceptor.preSend(message, channel)).isNotNull();
		}
	}

	private boolean delivered(String sessionId, String subscriptionId, IskurChannel channel) {
		SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor
				.create(SimpMessageType.MESSAGE);
		accessor.setDestination("/topic/iskur/channels/" + channel.getId());
		accessor.setSessionId(sessionId);
		accessor.setSubscriptionId(subscriptionId);
		Message<?> event = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
		return outbound.getInterceptors().stream()
				.filter(ExecutorChannelInterceptor.class::isInstance)
				.map(ExecutorChannelInterceptor.class::cast)
				.allMatch(interceptor -> interceptor.beforeHandle(event, outbound, message -> {
				}) != null);
	}

	private void place(AppUser who, IskurTeam team, IskurTeamRole role, AppUser leader) {
		IskurPlacement placement = new IskurPlacement(program.getId(), who.getId(), selin.getId(), Instant.now());
		placement.setTeamId(team.getId());
		placement.setTeamRole(role);
		placement.setLeaderId(leader == null ? null : leader.getId());
		placement.setWeeklyDays((short) 3);
		placement.finalizeBy(selin.getId(), Instant.now());
		placementRepository.saveAndFlush(placement);
	}

	private IskurUnit unit(String name, AppUser coordinator) {
		IskurUnit unit = new IskurUnit(program.getId(), name, 0, selin.getId(), Instant.now());
		unit.setCoordinatorId(coordinator.getId());
		return unitRepository.saveAndFlush(unit);
	}

	private IskurTeam team(IskurUnit unit, String name) {
		return teamRepository.saveAndFlush(new IskurTeam(unit, name, 0, Instant.now()));
	}

	private AppUser user(String name, UserRole role, Program joined) {
		AppUser user = new AppUser();
		user.setFullName(name);
		user.setEmail(UUID.randomUUID() + "@example.test");
		user.setRole(role);
		if (joined != null) {
			user.setProgramId(joined.getId());
		}
		return userRepository.saveAndFlush(user);
	}

	private Program program(String name, AppUser creator) {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName(name);
		created.setCreatedBy(creator.getId());
		created.setCreatedAt(Instant.now());
		created.setStatus(ProgramStatus.ACTIVE);
		created.setProgramType(ProgramType.PEER_MENTORING);
		return programRepository.saveAndFlush(created);
	}

	private void coordinatorOf(Program coordinated, AppUser user) {
		ProgramCoordinator link = new ProgramCoordinator();
		link.setId(UUID.randomUUID());
		link.setProgramId(coordinated.getId());
		link.setUserId(user.getId());
		link.setAddedBy(selin.getId());
		link.setCreatedAt(Instant.now());
		programCoordinatorRepository.saveAndFlush(link);
	}
}
