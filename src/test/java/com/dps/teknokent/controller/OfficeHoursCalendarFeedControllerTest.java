package com.dps.teknokent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.dps.teknokent.config.SecurityConfig;
import com.dps.teknokent.domain.AppLanguage;
import com.dps.teknokent.dto.responses.OfficeHoursCalendarFeedResponse;
import com.dps.teknokent.service.OfficeHoursCalendarFeedService;

@WebMvcTest(OfficeHoursCalendarFeedController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = { "app.edition=ODTU", "app.office-hours.calendar-feed.enabled=true" })
class OfficeHoursCalendarFeedControllerTest {

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000f101");
	private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-00000000f102");
	private static final String TOKEN = "b".repeat(43);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OfficeHoursCalendarFeedService feedService;

	private static RequestPostProcessor frontendServer() {
		return user("frontend").roles("SERVICE");
	}

	@Test
	void handsTheFrontendServerThePersonalAndCourseTokens() throws Exception {
		when(feedService.personalFeed(USER_ID)).thenReturn(new OfficeHoursCalendarFeedResponse(TOKEN));
		when(feedService.resetCourseFeed(USER_ID, COURSE_ID)).thenReturn(new OfficeHoursCalendarFeedResponse("new"));

		mockMvc.perform(get("/api/office-hours/users/{userId}/calendar-feed", USER_ID).with(frontendServer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value(TOKEN));
		mockMvc.perform(post("/api/office-hours/users/{userId}/courses/{courseId}/calendar-feed/reset", USER_ID,
				COURSE_ID).with(frontendServer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("new"));
	}

	@Test
	void servesTheFeedAsAnUncachedCalendarInTheLinksLanguage() throws Exception {
		when(feedService.feed(TOKEN, AppLanguage.EN)).thenReturn(Optional.of("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"));

		mockMvc.perform(get("/api/office-hours/calendar-feeds/{token}", TOKEN).param("lang", "en")
				.with(frontendServer()))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/calendar"))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().string("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"));
	}

	@Test
	void anyOtherLanguageIsTurkishAndAnUnknownLinkIs404() throws Exception {
		when(feedService.feed(any(), any())).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/office-hours/calendar-feeds/{token}", TOKEN).param("lang", "de")
				.with(frontendServer()))
				.andExpect(status().isNotFound());
		verify(feedService).feed(TOKEN, AppLanguage.TR);
	}

	@Test
	void onlyTheFrontendServerReachesTheFeed() throws Exception {
		mockMvc.perform(get("/api/office-hours/calendar-feeds/{token}", TOKEN))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/office-hours/calendar-feeds/{token}", TOKEN)
				.with(authentication(new UsernamePasswordAuthenticationToken("someone", null,
						List.of(new SimpleGrantedAuthority("ROLE_MENTEE"))))))
				.andExpect(status().isForbidden());
		verifyNoInteractions(feedService);
	}
}
