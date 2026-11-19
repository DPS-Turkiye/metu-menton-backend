package com.dps.teknokent.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import com.dps.teknokent.domain.TrainingState;
import com.dps.teknokent.dto.responses.PrismLinkResponse;
import com.dps.teknokent.dto.responses.StudentTrainingResponse;
import com.dps.teknokent.service.PrismTrainingService;
import com.dps.teknokent.service.TrainingService;

@WebMvcTest(TrainingController.class)
@Import(SecurityConfig.class)
// These endpoints belong to the ODTÜ deployment; the Teknokent default answers them with a 404.
@TestPropertySource(properties = "app.edition=ODTU")
class TrainingControllerTest {

	private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
	private static final UUID PROGRAM = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final String LINK = "https://prism.example.test/akran-mentor";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TrainingService trainingService;

	@MockitoBean
	private PrismTrainingService prismTrainingService;

	@Test
	void theEditorReadsAndSavesTheProgramsPrismLinkForTheActor() throws Exception {
		PrismLinkResponse saved = new PrismLinkResponse(LINK, Instant.parse("2026-11-20T08:00:00Z"), false);
		when(prismTrainingService.link(ACTOR, PROGRAM)).thenReturn(saved);
		when(prismTrainingService.saveLink(ACTOR, PROGRAM, LINK)).thenReturn(saved);

		mockMvc.perform(get("/api/coordinator/programs/" + PROGRAM + "/training/prism")
				.param("actorId", ACTOR.toString())
				.with(user("proxy")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value(LINK))
				.andExpect(jsonPath("$.inAppQuiz").value(false));
		mockMvc.perform(put("/api/coordinator/programs/" + PROGRAM + "/training/prism")
				.with(user("proxy"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"" + ACTOR + "\",\"url\":\"" + LINK + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value(LINK));
		verify(prismTrainingService).saveLink(ACTOR, PROGRAM, LINK);
	}

	@Test
	void anEmptyLinkNeverReachesTheService() throws Exception {
		mockMvc.perform(put("/api/coordinator/programs/" + PROGRAM + "/training/prism")
				.with(user("proxy"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"" + ACTOR + "\",\"url\":\" \"}"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(prismTrainingService);
	}

	@Test
	void theStudentSaysTheyFinishedPrism() throws Exception {
		when(trainingService.prismCompleted(ACTOR)).thenReturn(new StudentTrainingResponse(
				TrainingState.PRISM_PENDING, PROGRAM, "İŞKUR 2026 Bahar", null, null, false, 0, null, null, 8, 10, LINK,
				Instant.parse("2026-11-20T08:00:00Z")));

		mockMvc.perform(post("/api/training/peer/prism-completed")
				.with(user("proxy"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"" + ACTOR + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("PRISM_PENDING"))
				.andExpect(jsonPath("$.prismUrl").value(LINK));
	}
}
