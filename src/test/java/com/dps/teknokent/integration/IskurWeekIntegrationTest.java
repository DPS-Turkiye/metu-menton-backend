package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IdentityKind;
import com.dps.teknokent.domain.InstitutionDataSource;
import com.dps.teknokent.domain.IskurTaskKind;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.IskurWeekStatus;
import com.dps.teknokent.domain.MeetingMode;
import com.dps.teknokent.domain.MeetingStatus;
import com.dps.teknokent.domain.MentorshipRequestFormat;
import com.dps.teknokent.domain.MentorshipRequestStatus;
import com.dps.teknokent.domain.MentorshipRequestUrgency;
import com.dps.teknokent.domain.PeerActivityType;
import com.dps.teknokent.domain.PeerMeetingFormat;
import com.dps.teknokent.domain.PeerMeetingType;
import com.dps.teknokent.domain.PermissionSource;
import com.dps.teknokent.domain.ProgramMentorInvitedBy;
import com.dps.teknokent.domain.ProgramMentorStatus;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.IskurMeetingGapAlert;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurSupportActivity;
import com.dps.teknokent.entity.IskurTask;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.IskurWorkWeek;
import com.dps.teknokent.entity.Meeting;
import com.dps.teknokent.entity.MentorshipRequest;
import com.dps.teknokent.entity.Organization;
import com.dps.teknokent.entity.PeerActivity;
import com.dps.teknokent.entity.PeerMeetingReport;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinator;
import com.dps.teknokent.entity.ProgramMentor;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.IskurMeetingGapAlertRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurSupportActivityRepository;
import com.dps.teknokent.repository.IskurTaskRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.IskurWorkWeekRepository;
import com.dps.teknokent.repository.MeetingRepository;
import com.dps.teknokent.repository.MentorshipRequestRepository;
import com.dps.teknokent.repository.NotificationRepository;
import com.dps.teknokent.repository.OrganizationRepository;
import com.dps.teknokent.repository.PeerActivityRepository;
import com.dps.teknokent.repository.PeerMeetingReportRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramMentorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.security.AppUserPrincipal;
import com.dps.teknokent.service.IskurMeetingGapService;
import com.dps.teknokent.service.PeerReportCalendar;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The İŞKUR weekly log and the leader's review, through the ODTÜ edition's real security chain and an H2 database.
 * "Now" is Thursday 19 November 2026; the week starts on Monday the 16th.
 */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class IskurWeekIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
	private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
	private static final String WEEK = "/api/iskur/week/{weekStart}";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private InstitutionPersonRepository institutionPersonRepository;
	@Autowired
	private ProgramRepository programRepository;
	@Autowired
	private ProgramMentorRepository programMentorRepository;
	@Autowired
	private ProgramCoordinatorRepository programCoordinatorRepository;
	@Autowired
	private OrganizationRepository organizationRepository;
	@Autowired
	private MentorshipRequestRepository mentorshipRequestRepository;
	@Autowired
	private MeetingRepository meetingRepository;
	@Autowired
	private PeerMeetingReportRepository meetingReportRepository;
	@Autowired
	private PeerActivityRepository activityRepository;
	@Autowired
	private IskurUnitRepository unitRepository;
	@Autowired
	private IskurTeamRepository teamRepository;
	@Autowired
	private IskurPlacementRepository placementRepository;
	@Autowired
	private IskurTaskRepository taskRepository;
	@Autowired
	private IskurSupportActivityRepository supportRepository;
	@Autowired
	private IskurWorkWeekRepository weekRepository;
	@Autowired
	private IskurMeetingGapAlertRepository alertRepository;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private IskurMeetingGapService meetingGaps;
	@MockitoSpyBean
	private PeerReportCalendar calendar;

	private AppUser coordinator;
	private Program program;
	private AppUser zeynep;
	private AppUser mert;
	private AppUser kaan;
	private AppUser ayse;
	private AppUser outsider;
	private IskurTask mertsTask;
	private IskurTask ayseTask;
	private IskurSupportActivity openOffice;
	private IskurSupportActivity retired;

	@BeforeEach
	void setUp() {
		doReturn(NOW).when(calendar).now();
		coordinator = user("Selin Er", UserRole.COORDINATOR, null);
		program = peerProgram();
		coordinatorOf(program, coordinator);
		IskurUnit unit = unitRepository.saveAndFlush(new IskurUnit(program.getId(), "Kreatif ve Dijital Üretim", 0,
				coordinator.getId(), NOW));
		IskurTeam team = teamRepository.saveAndFlush(new IskurTeam(unit, "Medya ve Kreatif Üretim", 0, NOW));
		IskurTeam other = teamRepository.saveAndFlush(new IskurTeam(unit, "Dijital Dönüşüm", 1, NOW));

		zeynep = peerMentor("Zeynep Kaya");
		mert = peerMentor("Mert Demir");
		kaan = student("Kaan Yurt");
		ayse = student("Ayşe Yıldız");
		outsider = student("Deniz Arslan");
		place(zeynep, team, IskurTeamRole.LEADER, null, 3, null);
		place(mert, team, IskurTeamRole.MEMBER, zeynep, 3, null);
		place(kaan, team, IskurTeamRole.LEADER, null, 2, 0);
		place(ayse, team, IskurTeamRole.MEMBER, kaan, 1, null);
		place(outsider, other, IskurTeamRole.MEMBER, null, 2, null);

		// Mert's peer mentoring this week: one meeting with a sent report, one without, and an activity whose own
		// title names the mentee. None of it may reach a leader except as numbers.
		AppUser emre = student("Emre Gizlioğlu");
		MentorshipRequest request = request(emre, mert);
		Meeting reported = meeting(request, Instant.parse("2026-11-17T08:00:00Z"));
		meeting(request, Instant.parse("2026-11-18T08:00:00Z"));
		report(reported, mert, emre, "Gizli görüşme notu");
		activity(mert, LocalDate.of(2026, 11, 17), "Emre Gizlioğlu ile mesajlaşma", 10);

		mertsTask = task(mert, team, "Sponsor listesi: 10 firma");
		ayseTask = task(ayse, team, "Afiş için görsel öneri");
		openOffice = support("Açık ofis saati", true);
		retired = support("Eski faaliyet", false);
	}

	@Test
	void anInternSeesTheirWeekWithTheSplitTheMentoringNumbersAndTheirTasks() throws Exception {
		week(mert, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.weekStart").value("2026-11-16"))
				.andExpect(jsonPath("$.weekId").doesNotExist())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.editable").value(true))
				.andExpect(jsonPath("$.canSubmit").value(true))
				.andExpect(jsonPath("$.reviewer.fullName").value("Zeynep Kaya"))
				.andExpect(jsonPath("$.split.totalMinutes").value(1350))
				.andExpect(jsonPath("$.split.mentoringMinutes").value(360))
				.andExpect(jsonPath("$.split.teamMinutes").value(990))
				.andExpect(jsonPath("$.mentoring.meetingCount").value(2))
				.andExpect(jsonPath("$.mentoring.meetingMinutes").value(65))
				.andExpect(jsonPath("$.mentoring.activityMinutes").value(10))
				.andExpect(jsonPath("$.mentoring.reportsSubmitted").value(1))
				.andExpect(jsonPath("$.mentoring.reportsDue").value(2))
				.andExpect(jsonPath("$.totals.mentoringMinutes").value(75))
				.andExpect(jsonPath("$.totals.teamMinutes").value(0))
				.andExpect(jsonPath("$.needsSupportActivity").value(false))
				.andExpect(jsonPath("$.tasks[*].title").value(Matchers.contains("Sponsor listesi: 10 firma")))
				.andExpect(jsonPath("$.supportActivities[*].title").value(Matchers.contains("Açık ofis saati")));
		assertThat(weekRepository.findAll()).isEmpty();
	}

	@Test
	void aWeekWithoutAPlannedMeetingSuggestsASupportActivityThatCountsAsMentoring() throws Exception {
		week(ayse, null).andExpect(jsonPath("$.needsSupportActivity").value(true))
				.andExpect(jsonPath("$.canSubmit").value(false));

		addSupport(ayse, openOffice, 90)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.needsSupportActivity").value(false))
				.andExpect(jsonPath("$.entries[0].kind").value("SUPPORT_ACTIVITY"))
				.andExpect(jsonPath("$.entries[0].title").value("Açık ofis saati"))
				.andExpect(jsonPath("$.mentoring.supportMinutes").value(90))
				.andExpect(jsonPath("$.totals.mentoringMinutes").value(90));

		// A retired activity is no longer offered.
		addSupport(ayse, retired, 30).andExpect(status().isBadRequest());
		// Kaan gives no mentoring (0 minutes), so nothing is suggested to him.
		week(kaan, null).andExpect(jsonPath("$.needsSupportActivity").value(false));
	}

	@Test
	void savesTheLogWithinTheRulesAndSendsItToTheLeader() throws Exception {
		saveEntries(mert, lines(
				teamLine("2026-11-17", mertsTask.getId(), null, 180, "sponsor-listesi.xlsx", null),
				teamLine("2026-11-18", null, "Ekip toplantısı", 60, null, "Haftalık plan"),
				supportLine("2026-11-18", openOffice.getId(), 30)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.entries.length()").value(3))
				.andExpect(jsonPath("$.entries[0].title").value("Sponsor listesi: 10 firma"))
				.andExpect(jsonPath("$.totals.teamMinutes").value(240))
				.andExpect(jsonPath("$.totals.mentoringMinutes").value(105));

		// Replaced as a whole.
		saveEntries(mert, lines(teamLine("2026-11-17", mertsTask.getId(), null, 200, null, null)))
				.andExpect(jsonPath("$.entries.length()").value(1))
				.andExpect(jsonPath("$.totals.teamMinutes").value(200));

		saveEntries(mert, lines(teamLine("2026-11-20", null, "Yarın", 60, null, null)))
				.andExpect(status().isBadRequest());
		saveEntries(mert, lines(teamLine("2026-11-13", null, "Geçen hafta", 60, null, null)))
				.andExpect(status().isBadRequest());
		saveEntries(mert, lines(teamLine("2026-11-17", null, "Uzun", 300, null, null),
				teamLine("2026-11-17", null, "Daha uzun", 200, null, null)))
				.andExpect(status().isBadRequest());
		saveEntries(mert, lines(teamLine("2026-11-17", ayseTask.getId(), null, 60, null, null)))
				.andExpect(status().isBadRequest());
		saveEntries(mert, lines(teamLine("2026-11-17", null, " ", 60, null, null)))
				.andExpect(status().isBadRequest());
		send(put(WEEK + "/entries", MONDAY.plusDays(7)), "{\"actorId\":\"" + mert.getId() + "\",\"entries\":[]}")
				.andExpect(status().isBadRequest());

		submit(mert).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUBMITTED"))
				.andExpect(jsonPath("$.editable").value(false))
				.andExpect(jsonPath("$.submittedAt").isNotEmpty());
		assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(zeynep.getId()))
				.singleElement()
				.satisfies(notification -> assertThat(notification.getHref()).startsWith("/student/team/weeks/"));
		assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(kaan.getId())).isEmpty();

		saveEntries(mert, lines(teamLine("2026-11-17", null, "Geç", 60, null, null)))
				.andExpect(status().isConflict());
	}

	@Test
	void aDraftStaysTheInternsOwnUntilItIsSent() throws Exception {
		saveEntries(mert, lines(teamLine("2026-11-17", mertsTask.getId(), null, 180, "taslak.docx", null)))
				.andExpect(status().isOk());
		UUID draftId = weekOf(mert).getId();
		leaderWeek(zeynep, draftId).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/iskur/leader/weeks").param("actorId", zeynep.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(jsonPath("$.people[1].fullName").value("Mert Demir"))
				.andExpect(jsonPath("$.people[1].status").value("DRAFT"))
				.andExpect(jsonPath("$.people[1].weekId").doesNotExist())
				.andExpect(jsonPath("$.people[1].teamMinutes").value(0));
	}

	@Test
	void aRetiredActivityStaysOnTheDraftThatPickedIt() throws Exception {
		addSupport(ayse, openOffice, 60).andExpect(status().isOk());
		openOffice.setActive(false);
		supportRepository.saveAndFlush(openOffice);
		saveEntries(ayse, lines(supportLine("2026-11-18", openOffice.getId(), 60),
				teamLine("2026-11-18", ayseTask.getId(), null, 60, null, null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.entries.length()").value(2))
				.andExpect(jsonPath("$.supportActivities").isEmpty());
	}

	@Test
	void anEmptyWeekCannotBeSent() throws Exception {
		submit(ayse).andExpect(status().isBadRequest());
	}

	@Test
	void theLeaderSeesTeamWorkInFullAndMentoringAsNumbersOnly() throws Exception {
		saveEntries(mert, lines(
				teamLine("2026-11-17", mertsTask.getId(), null, 180, "sponsor-listesi.xlsx", "İlk 6 firma"),
				supportLine("2026-11-18", openOffice.getId(), 30)))
				.andExpect(status().isOk());
		submit(mert).andExpect(status().isOk());
		UUID weekId = weekOf(mert).getId();

		mockMvc.perform(get("/api/iskur/leader/weeks").param("actorId", zeynep.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isOk())
				// Her sub-group, and her co-leader Kaan, whose week is hers to review too.
				.andExpect(jsonPath("$.people[*].fullName").value(Matchers.contains("Kaan Yurt", "Mert Demir")))
				.andExpect(jsonPath("$.people[0].weekId").doesNotExist())
				.andExpect(jsonPath("$.people[1].status").value("SUBMITTED"))
				.andExpect(jsonPath("$.people[1].teamMinutes").value(180))
				.andExpect(jsonPath("$.people[1].mentoringMinutes").value(105));

		String body = leaderWeek(zeynep, weekId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.person.fullName").value("Mert Demir"))
				.andExpect(jsonPath("$.status").value("SUBMITTED"))
				.andExpect(jsonPath("$.canDecide").value(true))
				.andExpect(jsonPath("$.team[0].title").value("Sponsor listesi: 10 firma"))
				.andExpect(jsonPath("$.team[0].output").value("sponsor-listesi.xlsx"))
				.andExpect(jsonPath("$.team[0].note").value("İlk 6 firma"))
				.andExpect(jsonPath("$.team[0].taskStatus").value("OPEN"))
				.andExpect(jsonPath("$.mentoring.meetingCount").value(2))
				.andExpect(jsonPath("$.mentoring.reportsSubmitted").value(1))
				.andExpect(jsonPath("$.mentoring.reportsDue").value(2))
				.andExpect(jsonPath("$.mentoring.minutes").value(105))
				.andExpect(jsonPath("$.mentoring.days[0].date").value("2026-11-17"))
				.andExpect(jsonPath("$.mentoring.days[0].meetingCount").value(1))
				.andExpect(jsonPath("$.mentoring.days[0].minutes").value(45))
				.andExpect(jsonPath("$.mentoring.days[1].supportActivityTitles")
						.value(Matchers.contains("Açık ofis saati")))
				.andReturn().getResponse().getContentAsString();

		// Privacy by shape: no mentee, no meeting note, no activity text, and no name-like key under mentoring.
		assertThat(body).doesNotContain("Emre", "Gizlioğlu", "Gizli görüşme notu", "mesajlaşma");
		JsonNode mentoring = jsonMapper.readTree(body).get("mentoring");
		assertThat(keysOf(mentoring)).doesNotContain("mentee", "menteeId", "menteeName", "name", "fullName", "note",
				"body", "notes", "outcome", "discussion", "title");
	}

	@Test
	void theLeaderDecidesAndTheInternMayObjectOnceThenSendAgain() throws Exception {
		saveEntries(mert, lines(teamLine("2026-11-17", mertsTask.getId(), null, 180, null, null)))
				.andExpect(status().isOk());
		submit(mert).andExpect(status().isOk());
		UUID weekId = weekOf(mert).getId();

		decide(zeynep, weekId, "REVISION", null).andExpect(status().isBadRequest());
		decide(zeynep, weekId, "REVISION", "Çıktı bağlantısı eksik")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVISION_REQUESTED"))
				.andExpect(jsonPath("$.decision.reason").value("Çıktı bağlantısı eksik"))
				.andExpect(jsonPath("$.decision.timeMatchesWork").value(true))
				.andExpect(jsonPath("$.decision.outputsSufficient").value(false))
				.andExpect(jsonPath("$.canDecide").value(false));
		decide(zeynep, weekId, "APPROVE", null).andExpect(status().isConflict());
		assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(mert.getId()))
				.anySatisfy(notification -> assertThat(notification.getBody()).contains("revizyon"));

		week(mert, MONDAY)
				.andExpect(jsonPath("$.status").value("REVISION_REQUESTED"))
				.andExpect(jsonPath("$.editable").value(true))
				.andExpect(jsonPath("$.decision.decidedBy.fullName").value("Zeynep Kaya"));
		object(mert, "Bağlantıyı notta paylaştım").andExpect(status().isOk())
				.andExpect(jsonPath("$.objection.text").value("Bağlantıyı notta paylaştım"));
		object(mert, "Bir daha").andExpect(status().isConflict());
		assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(coordinator.getId()))
				.anySatisfy(notification -> assertThat(notification.getBody()).contains("Mert Demir"));

		saveEntries(mert, lines(teamLine("2026-11-17", mertsTask.getId(), null, 180, "https://x.test/l", null)))
				.andExpect(status().isOk());
		submit(mert).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUBMITTED"))
				.andExpect(jsonPath("$.decision").doesNotExist())
				.andExpect(jsonPath("$.objection").doesNotExist());
		decide(zeynep, weekId, "APPROVE", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));
		object(mert, "Onaya itiraz").andExpect(status().isConflict());
	}

	@Test
	void onlyTheRightLeaderReviewsAWeek() throws Exception {
		saveEntries(mert, lines(teamLine("2026-11-17", mertsTask.getId(), null, 60, null, null)))
				.andExpect(status().isOk());
		submit(mert).andExpect(status().isOk());
		UUID mertsWeek = weekOf(mert).getId();
		saveEntries(zeynep, lines(teamLine("2026-11-17", null, "Ekip planı", 60, null, null)))
				.andExpect(status().isOk());
		submit(zeynep).andExpect(status().isOk());
		UUID zeynepsWeek = weekOf(zeynep).getId();

		// Kaan leads the other sub-group: not Mert's week, but a fellow leader's week is his to review.
		leaderWeek(kaan, mertsWeek).andExpect(status().isNotFound());
		leaderWeek(kaan, zeynepsWeek).andExpect(status().isOk());
		leaderWeek(zeynep, zeynepsWeek).andExpect(status().isNotFound());
		decide(kaan, mertsWeek, "APPROVE", null).andExpect(status().isNotFound());
		// A member reviews no one, and neither does someone in another team.
		mockMvc.perform(get("/api/iskur/leader/weeks").param("actorId", mert.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isForbidden());
		leaderWeek(outsider, mertsWeek).andExpect(status().isForbidden());

		// Signed in as Mert while naming Zeynep: the session wins.
		mockMvc.perform(post("/api/iskur/leader/weeks/{weekId}/decision", mertsWeek)
				.with(authentication(signedIn(mert)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionBody(zeynep, "APPROVE", null)))
				.andExpect(status().isForbidden());
		assertThat(weekOf(mert).getStatus()).isEqualTo(IskurWeekStatus.SUBMITTED);
	}

	@Test
	void aOneLeaderTeamsLeaderIsReviewedByTheUnitCoordinator() throws Exception {
		AppUser solo = soloLeader();
		saveEntries(solo, lines(teamLine("2026-11-17", null, "Ekip planı", 120, null, null)))
				.andExpect(status().isOk());
		submit(solo).andExpect(status().isOk());
		UUID weekId = weekOf(solo).getId();
		assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(coordinator.getId()))
				.singleElement()
				.satisfies(notification -> assertThat(notification.getHref())
						.isEqualTo("/coordinator/programs/" + program.getId() + "/iskur/weeks/" + weekId));

		String base = "/api/coordinator/programs/" + program.getId() + "/iskur/weeks";
		mockMvc.perform(get(base).param("actorId", coordinator.getId().toString()).param("weekStart", "2026-11-16")
				.with(httpBasic("test", "test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.people[*].fullName").value(Matchers.contains("Tek Lider")));
		mockMvc.perform(get(base + "/{weekId}", weekId).param("actorId", coordinator.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canDecide").value(true));
		send(post(base + "/{weekId}/decision", weekId), decisionBody(coordinator, "APPROVE", null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));

		// A week a leader can review is not the coordinators' to decide, and a team leader never sees this one.
		saveEntries(mert, lines(teamLine("2026-11-17", null, "Afiş", 60, null, null))).andExpect(status().isOk());
		submit(mert).andExpect(status().isOk());
		mockMvc.perform(get(base + "/{weekId}", weekOf(mert).getId()).param("actorId", coordinator.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isNotFound());
		leaderWeek(zeynep, weekId).andExpect(status().isNotFound());
		// Someone who does not coordinate the program is refused.
		mockMvc.perform(get(base).param("actorId", mert.getId().toString()).with(httpBasic("test", "test")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void everyPlacedInternHasSomeoneToReviewTheirWeek() throws Exception {
		AppUser solo = soloLeader();
		Set<String> covered = new java.util.HashSet<>();
		for (AppUser leader : List.of(zeynep, kaan, solo)) {
			String body = mockMvc.perform(get("/api/iskur/leader/weeks").param("actorId", leader.getId().toString())
					.with(httpBasic("test", "test")))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			jsonMapper.readTree(body).get("people").forEach(row -> covered.add(row.get("fullName").asString()));
		}
		String fallback = mockMvc.perform(get("/api/coordinator/programs/{id}/iskur/weeks", program.getId())
				.param("actorId", coordinator.getId().toString()).with(httpBasic("test", "test")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		jsonMapper.readTree(fallback).get("people").forEach(row -> covered.add(row.get("fullName").asString()));

		assertThat(covered).containsExactlyInAnyOrder("Zeynep Kaya", "Mert Demir", "Kaan Yurt", "Ayşe Yıldız",
				"Deniz Arslan", "Tek Lider");
	}

	@Test
	void reportsTwoWeeksWithoutAMeetingToTheCoordinatorsOnce() {
		// Monday 7 December: the weeks of 23 and 30 November had no meeting for anyone.
		doReturn(Instant.parse("2026-12-07T07:00:00Z")).when(calendar).now();

		assertThat(meetingGaps.run()).isEqualTo(4);
		assertThat(alertRepository.findAll())
				.extracting(IskurMeetingGapAlert::getUserId)
				.containsExactlyInAnyOrder(zeynep.getId(), mert.getId(), ayse.getId(), outsider.getId());
		assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(coordinator.getId()))
				.hasSize(4)
				.anySatisfy(notification -> assertThat(notification.getBody()).contains("Ayşe Yıldız"));
		// Kaan gives no mentoring; a second run tells no one again.
		assertThat(meetingGaps.run()).isZero();
		// A week later the same stretch goes on: its mark moves forward, and no one is told again.
		doReturn(Instant.parse("2026-12-14T07:00:00Z")).when(calendar).now();
		assertThat(meetingGaps.run()).isZero();
		assertThat(alertRepository.findAll()).hasSize(8);
		assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(coordinator.getId())).hasSize(4);

		// A week earlier, Mert's meetings of 17 and 18 November still count, so he is not reported.
		alertRepository.deleteAll();
		doReturn(Instant.parse("2026-11-30T07:00:00Z")).when(calendar).now();
		meetingGaps.run();
		assertThat(alertRepository.findAll()).extracting(IskurMeetingGapAlert::getUserId)
				.doesNotContain(mert.getId(), kaan.getId());
	}

	@Test
	void someoneNotPlacedHasNoWeek() throws Exception {
		week(student("Ece Kurt"), null).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/iskur/week").param("actorId", mert.getId().toString()))
				.andExpect(status().isUnauthorized());
	}

	/** The other team gets one leader, who leads Deniz: a team with no co-leader. */
	private AppUser soloLeader() {
		AppUser solo = student("Tek Lider");
		IskurPlacement denizs = placementRepository.findByProgramIdAndUserId(program.getId(), outsider.getId())
				.orElseThrow();
		IskurPlacement placement = new IskurPlacement(program.getId(), solo.getId(), coordinator.getId(),
				Instant.parse("2026-11-10T09:00:00Z"));
		placement.setTeamId(denizs.getTeamId());
		placement.setTeamRole(IskurTeamRole.LEADER);
		placement.setWeeklyDays((short) 2);
		placementRepository.saveAndFlush(placement);
		denizs.setLeaderId(solo.getId());
		placementRepository.saveAndFlush(denizs);
		return solo;
	}

	private IskurWorkWeek weekOf(AppUser intern) {
		return weekRepository.findByProgramIdAndUserIdAndWeekStart(program.getId(), intern.getId(), MONDAY)
				.orElseThrow();
	}

	private static List<String> keysOf(JsonNode node) {
		List<String> keys = new ArrayList<>();
		collectKeys(node, keys);
		return keys;
	}

	private static void collectKeys(JsonNode node, List<String> keys) {
		if (node.isObject()) {
			for (String name : node.propertyNames()) {
				keys.add(name);
				collectKeys(node.get(name), keys);
			}
		} else if (node.isArray()) {
			for (JsonNode item : node) {
				collectKeys(item, keys);
			}
		}
	}

	private ResultActions week(AppUser actor, LocalDate weekStart) throws Exception {
		MockHttpServletRequestBuilder request = get("/api/iskur/week").param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test"));
		if (weekStart != null) {
			request = request.param("weekStart", weekStart.toString());
		}
		return mockMvc.perform(request);
	}

	private ResultActions leaderWeek(AppUser actor, UUID weekId) throws Exception {
		return mockMvc.perform(get("/api/iskur/leader/weeks/{weekId}", weekId)
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions saveEntries(AppUser actor, String entriesJson) throws Exception {
		return send(put(WEEK + "/entries", MONDAY),
				"{\"actorId\":\"" + actor.getId() + "\",\"entries\":" + entriesJson + "}");
	}

	private ResultActions addSupport(AppUser actor, IskurSupportActivity activity, int minutes) throws Exception {
		return send(post(WEEK + "/support-activity", MONDAY),
				"{\"actorId\":\"%s\",\"activityId\":\"%s\",\"date\":\"2026-11-18\",\"minutes\":%d}"
						.formatted(actor.getId(), activity.getId(), minutes));
	}

	private static String lines(String... lines) {
		return "[" + String.join(",", lines) + "]";
	}

	private static String teamLine(String date, UUID taskId, String title, int minutes, String output, String note) {
		return "{\"date\":\"%s\",\"kind\":\"TEAM\",\"taskId\":%s,\"title\":%s,\"minutes\":%d,\"output\":%s,\"note\":%s}"
				.formatted(date, quoted(taskId), quoted(title), minutes, quoted(output), quoted(note));
	}

	private static String supportLine(String date, UUID activityId, int minutes) {
		return "{\"date\":\"%s\",\"kind\":\"SUPPORT_ACTIVITY\",\"supportActivityId\":\"%s\",\"minutes\":%d}"
				.formatted(date, activityId, minutes);
	}

	private static String quoted(Object value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private ResultActions submit(AppUser actor) throws Exception {
		return send(post(WEEK + "/submit", MONDAY), "{\"actorId\":\"" + actor.getId() + "\"}");
	}

	private ResultActions object(AppUser actor, String text) throws Exception {
		return send(post(WEEK + "/objection", MONDAY),
				"{\"actorId\":\"" + actor.getId() + "\",\"objection\":\"" + text + "\"}");
	}

	private ResultActions decide(AppUser actor, UUID weekId, String decision, String reason) throws Exception {
		return send(post("/api/iskur/leader/weeks/{weekId}/decision", weekId), decisionBody(actor, decision, reason));
	}

	private static String decisionBody(AppUser actor, String decision, String reason) {
		return ("{\"actorId\":\"%s\",\"decision\":\"%s\",\"timeMatchesWork\":true,\"effortDocumented\":true,"
				+ "\"outputsSufficient\":false,\"reason\":%s}").formatted(actor.getId(), decision, quoted(reason));
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
		return mockMvc.perform(request.with(httpBasic("test", "test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private IskurTask task(AppUser assignee, IskurTeam team, String title) {
		IskurTask task = new IskurTask(program.getId(), team.getId(), assignee.getId(), zeynep.getId(), NOW);
		task.setTitle(title);
		task.setKind(IskurTaskKind.EVENT);
		task.setEstimatedMinutes(120);
		task.setDueDate(LocalDate.of(2026, 11, 20));
		return taskRepository.saveAndFlush(task);
	}

	private IskurSupportActivity support(String title, boolean active) {
		IskurSupportActivity activity = new IskurSupportActivity(program.getId(), title, 0, coordinator.getId(), NOW);
		activity.setActive(active);
		return supportRepository.saveAndFlush(activity);
	}

	private void place(AppUser who, IskurTeam into, IskurTeamRole role, AppUser leader, int days, Integer mentoring) {
		IskurPlacement placement = new IskurPlacement(program.getId(), who.getId(), coordinator.getId(),
				Instant.parse("2026-11-10T09:00:00Z"));
		placement.setTeamId(into.getId());
		placement.setTeamRole(role);
		placement.setLeaderId(leader == null ? null : leader.getId());
		placement.setWeeklyDays((short) days);
		placement.setMentoringMinutes(mentoring);
		placementRepository.saveAndFlush(placement);
	}

	private MentorshipRequest request(AppUser requester, AppUser peerMentor) {
		Organization team = new Organization();
		team.setName(requester.getFullName());
		team = organizationRepository.saveAndFlush(team);
		MentorshipRequest request = new MentorshipRequest();
		request.setRequester(requester);
		request.setMentor(peerMentor);
		request.setOrganization(team);
		request.setProgram(program);
		request.setRequestedForName(requester.getFullName());
		request.setSupportArea("CAREER_INTERNSHIP");
		request.setSummary("Staj başvurusu");
		request.setUrgency(MentorshipRequestUrgency.NOT_URGENT);
		request.setMentorshipFormat(MentorshipRequestFormat.SINGLE_MEETING);
		request.setMeetingMode(MeetingMode.IN_PERSON);
		request.setStatus(MentorshipRequestStatus.ACCEPTED);
		request.setAcceptedAt(NOW.minus(Duration.ofDays(7)));
		return mentorshipRequestRepository.saveAndFlush(request);
	}

	private Meeting meeting(MentorshipRequest request, Instant at) {
		Meeting meeting = new Meeting();
		meeting.setOrganization(request.getOrganization());
		meeting.setMentor(request.getMentor());
		meeting.setMentorshipRequest(request);
		meeting.setScheduledAt(at);
		meeting.setDurationMinutes(30);
		meeting.setStatus(MeetingStatus.COMPLETED);
		return meetingRepository.saveAndFlush(meeting);
	}

	private void report(Meeting meeting, AppUser peerMentor, AppUser mentee, String note) {
		PeerMeetingReport report = new PeerMeetingReport();
		report.setId(UUID.randomUUID());
		report.setMeetingId(meeting.getId());
		report.setMentorId(peerMentor.getId());
		report.setMenteeId(mentee.getId());
		report.setProgramId(program.getId());
		report.setMeetingType(PeerMeetingType.FIRST);
		report.setMeetingFormat(PeerMeetingFormat.IN_PERSON);
		report.setActualDurationMinutes(35);
		report.setDiscussion(note);
		report.setOutcome(note);
		report.setCreatedAt(NOW);
		report.submit(NOW);
		meetingReportRepository.saveAndFlush(report);
	}

	private void activity(AppUser peerMentor, LocalDate date, String title, int minutes) {
		PeerActivity activity = new PeerActivity();
		activity.setId(UUID.randomUUID());
		activity.setMentorId(peerMentor.getId());
		activity.setProgramId(program.getId());
		activity.setActivityDate(date);
		activity.setActivityType(PeerActivityType.MESSAGE_FOLLOW_UP);
		activity.setTitle(title);
		activity.setDurationMinutes(minutes);
		activity.setCreatedAt(NOW);
		activityRepository.saveAndFlush(activity);
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

	private AppUser student(String name) {
		AppUser user = user(name, UserRole.MENTEE, program);
		InstitutionPerson person = new InstitutionPerson("odtu-test", "student-" + user.getId());
		person.setFullName(user.getFullName());
		person.setEmail(user.getEmail());
		person.setSource(InstitutionDataSource.IMPORT);
		person.setSyncedAt(NOW);
		person.setKinds(Set.of(IdentityKind.STUDENT));
		person.setUser(user);
		institutionPersonRepository.saveAndFlush(person);
		return user;
	}

	private AppUser peerMentor(String name) {
		AppUser user = student(name);
		user.grant(UserRole.MENTOR, PermissionSource.BADGE, NOW.minus(Duration.ofDays(30)));
		user = userRepository.saveAndFlush(user);
		ProgramMentor membership = new ProgramMentor();
		membership.setId(UUID.randomUUID());
		membership.setProgramId(program.getId());
		membership.setMentorId(user.getId());
		membership.setStatus(ProgramMentorStatus.ACCEPTED);
		membership.setInvitedBy(ProgramMentorInvitedBy.COORDINATOR);
		membership.setCreatedAt(NOW);
		membership.setDecidedAt(NOW);
		programMentorRepository.saveAndFlush(membership);
		return user;
	}

	private Program peerProgram() {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName("İŞKUR Güz 2026");
		created.setCreatedBy(coordinator.getId());
		created.setCreatedAt(NOW);
		created.setStatus(ProgramStatus.ACTIVE);
		created.setProgramType(ProgramType.PEER_MENTORING);
		return programRepository.saveAndFlush(created);
	}

	private void coordinatorOf(Program coordinated, AppUser user) {
		ProgramCoordinator link = new ProgramCoordinator();
		link.setId(UUID.randomUUID());
		link.setProgramId(coordinated.getId());
		link.setUserId(user.getId());
		link.setAddedBy(user.getId());
		link.setCreatedAt(NOW);
		programCoordinatorRepository.saveAndFlush(link);
	}

	private static UsernamePasswordAuthenticationToken signedIn(AppUser user) {
		return new UsernamePasswordAuthenticationToken(new AppUserPrincipal(
				user.getId(), user.getEmail(), user.getRole(), user.getStatus(), "iskur-test-uid"), null, List.of());
	}
}
