package com.dps.teknokent.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.dps.teknokent.config.SecurityConfig;
import com.dps.teknokent.dto.responses.InstitutionLoginStartResponse;
import com.dps.teknokent.service.InstitutionLoginService;

@WebMvcTest(InstitutionLoginController.class)
@Import(SecurityConfig.class)
// These endpoints belong to the ODTÜ deployment; the Teknokent default answers them with a 404.
@TestPropertySource(properties = "app.edition=ODTU")
class InstitutionLoginControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private InstitutionLoginService institutionLoginService;

	// Only the Next.js proxy, on the service credentials, may start or finish an institution sign-in.
	@Test
	void nothingHereIsPublic() throws Exception {
		mockMvc.perform(post("/api/auth/institution/odtu/start")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/institution/exchange")
				.contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"c\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void startHandsBackWhereToGoAndTheState() throws Exception {
		when(institutionLoginService.start("odtu"))
				.thenReturn(new InstitutionLoginStartResponse("/login/odtu/dev?state=s1", "s1"));

		mockMvc.perform(post("/api/auth/institution/odtu/start").with(user("service")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.redirectUrl").value("/login/odtu/dev?state=s1"))
				.andExpect(jsonPath("$.state").value("s1"));
	}

	@Test
	void theCallbackNeedsItsState() throws Exception {
		mockMvc.perform(post("/api/auth/institution/odtu/callback").with(user("service"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"params\":{\"persona\":\"dev-ogrenci-1\"}}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void theCallbackAnswersWithTheOneTimeCode() throws Exception {
		when(institutionLoginService.complete("odtu", "s1", Map.of("persona", "dev-ogrenci-1"))).thenReturn("c1");

		mockMvc.perform(post("/api/auth/institution/odtu/callback").with(user("service"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"state\":\"s1\",\"params\":{\"persona\":\"dev-ogrenci-1\"}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("c1"));
	}

	// The login page asks this before it shows "ODTÜ ile giriş yap"; ids only, and only on the service credentials.
	@Test
	void namesTheLoginsThatAreOnAndNothingElseAboutThem() throws Exception {
		when(institutionLoginService.enabledProviders()).thenReturn(List.of("odtu-cas"));

		mockMvc.perform(get("/api/auth/institution/providers")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/auth/institution/providers").with(user("service")))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"providers\":[\"odtu-cas\"]}", JsonCompareMode.STRICT));
	}

	@Test
	void theCasCallbackHandsItsTicketToTheService() throws Exception {
		when(institutionLoginService.complete("odtu-cas", "s1", Map.of("ticket", "ST-1"))).thenReturn("c1");

		mockMvc.perform(post("/api/auth/institution/odtu-cas/callback").with(user("service"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"s1\",\"params\":{\"ticket\":\"ST-1\"}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("c1"));
	}

	@Test
	void theCodeIsTradedForACustomToken() throws Exception {
		when(institutionLoginService.exchange(eq("c1"))).thenReturn("custom-token");

		mockMvc.perform(post("/api/auth/institution/exchange").with(user("service"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"c1\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customToken").value("custom-token"));
	}

	@Test
	void thePersonaListDoesNotExistWhenTheStubIsOff() throws Exception {
		mockMvc.perform(get("/api/auth/institution/odtu/dev-personas").with(user("service")))
				.andExpect(status().isNotFound());
	}
}
