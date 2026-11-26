package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IskurTaskKind;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurInternEmail;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTask;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Notification;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurInternEmailRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTaskRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.NotificationRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.MeetingSlotTimes;

/** Handbook p.14/p.18: a leader asks another team for help, optionally for a task, and that team's leader answers. */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class IskurTeamSupportIntegrationTest {

	private static final String BASE = "/api/iskur/team-support";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private IskurInternEmailRepository internEmailRepository;
	@Autowired
	private ProgramRepository programRepository;
	@Autowired
	private IskurUnitRepository unitRepository;
	@Autowired
	private IskurTeamRepository teamRepository;
	@Autowired
	private IskurPlacementRepository placementRepository;
	@Autowired
	private IskurTaskRepository taskRepository;
	@Autowired
	private NotificationRepository notificationRepository;

	private AppUser selin;
	private Program program;
	private IskurTeam media;
	private IskurTeam digital;
	private IskurTeam elsewhere;
	private AppUser ayse;
	private AppUser burak;
	private AppUser ceren;
	private AppUser deniz;
	private IskurTask poster;

	@BeforeEach
	void setUp() {
		selin = user("Selin Er", UserRole.COORDINATOR, null);
		program = program("İŞKUR Güz 2026", selin);
		IskurUnit creative = unitRepository.saveAndFlush(new IskurUnit(program.getId(), "Kreatif ve Dijital Üretim",
				0, selin.getId(), Instant.now()));
		media = teamRepository.saveAndFlush(new IskurTeam(creative, "Medya ve Kreatif Üretim", 0, Instant.now()));
		digital = teamRepository.saveAndFlush(new IskurTeam(creative, "Dijital Dönüşüm ve Teknoloji", 1,
				Instant.now()));
		Program other = program("Başka Program", selin);
		IskurUnit otherUnit = unitRepository.saveAndFlush(new IskurUnit(other.getId(), "Birim", 0, selin.getId(),
				Instant.now()));
		elsewhere = teamRepository.saveAndFlush(new IskurTeam(otherUnit, "Başka Ekip", 0, Instant.now()));

		ayse = user("Ayşe Yıldız", UserRole.MENTEE, program);
		burak = user("Burak Tan", UserRole.MENTEE, program);
		ceren = user("Ceren Su", UserRole.MENTEE, program);
		deniz = user("Deniz Arslan", UserRole.MENTEE, program);
		place(ayse, media, IskurTeamRole.LEADER, null);
		place(burak, media, IskurTeamRole.MEMBER, ayse);
		place(ceren, digital, IskurTeamRole.LEADER, null);
		place(deniz, digital, IskurTeamRole.MEMBER, ceren);

		poster = new IskurTask(program.getId(), media.getId(), burak.getId(), ayse.getId(), Instant.now());
		poster.setTitle("Afiş için 2 görsel öneri");
		poster.setKind(IskurTaskKind.EVENT);
		poster.setEstimatedMinutes(120);
		poster.setDueDate(LocalDate.now(MeetingSlotTimes.APP_ZONE).plusDays(3));
		poster.setUpdatedAt(Instant.now());
		poster = taskRepository.saveAndFlush(poster);
	}

	@Test
	void aLeaderAsksAnotherTeamForATaskAndItsLeaderIsTold() throws Exception {
		mine(ayse)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.teamName").value("Medya ve Kreatif Üretim"))
				.andExpect(jsonPath("$.teams[*].name").value(Matchers.contains("Dijital Dönüşüm ve Teknoloji")))
				.andExpect(jsonPath("$.outgoing").isEmpty());

		open(ayse, digital, poster.getId(), "  A3 afiş için görsel desteği  ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outgoing[0].toTeamName").value("Dijital Dönüşüm ve Teknoloji"))
				.andExpect(jsonPath("$.outgoing[0].taskTitle").value("Afiş için 2 görsel öneri"))
				.andExpect(jsonPath("$.outgoing[0].need").value("A3 afiş için görsel desteği"))
				.andExpect(jsonPath("$.outgoing[0].status").value("OPEN"))
				.andExpect(jsonPath("$.outgoing[0].canAnswer").value(false));

		assertThat(titles(ceren)).containsExactly("Destek talebi");
		assertThat(titles(deniz)).isEmpty();
		mine(ceren)
				.andExpect(jsonPath("$.incoming[0].fromTeamName").value("Medya ve Kreatif Üretim"))
				.andExpect(jsonPath("$.incoming[0].openedByName").value("Ayşe Yıldız"))
				.andExpect(jsonPath("$.incoming[0].canAnswer").value(true));
		taskSupport(burak, poster.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].toTeamName").value("Dijital Dönüşüm ve Teknoloji"));
	}

	@Test
	void theAskedLeaderAcceptsAndTheAskerAndTheAssigneeAreTold() throws Exception {
		String id = idOf(open(ayse, digital, poster.getId(), "Görsel desteği"));
		answer(ayse, id, "accept", null).andExpect(status().isNotFound());
		answer(ceren, id, "accept", "Deniz perşembe yardım eder.")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.incoming[0].status").value("ACCEPTED"))
				.andExpect(jsonPath("$.incoming[0].answeredByName").value("Ceren Su"))
				.andExpect(jsonPath("$.incoming[0].canAnswer").value(false));
		answer(ceren, id, "decline", null).andExpect(status().isConflict());

		assertThat(titles(ayse)).containsExactly("Destek talebi kabul edildi");
		assertThat(titles(burak)).containsExactly("Destek talebi kabul edildi");
		taskSupport(ayse, poster.getId())
				.andExpect(jsonPath("$[0].status").value("ACCEPTED"))
				.andExpect(jsonPath("$[0].answerNote").value("Deniz perşembe yardım eder."));
	}

	@Test
	void aDeclineWithoutATaskTellsOnlyTheAsker() throws Exception {
		String id = idOf(open(ayse, digital, null, "Etkinlik günü iki kişi"));
		answer(ceren, id, "decline", "Bu hafta ekip dolu.")
				.andExpect(jsonPath("$.incoming[0].status").value("DECLINED"))
				.andExpect(jsonPath("$.incoming[0].taskId").doesNotExist());
		assertThat(titles(ayse)).containsExactly("Destek talebi yanıtlandı");
		assertThat(titles(burak)).isEmpty();
	}

	@Test
	void onlyLeadersAskAndAnswerAndOnlyOtherTeamsOfTheProgramCanBeAsked() throws Exception {
		mine(burak).andExpect(status().isForbidden());
		open(burak, digital, null, "Destek").andExpect(status().isForbidden());
		open(ayse, media, null, "Destek").andExpect(status().isBadRequest());
		open(ayse, elsewhere, null, "Destek").andExpect(status().isNotFound());
		open(ayse, digital, null, "   ").andExpect(status().isBadRequest());
		IskurTask theirs = new IskurTask(program.getId(), digital.getId(), deniz.getId(), ceren.getId(),
				Instant.now());
		theirs.setTitle("Başka ekibin görevi");
		theirs.setKind(IskurTaskKind.TEAM_TASK);
		theirs.setEstimatedMinutes(60);
		theirs.setDueDate(LocalDate.now(MeetingSlotTimes.APP_ZONE).plusDays(2));
		theirs.setUpdatedAt(Instant.now());
		taskRepository.saveAndFlush(theirs);
		open(ayse, digital, theirs.getId(), "Destek").andExpect(status().isNotFound());

		String id = idOf(open(ayse, digital, null, "Destek"));
		answer(deniz, id, "accept", null).andExpect(status().isForbidden());
		taskSupport(deniz, poster.getId()).andExpect(status().isNotFound());
	}

	private ResultActions mine(AppUser actor) throws Exception {
		return mockMvc.perform(get(BASE).param("actorId", actor.getId().toString()).with(httpBasic("test", "test")));
	}

	private ResultActions taskSupport(AppUser actor, UUID taskId) throws Exception {
		return mockMvc.perform(get("/api/iskur/tasks/{taskId}/team-support", taskId)
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions open(AppUser actor, IskurTeam to, UUID taskId, String need) throws Exception {
		return mockMvc.perform(post(BASE).with(httpBasic("test", "test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"%s\",\"toTeamId\":\"%s\",\"taskId\":%s,\"need\":\"%s\"}".formatted(
						actor.getId(), to.getId(), taskId == null ? "null" : "\"" + taskId + "\"", need)));
	}

	private ResultActions answer(AppUser actor, String id, String how, String note) throws Exception {
		return mockMvc.perform(post(BASE + "/{id}/" + how, id).with(httpBasic("test", "test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"%s\",\"note\":%s}".formatted(actor.getId(),
						note == null ? "null" : "\"" + note + "\"")));
	}

	private static String idOf(ResultActions result) throws Exception {
		String json = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		int at = json.indexOf("\"outgoing\":[{\"id\":\"") + "\"outgoing\":[{\"id\":\"".length();
		return json.substring(at, json.indexOf('"', at));
	}

	private List<String> titles(AppUser recipient) {
		return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipient.getId()).stream()
				.map(Notification::getTitle)
				.toList();
	}

	private void place(AppUser who, IskurTeam team, IskurTeamRole role, AppUser leader) {
		IskurPlacement placement = new IskurPlacement(program.getId(), who.getId(), selin.getId(), Instant.now());
		placement.setTeamId(team.getId());
		placement.setTeamRole(role);
		placement.setLeaderId(leader == null ? null : leader.getId());
		placement.setWeeklyDays((short) 3);
		placementRepository.saveAndFlush(placement);
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
}
