package com.dps.teknokent.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dps.teknokent.config.SecurityConfig;
import com.dps.teknokent.service.OfficeHoursCalendarFeedService;

/** With the switch at its default (off), the calendar routes answer as if they were not there. */
@WebMvcTest(OfficeHoursCalendarFeedController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.edition=ODTU")
class OfficeHoursCalendarFeedDisabledControllerTest {

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000f101");
	private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-00000000f102");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OfficeHoursCalendarFeedService feedService;

	@Test
	void everyLinkAndFeedRouteIs404AndNothingIsMade() throws Exception {
		MockHttpServletRequestBuilder[] routes = {
				get("/api/office-hours/users/{userId}/calendar-feed", USER_ID),
				post("/api/office-hours/users/{userId}/calendar-feed/reset", USER_ID),
				get("/api/office-hours/users/{userId}/courses/{courseId}/calendar-feed", USER_ID, COURSE_ID),
				post("/api/office-hours/users/{userId}/courses/{courseId}/calendar-feed/reset", USER_ID, COURSE_ID),
				get("/api/office-hours/calendar-feeds/{token}", "b".repeat(43)) };

		for (MockHttpServletRequestBuilder route : routes) {
			mockMvc.perform(route.with(user("frontend").roles("SERVICE"))).andExpect(status().isNotFound());
		}
		verifyNoInteractions(feedService);
	}
}
