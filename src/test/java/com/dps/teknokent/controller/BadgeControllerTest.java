package com.dps.teknokent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dps.teknokent.config.SecurityConfig;
import com.dps.teknokent.domain.BadgeCode;
import com.dps.teknokent.domain.BadgeKind;
import com.dps.teknokent.domain.BadgeSource;
import com.dps.teknokent.domain.GateBadgeState;
import com.dps.teknokent.domain.PrismDecision;
import com.dps.teknokent.domain.PrismDecisionOutcome;
import com.dps.teknokent.dto.responses.AchievementProgressResponse;
import com.dps.teknokent.dto.responses.GateBadgeRowResponse;
import com.dps.teknokent.dto.responses.PrismDecisionResultResponse;
import com.dps.teknokent.dto.responses.PublicBadgeResponse;
import com.dps.teknokent.service.AchievementService;
import com.dps.teknokent.service.BadgeService;
import com.dps.teknokent.service.PrismTrainingService;

@WebMvcTest(BadgeController.class)
@Import(SecurityConfig.class)
// These endpoints belong to the ODTÜ deployment; the Teknokent default answers them with a 404.
@TestPropertySource(properties = "app.edition=ODTU")
class BadgeControllerTest {

	private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
	private static final UUID PROGRAM = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
	private static final UUID AWARD = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BadgeService badgeService;

	@MockitoBean
	private AchievementService achievementService;

	@MockitoBean
	private PrismTrainingService prismTrainingService;

	@Test
	void needsTheServiceCredential() throws Exception {
		mockMvc.perform(get("/api/users/" + STUDENT + "/badges")).andExpect(status().isUnauthorized());
		verifyNoInteractions(badgeService);
	}

