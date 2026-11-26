package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IskurChannelKind;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurChannel;
import com.dps.teknokent.entity.IskurInternEmail;
import com.dps.teknokent.entity.IskurMessage;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinator;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurChannelRepository;
import com.dps.teknokent.repository.IskurInternEmailRepository;
import com.dps.teknokent.repository.IskurMessageRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.security.AppUserPrincipal;
import com.dps.teknokent.service.IskurChannelMembershipService;
import com.dps.teknokent.service.IskurMessagePostedEvent;
import com.jayway.jsonpath.JsonPath;

/** The İŞKUR channels, through the ODTÜ edition's real security chain and an H2 database. */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class IskurChannelIntegrationTest {

	private static final String CHANNELS = "/api/iskur/channels";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private IskurChannelMembershipService membership;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private IskurInternEmailRepository internEmailRepository;
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
	private IskurMessageRepository messageRepository;
	@Autowired
	private ApplicationEvents recorded;

	private AppUser admin;
	private AppUser selin;
	private AppUser kaan;
	private AppUser otherCoordinator;
	private Program program;
	private IskurUnit creative;
	private IskurUnit events;
	private IskurTeam media;
	private IskurTeam digital;
	private AppUser ayse;
	private AppUser burak;
	private AppUser ceren;
	private AppUser deniz;
	private AppUser unplaced;

	@BeforeEach
	void setUp() {
		admin = user("Yönetici Test", UserRole.ADMIN, null);
		selin = user("Selin Er", UserRole.COORDINATOR, null);
		kaan = user("Kaan Yurt", UserRole.COORDINATOR, null);
		AppUser ozan = user("Ozan Tek", UserRole.COORDINATOR, null);
		otherCoordinator = user("Başka Koordinatör", UserRole.COORDINATOR, null);
		program = program("İŞKUR Güz 2026", selin);
		coordinatorOf(program, kaan);
		coordinatorOf(program, ozan);
		program("Başka Program", otherCoordinator);

		creative = unit("Kreatif ve Dijital Üretim", kaan);
		events = unit("Etkinlik ve Topluluk", null);
		media = team(creative, "Medya ve Kreatif Üretim");
		digital = team(creative, "Dijital Dönüşüm ve Teknoloji");
		IskurTeam community = team(events, "Etkinlik ve Topluluk Yönetimi");

		ayse = user("Ayşe Yıldız", UserRole.MENTEE, program);
		burak = user("Burak Tan", UserRole.MENTEE, program);
		ceren = user("Ceren Su", UserRole.MENTEE, program);
		deniz = user("Deniz Arslan", UserRole.MENTEE, program);
		AppUser emre = user("Emre Kurt", UserRole.MENTEE, program);
		unplaced = user("Fatma Ak", UserRole.MENTEE, program);
		place(ayse, media, IskurTeamRole.LEADER, null, true);
		place(burak, media, IskurTeamRole.MEMBER, ayse, true);
		place(ceren, digital, IskurTeamRole.MEMBER, null, true);
		place(deniz, media, IskurTeamRole.MEMBER, ayse, false);
		place(emre, community, IskurTeamRole.MEMBER, null, true);
		membership.reconcile(program.getId());
	}

	@Test
	void opensTheFourChannelsOfAPlacementWithTheRightsOfRulesFrameFour() throws Exception {
		channels(burak)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.channels[*].kind").value(Matchers.contains(
						"ANNOUNCEMENTS", "UNIT", "TEAM", "LEADER_DIRECT")))
				.andExpect(jsonPath("$.channels[*].name").value(Matchers.contains(
						"Genel Duyurular", "Kreatif ve Dijital Üretim", "Medya ve Kreatif Üretim", "Ayşe Yıldız")))
				.andExpect(jsonPath("$.channels[0].canWrite").value(false))
				.andExpect(jsonPath("$.channels[0].memberCount").value(8))
				.andExpect(jsonPath("$.channels[1].canWrite").value(true))
				.andExpect(jsonPath("$.channels[1].memberCount").value(5))
				.andExpect(jsonPath("$.channels[2].unitName").value("Kreatif ve Dijital Üretim"))
				.andExpect(jsonPath("$.channels[2].memberCount").value(3))
				.andExpect(jsonPath("$.channels[3].counterpart.fullName").value("Ayşe Yıldız"))
				.andExpect(jsonPath("$.channels[3].counterpart.leader").value(true))
				.andExpect(jsonPath("$.channels[3].coordinatorView").value(false))
				.andExpect(jsonPath("$.channels[3].unopened").value(true))
				.andExpect(jsonPath("$.totalUnread").value(0));

		channels(ayse)
				.andExpect(jsonPath("$.channels[*].name").value(Matchers.contains(
						"Genel Duyurular", "Kreatif ve Dijital Üretim", "Medya ve Kreatif Üretim", "Burak Tan",
						"Deniz Arslan")))
				.andExpect(jsonPath("$.channels[3].counterpart.leader").value(false));
	}

	@Test
	void aProvisionalPlacementOpensTheSameChannelsAndFinalizingItChangesNothing() throws Exception {
		channels(deniz)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.channels[*].kind").value(Matchers.contains(
						"ANNOUNCEMENTS", "UNIT", "TEAM", "LEADER_DIRECT")))
				.andExpect(jsonPath("$.channels[3].name").value("Ayşe Yıldız"));

		long before = channelRepository.count();
		IskurPlacement placement = placementRepository.findByProgramIdAndUserId(program.getId(), deniz.getId())
				.orElseThrow();
		placement.finalizeBy(selin.getId(), Instant.now());
		placementRepository.saveAndFlush(placement);
		membership.reconcile(program.getId());

		assertThat(channelRepository.count()).isEqualTo(before);
		channels(deniz).andExpect(jsonPath("$.channels.length()").value(4));
	}

	@Test
	void aProvisionalInternWhoIsMovedFollowsTheMove() throws Exception {
		move(deniz, digital, null);
		channels(deniz)
				.andExpect(jsonPath("$.channels[*].name").value(Matchers.contains(
						"Genel Duyurular", "Kreatif ve Dijital Üretim", "Dijital Dönüşüm ve Teknoloji")));
		messages(deniz, channelOf(IskurChannelKind.TEAM, media.getId()).getId()).andExpect(status().isNotFound());
	}

	@Test
	void runningTheSyncAgainChangesNothing() throws Exception {
		long before = channelRepository.count();
		membership.reconcile(program.getId());
		membership.reconcile(program.getId());
		assertThat(channelRepository.count()).isEqualTo(before);
		channels(burak).andExpect(jsonPath("$.channels.length()").value(4));
	}

	@Test
	void aTeamMoveLeavesTheOldChannelsWithTheirHistoryAndComingBackRejoinsThem() throws Exception {
		UUID oldTeam = channelOf(IskurChannelKind.TEAM, media.getId()).getId();
		postMessage(burak, oldTeam, "{\"body\":\"Merhaba ekip\"}").andExpect(status().isOk());

		move(burak, digital, null);
		channels(burak)
				.andExpect(jsonPath("$.channels[*].name").value(Matchers.contains(
						"Genel Duyurular", "Kreatif ve Dijital Üretim", "Dijital Dönüşüm ve Teknoloji")));
		messages(burak, oldTeam).andExpect(status().isNotFound());
		messages(ayse, oldTeam)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages[*].body").value(Matchers.contains("Merhaba ekip")))
				.andExpect(jsonPath("$.channel.memberCount").value(2));

		move(burak, media, ayse);
		messages(burak, oldTeam)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages[0].mine").value(true));
		channels(burak).andExpect(jsonPath("$.channels.length()").value(4));
	}

	@Test
	void unplacingSomeoneTakesThemOutOfEveryChannel() throws Exception {
		placementRepository.delete(placementRepository.findByProgramIdAndUserId(program.getId(), ceren.getId())
				.orElseThrow());
		membership.reconcile(program.getId());
		channels(ceren).andExpect(jsonPath("$.channels").isEmpty());
	}

	@Test
	void internsWriteInTheirChannelsAndOnlyReadGenelDuyurular() throws Exception {
		UUID announcements = channelOf(IskurChannelKind.ANNOUNCEMENTS, null).getId();
		UUID team = channelOf(IskurChannelKind.TEAM, media.getId()).getId();

		postMessage(burak, announcements, "{\"body\":\"Soru\"}").andExpect(status().isForbidden());
		postMessage(burak, team, "{\"body\":\"Bugün 14:00'te toplanalım\",\"important\":true}")
				.andExpect(status().isBadRequest());
		postMessage(burak, team, "{\"body\":\"  Bugün 14:00'te toplanalım  \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body").value("Bugün 14:00'te toplanalım"))
				.andExpect(jsonPath("$.mine").value(true))
				.andExpect(jsonPath("$.sender.fullName").value("Burak Tan"))
				.andExpect(jsonPath("$.sender.coordinator").value(false))
				.andExpect(jsonPath("$.receipts").doesNotExist());

		channels(ayse)
				.andExpect(jsonPath("$.channels[2].unreadCount").value(1))
				.andExpect(jsonPath("$.totalUnread").value(1));
		channels(burak).andExpect(jsonPath("$.totalUnread").value(0));
		send(post(CHANNELS + "/{id}/read", team), actor(ayse)).andExpect(status().isNoContent());
		channels(ayse)
				.andExpect(jsonPath("$.channels[2].unreadCount").value(0))
				.andExpect(jsonPath("$.channels[2].unopened").value(false));
	}

	@Test
	void someoneOutsideAChannelCannotTellItExists() throws Exception {
		UUID team = channelOf(IskurChannelKind.TEAM, media.getId()).getId();
		UUID direct = channelOf(IskurChannelKind.LEADER_DIRECT, ayse.getId()).getId();

		messages(ceren, team).andExpect(status().isNotFound());
		postMessage(ceren, team, "{\"body\":\"Merhaba\"}").andExpect(status().isNotFound());
		send(post(CHANNELS + "/{id}/read", team), actor(ceren)).andExpect(status().isNotFound());
		messages(unplaced, team).andExpect(status().isNotFound());
		messages(UUID.randomUUID(), team).andExpect(status().isNotFound());
		messages(selin, direct).andExpect(status().isNotFound());
		messages(kaan, direct).andExpect(status().isNotFound());
		messages(admin, direct).andExpect(status().isNotFound());
		messages(otherCoordinator, channelOf(IskurChannelKind.ANNOUNCEMENTS, null).getId())
				.andExpect(status().isNotFound());
		channels(otherCoordinator).andExpect(jsonPath("$.channels").isEmpty());
	}

	@Test
	void aUnitCoordinatorReachesTheirUnitAndItsTeamsAndPostsOnlyInTheUnitChannel() throws Exception {
		channels(kaan)
				.andExpect(jsonPath("$.channels[*].name").value(Matchers.contains(
						"Genel Duyurular", "Etkinlik ve Topluluk", "Kreatif ve Dijital Üretim",
						"Dijital Dönüşüm ve Teknoloji", "Etkinlik ve Topluluk Yönetimi", "Medya ve Kreatif Üretim")))
				.andExpect(jsonPath("$.channels[*].coordinatorView").value(Matchers.everyItem(Matchers.is(true))))
				.andExpect(jsonPath("$.channels[3].canWrite").value(false));

		postMessage(kaan, channelOf(IskurChannelKind.UNIT, creative.getId()).getId(), "{\"body\":\"Hoş geldiniz\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sender.coordinator").value(true));
		postMessage(kaan, channelOf(IskurChannelKind.TEAM, media.getId()).getId(), "{\"body\":\"Merhaba\"}")
				.andExpect(status().isForbidden());
	}

	@Test
	void anotherProgramCoordinatorSeesOnlyGenelDuyurularAndUnitsWithoutTheirOwnCoordinator() throws Exception {
		channels(selin)
				.andExpect(jsonPath("$.channels[*].name").value(Matchers.contains(
						"Genel Duyurular", "Etkinlik ve Topluluk", "Etkinlik ve Topluluk Yönetimi")))
				.andExpect(jsonPath("$.channels[0].canWrite").value(true))
				.andExpect(jsonPath("$.channels[1].canWrite").value(true));
		messages(selin, channelOf(IskurChannelKind.UNIT, creative.getId()).getId()).andExpect(status().isNotFound());
		channels(selin, program.getId()).andExpect(jsonPath("$.channels.length()").value(3));
		channels(selin, UUID.randomUUID()).andExpect(jsonPath("$.channels").isEmpty());
	}

	@Test
	void theAdminReadsEveryUnitAndTeamButPostsOnlyInGenelDuyurular() throws Exception {
		channels(admin)
				.andExpect(jsonPath("$.channels.length()").value(6))
				.andExpect(jsonPath("$.channels[0].canWrite").value(true))
				.andExpect(jsonPath("$.channels[1].canWrite").value(false));
		postMessage(admin, channelOf(IskurChannelKind.UNIT, events.getId()).getId(), "{\"body\":\"Merhaba\"}")
				.andExpect(status().isForbidden());
		postMessage(admin, channelOf(IskurChannelKind.ANNOUNCEMENTS, null).getId(), "{\"body\":\"Duyuru\"}")
				.andExpect(status().isOk());
	}

	@Test
	void importantAnnouncementsNotifyEveryoneAndCollectReadReceipts() throws Exception {
		UUID announcements = channelOf(IskurChannelKind.ANNOUNCEMENTS, null).getId();
		String id = idOf(postMessage(selin, announcements, """
				{"title":"Perşembe 19 Kasım: İş sağlığı ve güvenliği eğitimi",\
				"body":"Eğitim 09:00'da başlıyor.","important":true}""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.important").value(true))
				.andExpect(jsonPath("$.title").value("Perşembe 19 Kasım: İş sağlığı ve güvenliği eğitimi"))
				.andExpect(jsonPath("$.sender.coordinator").value(true))
				.andExpect(jsonPath("$.receipts.read").value(0))
				.andExpect(jsonPath("$.receipts.total").value(5)));

		assertThat(recorded.stream(IskurMessagePostedEvent.class)).extracting(IskurMessagePostedEvent::messageId)
				.containsExactly(UUID.fromString(id));

		send(post("/api/iskur/messages/{id}/acknowledge", id), actor(burak))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.acknowledgedAt").isNotEmpty())
				.andExpect(jsonPath("$.receipts").doesNotExist());
		send(post("/api/iskur/messages/{id}/acknowledge", id), actor(burak)).andExpect(status().isOk());
		send(post("/api/iskur/messages/{id}/acknowledge", id), actor(unplaced)).andExpect(status().isNotFound());
		send(post("/api/iskur/messages/{id}/acknowledge", id), actor(selin)).andExpect(status().isBadRequest());

		receipts(selin, id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.read").value(1))
				.andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.people[*].fullName").value(Matchers.contains(
						"Ayşe Yıldız", "Burak Tan", "Ceren Su", "Deniz Arslan", "Emre Kurt")))
				.andExpect(jsonPath("$.people[0].readAt").doesNotExist())
				.andExpect(jsonPath("$.people[1].readAt").isNotEmpty())
				.andExpect(jsonPath("$.people[0].email").doesNotExist());
		receipts(kaan, id).andExpect(status().isOk());
		receipts(burak, id).andExpect(status().isForbidden());
		messages(ayse, announcements)
				.andExpect(jsonPath("$.messages[0].acknowledgedAt").doesNotExist())
				.andExpect(jsonPath("$.messages[0].receipts").doesNotExist());
		messages(burak, announcements).andExpect(jsonPath("$.messages[0].acknowledgedAt").isNotEmpty());
		messages(selin, announcements).andExpect(jsonPath("$.messages[0].receipts.read").value(1));
	}

	@Test
	void onlyImportantAnnouncementsAskForAReceipt() throws Exception {
		UUID announcements = channelOf(IskurChannelKind.ANNOUNCEMENTS, null).getId();
		String plain = idOf(
				postMessage(selin, announcements, "{\"title\":\"Hatırlatma\",\"body\":\"Yarın görüşürüz.\"}")
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.receipts").doesNotExist()));
		send(post("/api/iskur/messages/{id}/acknowledge", plain), actor(burak)).andExpect(status().isBadRequest());
		receipts(selin, plain).andExpect(status().isBadRequest());
		postMessage(kaan, channelOf(IskurChannelKind.UNIT, creative.getId()).getId(),
				"{\"title\":\"Başlık\",\"body\":\"Birim duyurusu\"}").andExpect(status().isBadRequest());
		postMessage(selin, announcements, "{\"body\":\"   \"}").andExpect(status().isBadRequest());
	}

	@Test
	void internsMuteKindsOfChannelButNeverGenelDuyurular() throws Exception {
		String path = "/api/iskur/channel-preferences";
		send(put(path), preferences(burak, "\"LEADER_DIRECT\",\"TEAM\""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mutedKinds").value(Matchers.contains("TEAM", "LEADER_DIRECT")));
		mockMvc.perform(get(path).param("programId", program.getId().toString())
				.param("actorId", burak.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mutedKinds").value(Matchers.contains("TEAM", "LEADER_DIRECT")));
		send(put(path), preferences(burak, "\"UNIT\""))
				.andExpect(jsonPath("$.mutedKinds").value(Matchers.contains("UNIT")));
		send(put(path), preferences(burak, "\"ANNOUNCEMENTS\"")).andExpect(status().isBadRequest());
		send(put(path), preferences(unplaced, "")).andExpect(status().isNotFound());
		send(put(path), preferences(kaan, "\"UNIT\"")).andExpect(status().isOk());
		send(put(path), preferences(otherCoordinator, "")).andExpect(status().isNotFound());
	}

	@Test
	void pagesBackFiftyMessagesAtATime() throws Exception {
		UUID team = channelOf(IskurChannelKind.TEAM, media.getId()).getId();
		Instant start = Instant.parse("2026-11-19T07:00:00Z");
		UUID sixth = null;
		for (int i = 1; i <= 55; i++) {
			IskurMessage saved = messageRepository.save(new IskurMessage(team, ayse.getId(), null, "Mesaj " + i,
					false, start.plusSeconds(i * 60L)));
			if (i == 6) {
				sixth = saved.getId();
			}
		}
		messages(burak, team)
				.andExpect(jsonPath("$.messages.length()").value(50))
				.andExpect(jsonPath("$.messages[0].body").value("Mesaj 6"))
				.andExpect(jsonPath("$.messages[49].body").value("Mesaj 55"))
				.andExpect(jsonPath("$.hasMore").value(true));
		mockMvc.perform(get(CHANNELS + "/{id}/messages", team)
				.param("actorId", burak.getId().toString())
				.param("before", String.valueOf(sixth))
				.with(httpBasic("test", "test")))
				.andExpect(jsonPath("$.messages[*].body").value(Matchers.contains(
						"Mesaj 1", "Mesaj 2", "Mesaj 3", "Mesaj 4", "Mesaj 5")))
				.andExpect(jsonPath("$.hasMore").value(false));
	}

	@Test
	void messagesSentAtTheSameMomentAreNeitherSkippedNorRepeatedAcrossPages() throws Exception {
		UUID team = channelOf(IskurChannelKind.TEAM, media.getId()).getId();
		Instant same = Instant.parse("2026-11-19T07:00:00Z");
		for (int i = 1; i <= 53; i++) {
			messageRepository.save(new IskurMessage(team, ayse.getId(), null, "Aynı an " + i, false, same));
		}
		String first = messages(burak, team).andReturn().getResponse().getContentAsString();
		List<String> firstPage = JsonPath.read(first, "$.messages[*].id");
		String oldest = firstPage.getFirst();
		String second = mockMvc.perform(get(CHANNELS + "/{id}/messages", team)
				.param("actorId", burak.getId().toString())
				.param("before", oldest)
				.with(httpBasic("test", "test")))
				.andExpect(jsonPath("$.hasMore").value(false))
				.andReturn().getResponse().getContentAsString();
		List<String> secondPage = JsonPath.read(second, "$.messages[*].id");
		assertThat(firstPage).hasSize(50).doesNotContainAnyElementsOf(secondPage);
		assertThat(secondPage).hasSize(3);
	}

	@Test
	void aCursorFromAnotherChannelIsRefused() throws Exception {
		UUID team = channelOf(IskurChannelKind.TEAM, media.getId()).getId();
		UUID elsewhere = channelOf(IskurChannelKind.UNIT, creative.getId()).getId();
		IskurMessage other = messageRepository.save(new IskurMessage(elsewhere, ayse.getId(), null, "Birim", false,
				Instant.now()));
		mockMvc.perform(get(CHANNELS + "/{id}/messages", team)
				.param("actorId", burak.getId().toString())
				.param("before", other.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void someonePostingInALoopIsStoppedAfterThirtyAMinute() throws Exception {
		UUID team = channelOf(IskurChannelKind.TEAM, media.getId()).getId();
		for (int i = 0; i < 30; i++) {
			postMessage(ayse, team, "{\"body\":\"Mesaj\"}").andExpect(status().isOk());
		}
		postMessage(ayse, team, "{\"body\":\"Bir tane daha\"}").andExpect(status().isTooManyRequests());
	}

	@Test
	void readingMarksTheNewestMessageAndKeepsLaterOnesUnread() throws Exception {
		UUID team = channelOf(IskurChannelKind.TEAM, media.getId()).getId();
		postMessage(burak, team, "{\"body\":\"Bir\"}").andExpect(status().isOk());
		send(post(CHANNELS + "/{id}/read", team), actor(ayse)).andExpect(status().isNoContent());
		send(post(CHANNELS + "/{id}/read", team), actor(ayse)).andExpect(status().isNoContent());
		postMessage(burak, team, "{\"body\":\"İki\"}").andExpect(status().isOk());
		channels(ayse).andExpect(jsonPath("$.channels[2].unreadCount").value(1));
	}

	@Test
	void anAccountThatIsNotApprovedIsRefused() throws Exception {
		burak.setStatus(UserAccountStatus.PENDING);
		userRepository.saveAndFlush(burak);
		channels(burak).andExpect(status().isForbidden());
	}

	@Test
	void aSignedInSessionWinsOverTheActorIdInTheRequest() throws Exception {
		mockMvc.perform(get(CHANNELS)
				.param("actorId", ayse.getId().toString())
				.with(authentication(signedIn(burak))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.channels[3].name").value("Ayşe Yıldız"));
	}

	private ResultActions channels(AppUser actor) throws Exception {
		return mockMvc.perform(get(CHANNELS)
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions channels(AppUser actor, UUID programId) throws Exception {
		return mockMvc.perform(get(CHANNELS)
				.param("actorId", actor.getId().toString())
				.param("programId", programId.toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions messages(AppUser actor, UUID channelId) throws Exception {
		return messages(actor.getId(), channelId);
	}

	private ResultActions messages(UUID actorId, UUID channelId) throws Exception {
		return mockMvc.perform(get(CHANNELS + "/{id}/messages", channelId)
				.param("actorId", actorId.toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions receipts(AppUser actor, String messageId) throws Exception {
		return mockMvc.perform(get("/api/iskur/messages/{id}/receipts", messageId)
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions postMessage(AppUser actor, UUID channelId, String json) throws Exception {
		String body = "{\"actorId\":\"" + actor.getId() + "\"," + json.strip().substring(1);
		return send(post(CHANNELS + "/{id}/messages", channelId), body);
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
		return mockMvc.perform(request.with(httpBasic("test", "test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private String preferences(AppUser actor, String kinds) {
		return "{\"actorId\":\"%s\",\"programId\":\"%s\",\"mutedKinds\":[%s]}"
				.formatted(actor.getId(), program.getId(), kinds);
	}

	private static String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static String actor(AppUser actor) {
		return "{\"actorId\":\"" + actor.getId() + "\"}";
	}

	private IskurChannel channelOf(IskurChannelKind kind, UUID ref) {
		return channelRepository.findByProgramId(program.getId()).stream()
				.filter(channel -> channel.getKind() == kind)
				.filter(channel -> switch (kind) {
				case ANNOUNCEMENTS -> true;
				case UNIT -> ref.equals(channel.getUnitId());
				case TEAM -> ref.equals(channel.getTeamId());
				case LEADER_DIRECT -> ref.equals(channel.getLeaderId());
				})
				.findFirst()
				.orElseThrow();
	}

	private void move(AppUser who, IskurTeam team, AppUser leader) {
		IskurPlacement placement = placementRepository.findByProgramIdAndUserId(program.getId(), who.getId())
				.orElseThrow();
		placement.setTeamId(team.getId());
		placement.setLeaderId(leader == null ? null : leader.getId());
		placementRepository.saveAndFlush(placement);
		membership.reconcile(program.getId());
	}

	private void place(AppUser who, IskurTeam team, IskurTeamRole role, AppUser leader, boolean finalized) {
		IskurPlacement placement = new IskurPlacement(program.getId(), who.getId(), selin.getId(), Instant.now());
		placement.setTeamId(team.getId());
		placement.setTeamRole(role);
		placement.setLeaderId(leader == null ? null : leader.getId());
		placement.setWeeklyDays((short) 3);
		if (finalized) {
			placement.finalizeBy(selin.getId(), Instant.now());
		}
		placementRepository.saveAndFlush(placement);
	}

	private IskurUnit unit(String name, AppUser coordinator) {
		IskurUnit unit = new IskurUnit(program.getId(), name, 0, selin.getId(), Instant.now());
		unit.setCoordinatorId(coordinator == null ? null : coordinator.getId());
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
		AppUser saved = userRepository.saveAndFlush(user);
		if (joined != null && role == UserRole.MENTEE) {
			internOf(joined, saved);
		}
		return saved;
	}

	/** Puts the person's address on the program's İŞKUR intern list, as the coordinator would. */
	private void internOf(Program listing, AppUser person) {
		internEmailRepository.saveAndFlush(new IskurInternEmail(listing.getId(),
				person.getEmail().toLowerCase(Locale.ROOT), null, Instant.now()));
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

	private static UsernamePasswordAuthenticationToken signedIn(AppUser user) {
		return new UsernamePasswordAuthenticationToken(new AppUserPrincipal(
				user.getId(), user.getEmail(), user.getRole(), user.getStatus(), "iskur-test-uid"), null, List.of());
	}
}
