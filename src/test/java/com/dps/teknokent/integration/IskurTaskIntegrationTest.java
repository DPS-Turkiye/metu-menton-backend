package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IskurTaskStatus;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTask;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Notification;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTaskRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.NotificationRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.security.AppUserPrincipal;
import com.dps.teknokent.util.MeetingSlotTimes;

/** Ekibim's tasks and "Görev detayı", through the ODTÜ edition's real security chain and an H2 database. */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class IskurTaskIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private AppUserRepository userRepository;
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

	private Program program;
	private AppUser zeynep;
	private AppUser mert;
	private AppUser ayse;
	private AppUser kaan;
	private AppUser burak;
	private AppUser outsider;
	private LocalDate today;
	private LocalDate monday;

	@BeforeEach
	void setUp() {
		AppUser coordinator = user("Selin Er", UserRole.COORDINATOR, null);
		program = program(coordinator);
		IskurUnit unit = unitRepository.saveAndFlush(new IskurUnit(program.getId(), "Kreatif ve Dijital Üretim", 0,
				coordinator.getId(), Instant.now()));
		IskurTeam team = teamRepository.saveAndFlush(new IskurTeam(unit, "Medya ve Kreatif Üretim", 0,
				Instant.now()));
		IskurTeam otherTeam = teamRepository.saveAndFlush(new IskurTeam(unit, "Dijital Dönüşüm", 1, Instant.now()));

		zeynep = user("Zeynep Kaya", UserRole.MENTEE, program);
		mert = user("Mert Demir", UserRole.MENTEE, program);
		ayse = user("Ayşe Yıldız", UserRole.MENTEE, program);
		kaan = user("Kaan Yurt", UserRole.MENTEE, program);
		burak = user("Burak Tan", UserRole.MENTEE, program);
		outsider = user("Deniz Arslan", UserRole.MENTEE, program);
		place(zeynep, team, IskurTeamRole.LEADER, null, 3);
		place(mert, team, IskurTeamRole.MEMBER, zeynep, 3);
		place(ayse, team, IskurTeamRole.MEMBER, zeynep, 1);
		place(kaan, team, IskurTeamRole.LEADER, null, 2);
		place(burak, team, IskurTeamRole.MEMBER, kaan, null);
		place(outsider, otherTeam, IskurTeamRole.MEMBER, null, 2);

		today = LocalDate.now(MeetingSlotTimes.APP_ZONE);
		monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	@Test
	void aLeaderAssignsToTheirSubgroupAndTheAssigneeIsNotified() throws Exception {
		assign(zeynep, mert, "Sponsor listesi: 10 firma", 120, today, false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.weekStart").value(monday.toString()))
				.andExpect(jsonPath("$.team.name").value("Medya ve Kreatif Üretim"))
				.andExpect(jsonPath("$.team.unitName").value("Kreatif ve Dijital Üretim"))
				.andExpect(jsonPath("$.viewerRole").value("LEADER"))
				.andExpect(jsonPath("$.canAssign").value(true))
				// The leader, their sub-group, then the other leader and theirs.
				.andExpect(jsonPath("$.members[*].fullName").value(Matchers.contains(
						"Zeynep Kaya", "Ayşe Yıldız", "Mert Demir", "Kaan Yurt", "Burak Tan")))
				.andExpect(jsonPath("$.members[*].assignable").value(Matchers.contains(true, true, true, false, false)))
				.andExpect(jsonPath("$.members[2].weeklyDays").value(3))
				.andExpect(jsonPath("$.members[2].teamMinutes").value(990))
				.andExpect(jsonPath("$.members[2].plannedMinutes").value(120))
				.andExpect(jsonPath("$.members[2].overTarget").value(false))
				.andExpect(jsonPath("$.members[2].tasks[0].title").value("Sponsor listesi: 10 firma"))
				.andExpect(jsonPath("$.members[2].tasks[0].assignedBy.fullName").value("Zeynep Kaya"))
				.andExpect(jsonPath("$.members[2].tasks[0].status").value("OPEN"))
				.andExpect(jsonPath("$.members[2].tasks[0].canWork").value(false))
				.andExpect(jsonPath("$.members[2].tasks[0].canManage").value(true))
				.andExpect(jsonPath("$.members[4].teamMinutes").doesNotExist())
				.andExpect(jsonPath("$.roster.length()").value(5));

		List<Notification> inbox = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(mert.getId());
		assertThat(inbox).singleElement().satisfies(notification -> {
			assertThat(notification.getCategory()).isEqualTo(NotificationCategory.TASK_ASSIGNED);
			assertThat(notification.getBody()).contains("Zeynep Kaya", "Sponsor listesi: 10 firma");
			assertThat(notification.getHref()).startsWith("/student/team/tasks/");
		});
	}

	@Test
	void aLeaderMayGiveThemselvesATaskWithoutANotification() throws Exception {
		assign(zeynep, zeynep, "Ekip toplantısı: haftalık plan", 60, today, false).andExpect(status().isOk());
		assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(zeynep.getId())).isEmpty();
	}

	@Test
	void flagsAPlanOverTheTeamTargetWithoutRefusingIt() throws Exception {
		// Ayşe works one day: 330 minutes of team work.
		assign(zeynep, ayse, "Afiş için 2 görsel öneri", 240, today, true).andExpect(status().isOk());
		assign(zeynep, ayse, "Konuşmacı tanıtım metinleri", 120, today, false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members[1].fullName").value("Ayşe Yıldız"))
				.andExpect(jsonPath("$.members[1].teamMinutes").value(330))
				.andExpect(jsonPath("$.members[1].plannedMinutes").value(360))
				.andExpect(jsonPath("$.members[1].overTarget").value(true));
	}

	@Test
	void refusesAnotherLeadersSubgroupAnotherTeamAndAMemberAssigning() throws Exception {
		assign(zeynep, burak, "Başka alt grup", 60, today, false).andExpect(status().isBadRequest());
		assign(zeynep, outsider, "Başka ekip", 60, today, false).andExpect(status().isBadRequest());
		assign(mert, ayse, "Üye atayamaz", 60, today, false).andExpect(status().isForbidden());
		assertThat(taskRepository.findAll()).isEmpty();
	}

	@Test
	void checksTheEstimateStepAndTheDueDate() throws Exception {
		assign(zeynep, mert, "Adımsız", 75, today, false).andExpect(status().isBadRequest());
		assign(zeynep, mert, "Çok kısa", 30, today, false).andExpect(status().isBadRequest());
		assign(zeynep, mert, "Çok uzun", 480, today, false).andExpect(status().isBadRequest());
		assign(zeynep, mert, "Geçmiş", 60, today.minusDays(1), false).andExpect(status().isBadRequest());
		assign(zeynep, mert, "Çok ileri", 60, today.plusDays(57), false).andExpect(status().isBadRequest());
		assertThat(taskRepository.findAll()).isEmpty();
	}

	@Test
	void aMemberSeesOnlyTheirOwnTasksButTheWholeRoster() throws Exception {
		assign(zeynep, mert, "Sponsor listesi", 120, today, false).andExpect(status().isOk());
		assign(zeynep, ayse, "Afiş", 60, today, false).andExpect(status().isOk());

		teamTasks(mert, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.viewerRole").value("MEMBER"))
				.andExpect(jsonPath("$.canAssign").value(false))
				.andExpect(jsonPath("$.members.length()").value(1))
				.andExpect(jsonPath("$.members[0].fullName").value("Mert Demir"))
				.andExpect(jsonPath("$.members[0].tasks[*].title").value(Matchers.contains("Sponsor listesi")))
				.andExpect(jsonPath("$.members[0].tasks[0].canWork").value(true))
				.andExpect(jsonPath("$.members[0].tasks[0].canManage").value(false))
				.andExpect(jsonPath("$.roster[*].fullName").value(Matchers.contains(
						"Mert Demir", "Zeynep Kaya", "Ayşe Yıldız", "Kaan Yurt", "Burak Tan")))
				.andExpect(jsonPath("$.roster[0].leaderId").value(zeynep.getId().toString()));
	}

	@Test
	void listsOnlyTheAskedWeekAndRefusesADayThatIsNotAMonday() throws Exception {
		assign(zeynep, mert, "Bu hafta", 60, today, false).andExpect(status().isOk());
		assign(zeynep, mert, "Gelecek hafta", 60, monday.plusDays(7), false).andExpect(status().isOk());

		teamTasks(mert, monday.plusDays(7))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members[0].tasks[*].title").value(Matchers.contains("Gelecek hafta")));
		teamTasks(mert, monday.plusDays(1)).andExpect(status().isBadRequest());
	}

	@Test
	void theAssigneeCompletesATaskWithItsTimeAndOutput() throws Exception {
		UUID taskId = createTask(mert, true);

		update(mert, taskId, "{\"status\":\"DONE\",\"actualMinutes\":105}").andExpect(status().isBadRequest());
		update(mert, taskId, "{\"status\":\"DONE\",\"outputLink\":\"https://drive.example.test/afis.pdf\"}")
				.andExpect(status().isBadRequest());
		update(mert, taskId, "{\"actualMinutes\":105,\"outputLink\":\"javascript:alert(1)\"}")
				.andExpect(status().isBadRequest());

		update(mert, taskId, "{\"status\":\"DONE\",\"actualMinutes\":105,"
				+ "\"outputLink\":\"https://drive.example.test/afis.pdf\",\"outputNote\":\"İki öneri\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DONE"))
				.andExpect(jsonPath("$.actualMinutes").value(105))
				.andExpect(jsonPath("$.outputLink").value("https://drive.example.test/afis.pdf"))
				.andExpect(jsonPath("$.completedAt").isNotEmpty())
				.andExpect(jsonPath("$.canWork").value(true));

		IskurTask saved = taskRepository.findById(taskId).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(IskurTaskStatus.DONE);
		assertThat(saved.getOutputNote()).isEqualTo("İki öneri");
	}

	@Test
	void aHandoverNeedsAReasonAndALeaderMayReopen() throws Exception {
		UUID taskId = createTask(mert, false);

		update(mert, taskId, "{\"status\":\"HANDED_OVER\"}").andExpect(status().isBadRequest());
		update(mert, taskId, "{\"status\":\"HANDED_OVER\",\"handoverReason\":\"Veri bekleniyor\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("HANDED_OVER"))
				.andExpect(jsonPath("$.handoverReason").value("Veri bekleniyor"));
		teamTasks(zeynep, null).andExpect(jsonPath("$.members[2].plannedMinutes").value(0));

		// The leader may not complete someone else's task, only reopen it.
		update(zeynep, taskId, "{\"status\":\"DONE\",\"actualMinutes\":60}").andExpect(status().isForbidden());
		update(zeynep, taskId, "{\"status\":\"OPEN\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.handoverReason").doesNotExist())
				.andExpect(jsonPath("$.canWork").value(false))
				.andExpect(jsonPath("$.canManage").value(true));
	}

	@Test
	void aDoneTaskIsLockedUntilItsAssigneeOrALeaderReopensIt() throws Exception {
		UUID taskId = createTask(mert, true);
		update(mert, taskId, "{\"status\":\"DONE\",\"actualMinutes\":90,\"outputNote\":\"Teslim edildi\"}")
				.andExpect(status().isOk());

		// Saving again, with or without the status, would empty a finished task: refused.
		update(mert, taskId, "{\"status\":\"DONE\"}").andExpect(status().isConflict());
		update(mert, taskId, "{\"actualMinutes\":30}").andExpect(status().isConflict());
		IskurTask locked = taskRepository.findById(taskId).orElseThrow();
		assertThat(locked.getActualMinutes()).isEqualTo(90);
		assertThat(locked.getOutputNote()).isEqualTo("Teslim edildi");

		// A completion by mistake: the assignee reopens it, and the time and output stay for the next try.
		update(mert, taskId, "{\"status\":\"OPEN\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.completedAt").doesNotExist())
				.andExpect(jsonPath("$.actualMinutes").value(90));
	}

	@Test
	void aLeaderCompletesTheirOwnTaskAndAPlainHttpLinkIsRefused() throws Exception {
		assign(zeynep, zeynep, "Ekip toplantısı", 60, today, true).andExpect(status().isOk());
		UUID taskId = taskRepository.findAll().getFirst().getId();
		update(zeynep, taskId, "{\"status\":\"DONE\",\"actualMinutes\":60,"
				+ "\"outputLink\":\"http://notlar.example.test\"}").andExpect(status().isBadRequest());
		update(zeynep, taskId, "{\"status\":\"DONE\",\"actualMinutes\":60,"
				+ "\"outputLink\":\"https://notlar.example.test\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canWork").value(true))
				.andExpect(jsonPath("$.canManage").value(true));
	}

	@Test
	void someoneMovedToAnotherTeamNoLongerReachesTheirOldTask() throws Exception {
		UUID taskId = createTask(mert, false);
		IskurPlacement moved = placementRepository.findByProgramIdAndUserId(program.getId(), mert.getId())
				.orElseThrow();
		moved.setTeamId(placementRepository.findByProgramIdAndUserId(program.getId(), outsider.getId()).orElseThrow()
				.getTeamId());
		moved.setLeaderId(null);
		placementRepository.saveAndFlush(moved);

		task(mert, taskId).andExpect(status().isNotFound());
		update(mert, taskId, "{\"actualMinutes\":30}").andExpect(status().isNotFound());
		task(zeynep, taskId).andExpect(status().isOk());
	}

	@Test
	void aTaskIsHiddenFromEveryoneButItsAssigneeAndTheTeamsLeaders() throws Exception {
		UUID taskId = createTask(mert, false);

		task(mert, taskId).andExpect(status().isOk()).andExpect(jsonPath("$.teamName")
				.value("Medya ve Kreatif Üretim"));
		task(zeynep, taskId).andExpect(status().isOk());
		// The other leader of the same team sees the whole team's tasks too.
		task(kaan, taskId).andExpect(status().isOk()).andExpect(jsonPath("$.canManage").value(true));
		task(ayse, taskId).andExpect(status().isNotFound());
		task(outsider, taskId).andExpect(status().isNotFound());
		update(ayse, taskId, "{\"status\":\"OPEN\"}").andExpect(status().isNotFound());
		deleteTask(ayse, taskId).andExpect(status().isNotFound());
	}

	@Test
	void onlyALeaderDeletesATask() throws Exception {
		UUID taskId = createTask(mert, false);
		deleteTask(mert, taskId).andExpect(status().isForbidden());
		deleteTask(zeynep, taskId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members[2].tasks").isEmpty());
		assertThat(taskRepository.findById(taskId)).isEmpty();
	}

	@Test
	void anArchivedProgramKeepsItsTasksReadOnly() throws Exception {
		UUID taskId = createTask(mert, false);
		program.setStatus(ProgramStatus.COMPLETED);
		programRepository.saveAndFlush(program);

		task(mert, taskId).andExpect(status().isOk());
		update(mert, taskId, "{\"actualMinutes\":60}").andExpect(status().isBadRequest());
	}

	@Test
	void someoneNotPlacedHasNoTeamAndTheSessionWinsOverTheNamedActor() throws Exception {
		AppUser unplaced = user("Ece Kurt", UserRole.MENTEE, program);
		teamTasks(unplaced, null).andExpect(status().isNotFound());

		// Signed in as a member while the body names the leader: the session wins, so assigning is refused.
		mockMvc.perform(post("/api/iskur/team/tasks")
				.with(authentication(signedIn(mert)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(taskBody(zeynep, ayse, "Kimlik", 60, today, false)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/iskur/team/tasks").param("actorId", zeynep.getId().toString()))
				.andExpect(status().isUnauthorized());
		assertThat(taskRepository.findAll()).isEmpty();
	}

	private UUID createTask(AppUser assignee, boolean needsOutput) throws Exception {
		assign(zeynep, assignee, "Afiş için 2 görsel öneri", 120, today, needsOutput).andExpect(status().isOk());
		return taskRepository.findAll().getFirst().getId();
	}

	private ResultActions assign(AppUser actor, AppUser assignee, String title, int minutes, LocalDate due,
			boolean needsOutput) throws Exception {
		return send(post("/api/iskur/team/tasks"), taskBody(actor, assignee, title, minutes, due, needsOutput));
	}

	private static String taskBody(AppUser actor, AppUser assignee, String title, int minutes, LocalDate due,
			boolean needsOutput) {
		return ("{\"actorId\":\"%s\",\"assigneeId\":\"%s\",\"title\":\"%s\",\"kind\":\"EVENT\","
				+ "\"estimatedMinutes\":%d,\"dueDate\":\"%s\",\"needsOutput\":%s,\"description\":\"Bilim sohbetleri\"}")
				.formatted(actor.getId(), assignee.getId(), title, minutes, due, needsOutput);
	}

	private ResultActions teamTasks(AppUser actor, LocalDate weekStart) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/iskur/team/tasks")
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test"));
		if (weekStart != null) {
			request = request.param("weekStart", weekStart.toString());
		}
		return mockMvc.perform(request);
	}

	private ResultActions task(AppUser actor, UUID taskId) throws Exception {
		return mockMvc.perform(get("/api/iskur/tasks/{taskId}", taskId)
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions deleteTask(AppUser actor, UUID taskId) throws Exception {
		return mockMvc.perform(delete("/api/iskur/tasks/{taskId}", taskId)
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions update(AppUser actor, UUID taskId, String fields) throws Exception {
		return send(patch("/api/iskur/tasks/{taskId}", taskId),
				"{\"actorId\":\"" + actor.getId() + "\"," + fields.substring(1));
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
		return mockMvc.perform(request.with(httpBasic("test", "test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private void place(AppUser who, IskurTeam into, IskurTeamRole role, AppUser leader, Integer days) {
		IskurPlacement placement = new IskurPlacement(program.getId(), who.getId(), null, Instant.now());
		placement.setTeamId(into.getId());
		placement.setTeamRole(role);
		placement.setLeaderId(leader == null ? null : leader.getId());
		placement.setWeeklyDays(days == null ? null : days.shortValue());
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
		return userRepository.saveAndFlush(user);
	}

	private Program program(AppUser creator) {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName("İŞKUR Güz 2026");
		created.setCreatedBy(creator.getId());
		created.setCreatedAt(Instant.now());
		created.setStatus(ProgramStatus.ACTIVE);
		created.setProgramType(ProgramType.PEER_MENTORING);
		return programRepository.saveAndFlush(created);
	}

	private static UsernamePasswordAuthenticationToken signedIn(AppUser user) {
		return new UsernamePasswordAuthenticationToken(new AppUserPrincipal(
				user.getId(), user.getEmail(), user.getRole(), user.getStatus(), "iskur-test-uid"), null, List.of());
	}
}
