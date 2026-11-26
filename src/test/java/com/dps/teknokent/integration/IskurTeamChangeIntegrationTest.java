package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurInternEmail;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Notification;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinator;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurInternEmailRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.NotificationRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramRepository;

/** Handbook p.18: an intern asks to change team during the trial; the coordinator moves them or declines. */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class IskurTeamChangeIntegrationTest {

	private static final String MINE = "/api/iskur/team-change";
	private static final String LIST = "/api/coordinator/programs/{programId}/iskur/team-changes";
	private static final String PLACE = "/api/coordinator/programs/{programId}/iskur/placements/{userId}";

	@Autowired
	private MockMvc mockMvc;
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
	private NotificationRepository notificationRepository;

	private AppUser selin;
	private AppUser kaan;
	private AppUser otherCoordinator;
	private Program program;
	private IskurTeam media;
	private IskurTeam digital;
	private AppUser ayse;
	private AppUser burak;

	@BeforeEach
	void setUp() throws Exception {
		selin = user("Selin Er", UserRole.COORDINATOR, null);
		kaan = user("Kaan Yurt", UserRole.COORDINATOR, null);
		otherCoordinator = user("Başka Koordinatör", UserRole.COORDINATOR, null);
		program = program("İŞKUR Güz 2026", selin);
		coordinatorOf(kaan);
		program("Başka Program", otherCoordinator);
		IskurUnit creative = unitRepository.saveAndFlush(new IskurUnit(program.getId(), "Kreatif ve Dijital Üretim",
				0, selin.getId(), Instant.now()));
		creative.setCoordinatorId(kaan.getId());
		unitRepository.saveAndFlush(creative);
		media = teamRepository.saveAndFlush(new IskurTeam(creative, "Medya ve Kreatif Üretim", 0, Instant.now()));
		digital = teamRepository.saveAndFlush(new IskurTeam(creative, "Dijital Dönüşüm ve Teknoloji", 1,
				Instant.now()));
		ayse = user("Ayşe Yıldız", UserRole.MENTEE, program);
		burak = user("Burak Tan", UserRole.MENTEE, program);
		place(ayse, media).andExpect(status().isOk());
	}

	@Test
	void anInternAsksDuringTheTrialAndTheUnitCoordinatorIsTold() throws Exception {
		mine(ayse)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canAsk").value(true))
				.andExpect(jsonPath("$.latest").doesNotExist());

		ask(ayse, "  Dijital Dönüşüm ekibindeki işler yetkinliklerime daha uygun.  ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canAsk").value(false))
				.andExpect(jsonPath("$.latest.status").value("PENDING"))
				.andExpect(jsonPath("$.latest.reason").value(
						"Dijital Dönüşüm ekibindeki işler yetkinliklerime daha uygun."))
				.andExpect(jsonPath("$.latest.fromTeamName").value("Medya ve Kreatif Üretim"));
		ask(ayse, "Bir daha").andExpect(status().isConflict());

		assertThat(titles(kaan)).containsExactly("Ekip değişikliği talebi");
		assertThat(titles(selin)).isEmpty();
		list(selin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requests[0].fullName").value("Ayşe Yıldız"))
				.andExpect(jsonPath("$.requests[0].currentTeamName").value("Medya ve Kreatif Üretim"));
	}

	@Test
	void movingTheInternAnswersTheRequestAndTellsThem() throws Exception {
		ask(ayse, "Dijital ekibe geçmek istiyorum.").andExpect(status().isOk());
		place(ayse, digital).andExpect(status().isOk());

		mine(ayse)
				.andExpect(jsonPath("$.latest.status").value("MOVED"))
				.andExpect(jsonPath("$.latest.fromTeamName").value("Medya ve Kreatif Üretim"))
				.andExpect(jsonPath("$.latest.currentTeamName").value("Dijital Dönüşüm ve Teknoloji"))
				.andExpect(jsonPath("$.canAsk").value(true));
		assertThat(titles(ayse)).containsExactly("Ekibin değişti");
	}

	@Test
	void aCoordinatorDeclinesWithANoteTheInternReads() throws Exception {
		String id = idOf(ask(ayse, "Dijital ekibe geçmek istiyorum."));
		decline(kaan, id, "").andExpect(status().isBadRequest());
		decline(kaan, id, "Medya ekibinin bu dönem sana ihtiyacı var.")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requests[0].status").value("DECLINED"))
				.andExpect(jsonPath("$.requests[0].decidedByName").value("Kaan Yurt"));
		decline(selin, id, "Tekrar").andExpect(status().isConflict());

		mine(ayse)
				.andExpect(jsonPath("$.latest.decisionNote").value("Medya ekibinin bu dönem sana ihtiyacı var."))
				.andExpect(jsonPath("$.canAsk").value(true));
		assertThat(titles(ayse)).containsExactly("Ekip değişikliği talebin yanıtlandı");
	}

	@Test
	void onlyAPlacedInternInTheTrialMayAsk() throws Exception {
		ask(burak, "Ekip istiyorum").andExpect(status().isBadRequest());
		send(post("/api/coordinator/programs/{programId}/iskur/placements/finalize", program.getId()),
				"{\"actorId\":\"%s\",\"teamId\":\"%s\"}".formatted(selin.getId(), media.getId()))
				.andExpect(status().isOk());
		mine(ayse).andExpect(jsonPath("$.canAsk").value(false));
		ask(ayse, "Değiştirmek istiyorum").andExpect(status().isBadRequest());
		mine(selin).andExpect(status().isNotFound());
	}

	@Test
	void anEditInsideTheSameTeamOrAFinalizeLeavesTheRequestWaiting() throws Exception {
		ask(ayse, "Dijital ekibe geçmek istiyorum.").andExpect(status().isOk());
		place(ayse, media).andExpect(status().isOk());
		send(post("/api/coordinator/programs/{programId}/iskur/placements/finalize", program.getId()),
				"{\"actorId\":\"%s\",\"teamId\":\"%s\"}".formatted(selin.getId(), media.getId()))
				.andExpect(status().isOk());
		mine(ayse).andExpect(jsonPath("$.latest.status").value("PENDING"));
		list(kaan).andExpect(jsonPath("$.requests[0].status").value("PENDING"));
	}

	@Test
	void everyProgramCoordinatorIsToldWhileTheUnitHasNoneOfItsOwn() throws Exception {
		IskurUnit events = unitRepository.saveAndFlush(new IskurUnit(program.getId(), "Etkinlik ve Topluluk", 1,
				selin.getId(), Instant.now()));
		IskurTeam community = teamRepository.saveAndFlush(new IskurTeam(events, "Etkinlik ve Topluluk Yönetimi", 0,
				Instant.now()));
		place(burak, community).andExpect(status().isOk());
		ask(burak, "Medya ekibine geçmek istiyorum.").andExpect(status().isOk());
		assertThat(titles(selin)).containsExactly("Ekip değişikliği talebi");
		assertThat(titles(kaan)).containsExactly("Ekip değişikliği talebi");
	}

	@Test
	void unplacingTheInternCancelsTheirRequest() throws Exception {
		ask(ayse, "Dijital ekibe geçmek istiyorum.").andExpect(status().isOk());
		send(delete(PLACE, program.getId(), ayse.getId()).param("actorId", selin.getId().toString()), null)
				.andExpect(status().isOk());
		list(selin).andExpect(jsonPath("$.requests[0].status").value("CANCELLED"));
	}

	@Test
	void onlyTheProgramsCoordinatorsSeeAndAnswerTheRequests() throws Exception {
		String id = idOf(ask(ayse, "Dijital ekibe geçmek istiyorum."));
		// The same guard as the rest of the İŞKUR workspace.
		list(otherCoordinator).andExpect(status().isBadRequest());
		decline(otherCoordinator, id, "Hayır").andExpect(status().isBadRequest());
		list(ayse).andExpect(status().is4xxClientError());
		decline(ayse, id, "Hayır").andExpect(status().is4xxClientError());
		mine(ayse).andExpect(jsonPath("$.latest.status").value("PENDING"));
	}

	private ResultActions mine(AppUser actor) throws Exception {
		return mockMvc.perform(get(MINE).param("actorId", actor.getId().toString()).with(httpBasic("test", "test")));
	}

	private ResultActions ask(AppUser actor, String reason) throws Exception {
		return send(post(MINE), "{\"actorId\":\"%s\",\"reason\":\"%s\"}".formatted(actor.getId(), reason));
	}

	private ResultActions list(AppUser actor) throws Exception {
		return mockMvc.perform(get(LIST, program.getId()).param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions decline(AppUser actor, String id, String note) throws Exception {
		return send(post(LIST + "/{id}/decline", program.getId(), id),
				"{\"actorId\":\"%s\",\"note\":\"%s\"}".formatted(actor.getId(), note));
	}

	private ResultActions place(AppUser who, IskurTeam team) throws Exception {
		return send(put(PLACE, program.getId(), who.getId()),
				"{\"actorId\":\"%s\",\"teamId\":\"%s\",\"role\":\"%s\",\"leaderId\":null,\"weeklyDays\":3,\"mentoringMinutes\":null}"
						.formatted(selin.getId(), team.getId(), IskurTeamRole.MEMBER));
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
		MockHttpServletRequestBuilder withAuth = request.with(httpBasic("test", "test"));
		if (body != null) {
			withAuth = withAuth.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(withAuth);
	}

	private static String idOf(ResultActions result) throws Exception {
		String json = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		int at = json.indexOf("\"id\":\"") + 6;
		return json.substring(at, json.indexOf('"', at));
	}

	private java.util.List<String> titles(AppUser recipient) {
		return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipient.getId()).stream()
				.map(Notification::getTitle)
				.toList();
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
			internEmailRepository.saveAndFlush(new IskurInternEmail(joined.getId(),
					saved.getEmail().toLowerCase(Locale.ROOT), null, Instant.now()));
		}
		return saved;
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

	private void coordinatorOf(AppUser user) {
		ProgramCoordinator link = new ProgramCoordinator();
		link.setId(UUID.randomUUID());
		link.setProgramId(program.getId());
		link.setUserId(user.getId());
		link.setAddedBy(selin.getId());
		link.setCreatedAt(Instant.now());
		programCoordinatorRepository.saveAndFlush(link);
	}
}