	@Test
	void showsAPersonsActiveBadges() throws Exception {
		when(badgeService.publicOf(STUDENT)).thenReturn(List.of(new PublicBadgeResponse(BadgeCode.PEER_MENTOR,
				BadgeKind.GATE, PROGRAM, Instant.parse("2026-11-08T10:00:00Z"))));

		mockMvc.perform(get("/api/users/" + STUDENT + "/badges").with(user("proxy")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].code").value("PEER_MENTOR"))
				.andExpect(jsonPath("$[0].kind").value("GATE"))
				.andExpect(jsonPath("$[0].reason").doesNotExist());
	}

	@Test
	void showsTheActorsAchievementProgress() throws Exception {
		when(achievementService.mine(ACTOR)).thenReturn(List.of(new AchievementProgressResponse(BadgeCode.SESSIONS_10,
				false, null, 4, 10, null, null)));

		mockMvc.perform(get("/api/badges/mine/achievements").param("actorId", ACTOR.toString()).with(user("proxy")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].code").value("SESSIONS_10"))
				.andExpect(jsonPath("$[0].current").value(4))
				.andExpect(jsonPath("$[0].target").value(10));
	}

	@Test
	void awardsTheGateBadgeForTheActor() throws Exception {
		when(badgeService.awardGate(ACTOR, PROGRAM, STUDENT)).thenReturn(row(GateBadgeState.ACTIVE));

		mockMvc.perform(post("/api/coordinator/programs/" + PROGRAM + "/badges/awards")
				.with(user("proxy"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"" + ACTOR + "\",\"userId\":\"" + STUDENT + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("ACTIVE"));
		verify(badgeService).awardGate(ACTOR, PROGRAM, STUDENT);
	}

	@Test
	void sendsTheProgramsPrismDecisionsForTheActorWithAResultPerAddress() throws Exception {
		when(prismTrainingService.decide(any(), any(), any(), any())).thenReturn(List.of(
				new PrismDecisionResultResponse("stajyer@example.test", PrismDecisionOutcome.APPROVED, "Stajyer"),
				new PrismDecisionResultResponse("yok@example.test", PrismDecisionOutcome.NOT_FOUND, null)));

		mockMvc.perform(post("/api/coordinator/programs/" + PROGRAM + "/badges/prism-decisions")
				.with(user("proxy"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"" + ACTOR + "\",\"decision\":\"APPROVED\",\"entries\":["
						+ "{\"email\":\"stajyer@example.test\",\"score\":\"86\"},{\"email\":\"yok@example.test\"}]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].outcome").value("APPROVED"))
				.andExpect(jsonPath("$[1].outcome").value("NOT_FOUND"))
				.andExpect(jsonPath("$[1].fullName").doesNotExist());
		verify(prismTrainingService).decide(org.mockito.ArgumentMatchers.eq(ACTOR),
				org.mockito.ArgumentMatchers.eq(PROGRAM), org.mockito.ArgumentMatchers.eq(PrismDecision.APPROVED),
				any());
	}

	@Test
	void aPrismDecisionWithoutAddressesOrWithALongScoreNeverReachesTheService() throws Exception {
		for (String body : List.of("{\"decision\":\"APPROVED\",\"entries\":[]}",
				"{\"decision\":\"RETAKE\",\"entries\":[{\"email\":\"a@example.test\",\"score\":\""
						+ "x".repeat(33) + "\"}]}",
				"{\"entries\":[{\"email\":\"a@example.test\"}]}")) {
			mockMvc.perform(post("/api/coordinator/programs/" + PROGRAM + "/badges/prism-decisions")
					.with(user("proxy"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
					.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(prismTrainingService);
	}

	@Test
	void aRevokeWithoutAReasonNeverReachesTheService() throws Exception {
		mockMvc.perform(post("/api/coordinator/programs/" + PROGRAM + "/badges/awards/" + AWARD + "/revoke")
				.with(user("proxy"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"" + ACTOR + "\",\"reason\":\"  \"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(badgeService);
	}

	@Test
	void filtersTheRosterByState() throws Exception {
		when(badgeService.roster(ACTOR, PROGRAM, GateBadgeState.ACTIVE))
				.thenReturn(List.of(row(GateBadgeState.ACTIVE)));

		mockMvc.perform(get("/api/coordinator/programs/" + PROGRAM + "/badges/roster")
				.param("actorId", ACTOR.toString())
				.param("state", "ACTIVE")
				.with(user("proxy")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].userId").value(STUDENT.toString()));
		verify(badgeService).roster(any(), any(), any());
	}

	@Test
	void sendsEachRostersProfileAsKatilimcilarReadsIt() throws Exception {
		GateBadgeRowResponse plain = row(GateBadgeState.ACTIVE);
		GateBadgeRowResponse withProfile = new GateBadgeRowResponse(plain.userId(), plain.fullName(), plain.email(),
				plain.programId(), plain.programName(), plain.state(), plain.awardId(), plain.source(),
				plain.awardedAt(), plain.revokedAt(), plain.reason(), plain.training(),
				new GateBadgeRowResponse.Profile("Endüstri Mühendisliği", 4, true,
						List.of(com.dps.teknokent.domain.PeerTopicCategory.CAREER_INTERNSHIP), 3, 4),
				null, true, true);
		when(badgeService.roster(ACTOR, PROGRAM, null)).thenReturn(List.of(withProfile));

		mockMvc.perform(get("/api/coordinator/programs/" + PROGRAM + "/badges/roster")
				.param("actorId", ACTOR.toString())
				.with(user("proxy")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].profile.department").value("Endüstri Mühendisliği"))
				.andExpect(jsonPath("$[0].profile.classYear").value(4))
				.andExpect(jsonPath("$[0].profile.mentorProfile").value(true))
				.andExpect(jsonPath("$[0].profile.categories[0]").value("CAREER_INTERNSHIP"))
				.andExpect(jsonPath("$[0].profile.activeMentees").value(3))
				.andExpect(jsonPath("$[0].profile.menteeCapacity").value(4))
				.andExpect(jsonPath("$[0].iskurIntern").value(true));
	}

	private static GateBadgeRowResponse row(GateBadgeState state) {
		return new GateBadgeRowResponse(STUDENT, "Zeynep Öğrenci", "zeynep@odtu-dev.invalid", PROGRAM,
				"İŞKUR 2026 Bahar", state, AWARD, BadgeSource.COORDINATOR, Instant.parse("2026-11-08T10:00:00Z"),
				null, null, GateBadgeRowResponse.Training.NONE, null, null, true, false);
	}
}
