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
import com.dps.teknokent.domain.PermissionSource;
import com.dps.teknokent.domain.PrismDecision;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurInternEmail;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Notification;
import com.dps.teknokent.entity.PrismTrainingReview;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinator;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurInternEmailRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.NotificationRepository;
import com.dps.teknokent.repository.PrismTrainingReviewRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramRepository;

/**
 * İŞKUR work model, 27 Nov: an intern without the badge tells the coordinator why they could not pass the
 * assessment; the coordinator sets their weekly mentoring time, may reopen the test, and writes a note.
 */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class IskurAssessmentStatementIntegrationTest {

	private static final String MINE = "/api/iskur/assessment-statement";
	private static final String LIST = "/api/coordinator/programs/{programId}/iskur/assessment-statements";
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
	private IskurPlacementRepository placementRepository;
	@Autowired
	private PrismTrainingReviewRepository reviewRepository;
	@Autowired
	private NotificationRepository notificationRepository;

	private AppUser selin;
	private AppUser kaan;
	private AppUser otherCoordinator;
	private Program program;
	private IskurTeam media;
	private AppUser ece;
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
		ece = user("Ece Kaya", UserRole.MENTEE, program);
		burak = user("Burak Tan", UserRole.MENTEE, program);
		place(ece, 3, null).andExpect(status().isOk());
	}

	@Test
	void anInternWithoutTheBadgeSendsAStatementAndTheUnitCoordinatorIsTold() throws Exception {
		mine(ece)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canSend").value(true))
				.andExpect(jsonPath("$.latest").doesNotExist());

		send(ece, "FAILED", "  Testi iki kez denedim, 60 puanın altında kaldım.  ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canSend").value(false))
				.andExpect(jsonPath("$.latest.status").value("PENDING"))
				.andExpect(jsonPath("$.latest.reason").value("FAILED"))
				.andExpect(jsonPath("$.latest.text").value("Testi iki kez denedim, 60 puanın altında kaldım."));
		send(ece, "OTHER", "Bir daha").andExpect(status().isConflict());
		send(ece, "OTHER", " ").andExpect(status().isBadRequest());

		assertThat(titles(kaan)).containsExactly("Değerlendirme açıklaması");
		assertThat(titles(selin)).isEmpty();
		list(selin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pendingCount").value(1))
				.andExpect(jsonPath("$.statements[0].fullName").value("Ece Kaya"))
				.andExpect(jsonPath("$.statements[0].teamName").value("Medya ve Kreatif Üretim"))
				.andExpect(jsonPath("$.statements[0].weeklyDays").value(3))
				.andExpect(jsonPath("$.statements[0].currentMentoringMinutes").value(360))
				.andExpect(jsonPath("$.statements[0].mentoringIsDefault").value(true))
				.andExpect(jsonPath("$.statements[0].trainingState").value("NOT_AVAILABLE"))
				.andExpect(jsonPath("$.statements[0].peerMentor").value(false));
	}

	@Test
	void settingNoMentoringMovesTheWholeWeekToTeamWorkAndTellsTheIntern() throws Exception {
		String id = idOf(send(ece, "NO_MENTORING", "Mentor olmak istemiyorum, ekip işine odaklanmak istiyorum."));
		decide(kaan, id, "SET", 0, false, "Tüm haftan ekip çalışmasında.")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pendingCount").value(0))
				.andExpect(jsonPath("$.statements[0].status").value("DECIDED"))
				.andExpect(jsonPath("$.statements[0].mentoringDecision").value("SET"))
				.andExpect(jsonPath("$.statements[0].mentoringMinutes").value(0))
				.andExpect(jsonPath("$.statements[0].currentMentoringMinutes").value(0))
				.andExpect(jsonPath("$.statements[0].decidedByName").value("Kaan Yurt"));
		decide(selin, id, "KEEP", null, false, "Tekrar").andExpect(status().isConflict());

		assertThat(placementRepository.findByProgramIdAndUserId(program.getId(), ece.getId()).orElseThrow()
				.getMentoringMinutes()).isZero();
		mine(ece)
				.andExpect(jsonPath("$.canSend").value(true))
				.andExpect(jsonPath("$.latest.mentoringMinutes").value(0))
				.andExpect(jsonPath("$.latest.decisionNote").value("Tüm haftan ekip çalışmasında."));
		assertThat(titles(ece)).containsExactly("Açıklaman yanıtlandı");
	}

	@Test
	void backToTheDefaultClearsTheOverride() throws Exception {
		place(ece, 3, 120).andExpect(status().isOk());
		String id = idOf(send(ece, "PERSONAL", "Bu ay ders yüküm çok fazla."));
		decide(kaan, id, "DEFAULT", null, false, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statements[0].mentoringMinutes").value(360))
				.andExpect(jsonPath("$.statements[0].mentoringIsDefault").value(true));
		assertThat(placementRepository.findByProgramIdAndUserId(program.getId(), ece.getId()).orElseThrow()
				.getMentoringMinutes()).isNull();
	}

	@Test
	void aDecisionMustDoSomethingAndMinutesMustFitTheWeek() throws Exception {
		String id = idOf(send(ece, "FAILED", "Geçemedim."));
		decide(kaan, id, "KEEP", null, false, "  ").andExpect(status().isBadRequest());
		decide(kaan, id, "SET", null, false, null).andExpect(status().isBadRequest());
		decide(kaan, id, "SET", 45, false, null).andExpect(status().isBadRequest());
		decide(kaan, id, "SET", 1380, false, null).andExpect(status().isBadRequest());
		list(kaan).andExpect(jsonPath("$.statements[0].status").value("PENDING"));
		assertThat(placementRepository.findByProgramIdAndUserId(program.getId(), ece.getId()).orElseThrow()
				.getMentoringMinutes()).isNull();
	}

	@Test
	void theTestReopensOnlyWhileTheInternWaitsForAPrismDecision() throws Exception {
		String id = idOf(send(ece, "FAILED", "Geçemedim, tekrar denemek istiyorum."));
		decide(kaan, id, "KEEP", null, true, null).andExpect(status().isBadRequest());

		PrismTrainingReview review = new PrismTrainingReview();
		review.setId(UUID.randomUUID());
		review.setProgramId(program.getId());
		review.setUserId(ece.getId());
		review.setCompletedAt(Instant.now());
		reviewRepository.saveAndFlush(review);
		list(kaan).andExpect(jsonPath("$.statements[0].trainingState").value("PRISM_PENDING"));

		decide(kaan, id, "KEEP", null, true, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statements[0].retakeOpened").value(true));
		PrismTrainingReview after = reviewRepository.findByProgramIdAndUserId(program.getId(), ece.getId())
				.orElseThrow();
		assertThat(after.getDecision()).isEqualTo(PrismDecision.RETAKE);
		assertThat(after.getDecidedBy()).isEqualTo(kaan.getId());
		// One message for the whole decision, not a second "take the test again".
		assertThat(titles(ece)).containsExactly("Açıklaman yanıtlandı");
	}

	@Test
	void onlyAPlacedInternWithoutTheBadgeMaySend() throws Exception {
		send(burak, "OTHER", "Henüz ekibim yok").andExpect(status().isBadRequest());
		ece.grant(UserRole.MENTOR, PermissionSource.BADGE, Instant.now());
		userRepository.saveAndFlush(ece);
		mine(ece).andExpect(jsonPath("$.canSend").value(false));
		send(ece, "OTHER", "Rozetim var").andExpect(status().isBadRequest());
		mine(selin).andExpect(status().isNotFound());
	}

	@Test
	void unplacingTheInternCancelsTheirStatement() throws Exception {
		send(ece, "NOT_STARTED", "Zaman bulamadım.").andExpect(status().isOk());
		send(delete(PLACE, program.getId(), ece.getId()).param("actorId", selin.getId().toString()), null)
				.andExpect(status().isOk());
		list(selin)
				.andExpect(jsonPath("$.pendingCount").value(0))
				.andExpect(jsonPath("$.statements[0].status").value("CANCELLED"));
	}

	@Test
	void onlyTheProgramsCoordinatorsSeeAndDecide() throws Exception {
		String id = idOf(send(ece, "NO_ACCESS", "Prism'e giriş yapamadım."));
		list(otherCoordinator).andExpect(status().isBadRequest());
		decide(otherCoordinator, id, "SET", 0, false, null).andExpect(status().isBadRequest());
		list(ece).andExpect(status().is4xxClientError());
		decide(ece, id, "SET", 0, false, null).andExpect(status().is4xxClientError());
		mine(ece).andExpect(jsonPath("$.latest.status").value("PENDING"));
	}

	@Test
	void anArchivedProgramsStatementsCannotBeDecided() throws Exception {
		String id = idOf(send(ece, "FAILED", "Geçemedim."));
		program.setStatus(ProgramStatus.COMPLETED);
		programRepository.saveAndFlush(program);
		decide(kaan, id, "SET", 0, false, null).andExpect(status().isBadRequest());
		list(kaan).andExpect(jsonPath("$.statements[0].status").value("PENDING"));
	}

	private ResultActions mine(AppUser actor) throws Exception {
		return mockMvc.perform(get(MINE).param("actorId", actor.getId().toString()).with(httpBasic("test", "test")));
	}

	private ResultActions send(AppUser actor, String reason, String text) throws Exception {
		return send(post(MINE), "{\"actorId\":\"%s\",\"reason\":\"%s\",\"text\":\"%s\"}"
				.formatted(actor.getId(), reason, text));
	}

	private ResultActions list(AppUser actor) throws Exception {
		return mockMvc.perform(get(LIST, program.getId()).param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions decide(AppUser actor, String id, String mentoring, Integer minutes, boolean retake,
			String note) throws Exception {
		return send(post(LIST + "/{id}/decide", program.getId(), id),
				"{\"actorId\":\"%s\",\"mentoring\":\"%s\",\"mentoringMinutes\":%s,\"openRetake\":%s,\"note\":%s}"
						.formatted(actor.getId(), mentoring, minutes, retake,
								note == null ? "null" : "\"" + note + "\""));
	}

	private ResultActions place(AppUser who, Integer days, Integer mentoring) throws Exception {
		return send(put(PLACE, program.getId(), who.getId()),
				"{\"actorId\":\"%s\",\"teamId\":\"%s\",\"role\":\"%s\",\"leaderId\":null,\"weeklyDays\":%s,\"mentoringMinutes\":%s}"
						.formatted(selin.getId(), media.getId(), IskurTeamRole.MEMBER, days, mentoring));
	}

	private static String idOf(ResultActions result) throws Exception {
		String json = result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		int at = json.indexOf("\"latest\":{\"id\":\"") + 16;
		return json.substring(at, json.indexOf('"', at));
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
		MockHttpServletRequestBuilder withAuth = request.with(httpBasic("test", "test"));
		if (body != null) {
			withAuth = withAuth.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(withAuth);
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
