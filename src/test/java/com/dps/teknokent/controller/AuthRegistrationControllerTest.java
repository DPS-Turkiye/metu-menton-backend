package com.dps.teknokent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.dps.teknokent.dto.requests.RegisterFounderRequest;
import com.dps.teknokent.dto.requests.RegisterMemberRequest;
import com.dps.teknokent.dto.requests.RegisterMentorRequest;
import com.dps.teknokent.dto.requests.RegisterStudentRequest;
import com.dps.teknokent.dto.responses.ProgramEligibilityResponse;
import com.dps.teknokent.dto.responses.UserResponse;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.security.FirebaseIdentity;
import com.dps.teknokent.service.ProgramEligibilityService;
import com.dps.teknokent.service.RegistrationService;
import com.dps.teknokent.service.StudentSignupService;

@WebMvcTest(AuthRegistrationController.class)
@Import(SecurityConfig.class)
class AuthRegistrationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegistrationService registrationService;

	@MockitoBean
	private ProgramEligibilityService programEligibilityService;

	@MockitoBean
	private StudentSignupService studentSignupService;

	private static final UUID ISKUR_ID = UUID.fromString("00000000-0000-0000-0000-0000000009a1");

	private static UsernamePasswordAuthenticationToken registering(String email) {
		return new UsernamePasswordAuthenticationToken(
				new FirebaseIdentity("uid-1", email, true), null,
				List.of(new SimpleGrantedAuthority("ROLE_REGISTERING")));
	}

	@Test
	void answersProgramEligibilityForTheSignedInAddressOnly() throws Exception {
		when(programEligibilityService.check(ISKUR_ID, "e1@metu.edu.tr"))
				.thenReturn(ProgramEligibilityResponse.refused("not on the list"));

		mockMvc.perform(get("/api/auth/register/program-eligibility")
				.param("programId", ISKUR_ID.toString())
				.param("email", "someone-else@metu.edu.tr")
				.with(authentication(registering("e1@metu.edu.tr"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eligible").value(false))
				.andExpect(jsonPath("$.message").value("not on the list"));
		verify(programEligibilityService).check(ISKUR_ID, "e1@metu.edu.tr");
	}

	// Firebase puts the address in the token before it is verified; the list must not answer for it.
	@Test
	void refusesProgramEligibilityForAnUnverifiedAddressWithoutAskingTheList() throws Exception {
		mockMvc.perform(get("/api/auth/register/program-eligibility")
				.param("programId", ISKUR_ID.toString())
				.with(authentication(new UsernamePasswordAuthenticationToken(
						new FirebaseIdentity("uid-1", "e1@metu.edu.tr", false), null,
						List.of(new SimpleGrantedAuthority("ROLE_REGISTERING"))))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eligible").value(false))
				.andExpect(jsonPath("$.message").isNotEmpty());
		verifyNoInteractions(programEligibilityService);
	}

	@Test
	void programEligibilityNeedsAFirebaseSignIn() throws Exception {
		mockMvc.perform(get("/api/auth/register/program-eligibility").param("programId", ISKUR_ID.toString())
				.with(user("proxy").roles("ADMIN")))
				.andExpect(status().isForbidden());
		verifyNoInteractions(programEligibilityService);
	}

	// On the service credentials there is no Firebase login to bind, so the row could be claimed by its address later.
	@Test
	void refusesRegistrationOnTheServiceCredentials() throws Exception {
		mockMvc.perform(post("/api/auth/register/founder")
				.with(user("proxy").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "fullName": "Ayşe Demir",
						  "email": "ayse@startup.com",
						  "organizationName": "GreenLoop",
						  "programId": "00000000-0000-0000-0000-0000000000aa",
						  "jobTitle": "Kurucu"
						}
						"""))
				.andExpect(status().isForbidden());
		verifyNoInteractions(registrationService);
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

	@Test
	void requiresAuthenticationForFounderRegister() throws Exception {
		mockMvc.perform(post("/api/auth/register/founder")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "fullName": "Ayşe Demir",
						  "email": "ayse@startup.com",
						  "organizationName": "GreenLoop"
						}
						"""))
				.andExpect(status().isUnauthorized());
	}

	// A sign-up whose address already has an account: the reason and its code reach the client, not a bare 400.
	@Test
	void founderRegisterForAnExistingAddressAnswersWithItsCode() throws Exception {
		when(registrationService.registerFounder(any(RegisterFounderRequest.class)))
				.thenThrow(new InvalidRequestException("Bu e-posta adresiyle kayıtlı bir hesap zaten var.",
						RegistrationService.EMAIL_ALREADY_REGISTERED));

		mockMvc.perform(post("/api/auth/register/founder")
				.with(authentication(registering("ayse@startup.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "fullName": "Ayşe Demir",
						  "email": "ayse@startup.com",
						  "organizationName": "GreenLoop",
						  "programId": "00000000-0000-0000-0000-0000000000aa"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.message").value("Bu e-posta adresiyle kayıtlı bir hesap zaten var."));
	}

	// A field the form let through still comes back by name, so the sign-up screen can point at it.
	@Test
	void founderRegisterNamesTheInvalidField() throws Exception {
		mockMvc.perform(post("/api/auth/register/founder")
				.with(authentication(registering("ayse@startup.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "fullName": "Ayşe Demir",
						  "email": "ayse@startup.com",
						  "organizationName": "GreenLoop",
						  "programId": "00000000-0000-0000-0000-0000000000aa",
						  "phone": "0532 123 45 67"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.phone").exists());
		verifyNoInteractions(registrationService);
	}

	@Test
	void registersFounder() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000811");
		UUID organizationId = UUID.fromString("10000000-0000-0000-0000-000000000011");

		when(registrationService.registerFounder(any(RegisterFounderRequest.class))).thenReturn(new UserResponse(
				userId,
				"ayse@startup.com",
				"Ayşe Demir",
				UserRole.MENTEE,
				UserAccountStatus.PENDING,
				organizationId,
				"GreenLoop",
				"Kurucu",
				null,
				false));

		mockMvc.perform(post("/api/auth/register/founder")
				.with(authentication(registering("ayse@startup.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "fullName": "Ayşe Demir",
						  "email": "ayse@startup.com",
						  "organizationName": "GreenLoop",
						  "programId": "00000000-0000-0000-0000-0000000000aa",
						  "jobTitle": "Kurucu"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(userId.toString()))
				.andExpect(jsonPath("$.email").value("ayse@startup.com"))
				.andExpect(jsonPath("$.role").value("MENTEE"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.organizationId").value(organizationId.toString()));
	}

	@Test
	void registersMentor() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000812");

		when(registrationService.registerMentor(any(RegisterMentorRequest.class))).thenReturn(new UserResponse(
				userId,
				"kemal@mentor.com",
				"Dr. Kemal Arslan",
				UserRole.MENTOR,
				UserAccountStatus.PENDING,
				null,
				null,
				null,
				null,
				false));

		mockMvc.perform(post("/api/auth/register/mentor")
				.with(authentication(registering("ayse@startup.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "fullName": "Dr. Kemal Arslan",
						  "email": "kemal@mentor.com"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(userId.toString()))
				.andExpect(jsonPath("$.role").value("MENTOR"))
				.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void registersMember() throws Exception {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000813");
		UUID organizationId = UUID.fromString("10000000-0000-0000-0000-000000000011");

		when(registrationService.registerMember(any(RegisterMemberRequest.class))).thenReturn(new UserResponse(
				userId,
				"elif@startup.com",
				"Elif Demir",
				UserRole.MENTEE,
				UserAccountStatus.APPROVED,
				organizationId,
				"GreenLoop",
				null,
				null,
				false));

		mockMvc.perform(post("/api/auth/register/member")
				.with(authentication(registering("ayse@startup.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "fullName": "Elif Demir",
						  "email": "elif@startup.com",
						  "inviteCode": "ABCD1234"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(userId.toString()))
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.organizationId").value(organizationId.toString()));
	}

	@Test
	void rejectsFounderRegisterWhenEmailIsBlank() throws Exception {
		mockMvc.perform(post("/api/auth/register/founder")
				.with(authentication(registering("ayse@startup.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "fullName": "Ayşe Demir",
						  "email": "",
						  "organizationName": "GreenLoop"
						}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsFounderRegisterWhenPhoneContainsLetters() throws Exception {
		mockMvc.perform(post("/api/auth/register/founder")
				.with(authentication(registering("ayse@startup.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "fullName": "Ayşe Demir",
						  "email": "ayse@startup.com",
						  "organizationName": "GreenLoop",
						  "programId": "00000000-0000-0000-0000-0000000000aa",
						  "phone": "+90abc5551112233"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Request validation failed"))
				.andExpect(jsonPath("$.fieldErrors.phone").exists());
	}

	// A founder without a startup is refused with a code the sign-up screen can act on.
	@Test
	void founderRegisterWithoutAStartupAnswersWithItsCode() throws Exception {
		when(registrationService.registerFounder(any(RegisterFounderRequest.class)))
				.thenThrow(new InvalidRequestException("Startup adı gerekli.", RegistrationService.STARTUP_REQUIRED));

		mockMvc.perform(post("/api/auth/register/founder")
				.with(authentication(registering("ayse@startup.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "fullName": "Ayşe Demir",
						  "email": "ayse@startup.com",
						  "programId": "00000000-0000-0000-0000-0000000000aa"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("STARTUP_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Startup adı gerekli."));
	}
}
