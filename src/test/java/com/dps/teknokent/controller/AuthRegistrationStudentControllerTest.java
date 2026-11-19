package com.dps.teknokent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dps.teknokent.config.SecurityConfig;
import com.dps.teknokent.domain.StudentYear;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.requests.RegisterStudentRequest;
import com.dps.teknokent.dto.responses.UserResponse;
import com.dps.teknokent.security.FirebaseIdentity;
import com.dps.teknokent.service.ProgramEligibilityService;
import com.dps.teknokent.service.RegistrationService;
import com.dps.teknokent.service.StudentSignupService;

/** ODTÜ's student sign-up; the path is closed on the Teknokent edition the other controller tests run as. */
@WebMvcTest(controllers = AuthRegistrationController.class, properties = "app.edition=ODTU")
@Import(SecurityConfig.class)
class AuthRegistrationStudentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegistrationService registrationService;

	@MockitoBean
	private ProgramEligibilityService programEligibilityService;

	@MockitoBean
	private StudentSignupService studentSignupService;

	private static UsernamePasswordAuthenticationToken registering(String email) {
		return new UsernamePasswordAuthenticationToken(
				new FirebaseIdentity("uid-1", email, true), null,
				List.of(new SimpleGrantedAuthority("ROLE_REGISTERING")));
	}

	private static final String STUDENT_FORM = """
			{
			  "firstName": "Deniz",
			  "lastName": "Kaya",
			  "department": "Bilgisayar Mühendisliği",
			  "year": "YEAR_3",
			  "email": "someone-else@metu.edu.tr"
			}
			""";

	// The address comes from the caller's own token; an "email" in the body is ignored.
	@Test
	void registersAStudentWithTheTokensOwnIdentity() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000913");
		FirebaseIdentity identity = new FirebaseIdentity("uid-1", "deniz@metu.edu.tr", true);
		when(studentSignupService.register(eq(identity),
				any(RegisterStudentRequest.class))).thenReturn(new UserResponse(userId, "deniz@metu.edu.tr",
						"Deniz Kaya", UserRole.MENTEE, UserAccountStatus.APPROVED, null, null, null, null, true));

		mockMvc.perform(post("/api/auth/register/student")
				.with(authentication(new UsernamePasswordAuthenticationToken(identity, null,
						List.of(new SimpleGrantedAuthority("ROLE_REGISTERING")))))
				.contentType(MediaType.APPLICATION_JSON)
				.content(STUDENT_FORM))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(userId.toString()))
				.andExpect(jsonPath("$.role").value("MENTEE"))
				.andExpect(jsonPath("$.status").value("APPROVED"));
		verify(studentSignupService).register(eq(identity),
				argThat(request -> "Deniz".equals(request.firstName())
						&& request.year() == StudentYear.YEAR_3));
	}

	@Test
	void studentSignupNeedsAFirebaseSignInNotTheServiceCredentials() throws Exception {
		mockMvc.perform(post("/api/auth/register/student")
				.contentType(MediaType.APPLICATION_JSON)
				.content(STUDENT_FORM))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/register/student")
				.with(user("proxy").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(STUDENT_FORM))
				.andExpect(status().isForbidden());
		verifyNoInteractions(studentSignupService);
	}

	@Test
	void studentSignupRefusesAnUnknownYearOrAMissingDepartment() throws Exception {
		for (String body : List.of(STUDENT_FORM.replace("YEAR_3", "YEAR_9"),
				STUDENT_FORM.replace("Bilgisayar Mühendisliği", " "))) {
			mockMvc.perform(post("/api/auth/register/student")
					.with(authentication(registering("deniz@metu.edu.tr")))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
					.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(studentSignupService);
	}
}
