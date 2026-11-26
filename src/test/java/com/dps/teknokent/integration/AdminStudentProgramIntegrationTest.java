package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import com.dps.teknokent.domain.BadgeCode;
import com.dps.teknokent.domain.BadgeSource;
import com.dps.teknokent.domain.BadgeStatus;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramMentorInvitedBy;
import com.dps.teknokent.domain.ProgramMentorStatus;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinator;
import com.dps.teknokent.entity.ProgramMentor;
import com.dps.teknokent.entity.UserBadge;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramMentorRemovalRepository;
import com.dps.teknokent.repository.ProgramMentorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.repository.UserBadgeRepository;
import com.dps.teknokent.security.AppUserPrincipal;

import tools.jackson.databind.json.JsonMapper;

/**
 * Kullanıcılar › Öğrenciler, "Programa ekle / Programdan çıkar" (Hocam, 26 Nov): admin only on the server, refused
 * with the open work listed, nothing deleted, and an Akran mentor keeps mentoring in the semester they move to.
 */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class AdminStudentProgramIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private AppUserRepository users;
	@Autowired
	private ProgramRepository programs;
	@Autowired
	private ProgramCoordinatorRepository coordinators;
	@Autowired
	private ProgramMentorRepository programMentors;
	@Autowired
	private ProgramMentorRemovalRepository removals;
	@Autowired
	private UserBadgeRepository badges;
	@Autowired
	private IskurPlacementRepository placements;

	private AppUser admin;
	private AppUser coordinator;
	private Program fall;
	private Program spring;
	private AppUser student;

	@BeforeEach
	void setUp() {
		admin = account(UserRole.ADMIN, "Türkan Kaya", null);
		coordinator = account(UserRole.COORDINATOR, "Elif Demir", null);
		fall = semester("2026 Güz", ProgramStatus.ACTIVE);
		spring = semester("2027 Bahar", ProgramStatus.DRAFT);
		ProgramCoordinator link = new ProgramCoordinator();
		link.setId(UUID.randomUUID());
		link.setProgramId(fall.getId());
		link.setUserId(coordinator.getId());
		link.setAddedBy(admin.getId());
		link.setCreatedAt(Instant.now());
		coordinators.saveAndFlush(link);
		student = account(UserRole.MENTEE, "Ayşe Yılmaz", fall);
	}

	@Test
	void onlyTheAdminSeesOrChangesAStudentsProgram() throws Exception {
		call(get("/api/admin/users/{id}/program", student.getId()), coordinator).andExpect(status().isForbidden());
		send(put("/api/admin/users/{id}/program", student.getId()), coordinator, spring.getId())
				.andExpect(status().isForbidden());
		remove(coordinator).andExpect(status().isForbidden());

		assertThat(users.findById(student.getId()).orElseThrow().getProgramId()).isEqualTo(fall.getId());
	}

	@Test
	void aChangeTakesTheAdminsOwnSignInNotTheSharedServiceCredentials() throws Exception {
		mockMvc.perform(put("/api/admin/users/{id}/program", student.getId())
				.param("actorId", admin.getId().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(Map.of("programId", spring.getId())))
				.with(user("frontend").roles("SERVICE")))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/users/{id}/program", student.getId())
				.param("actorId", admin.getId().toString())
				.with(user("frontend").roles("SERVICE")))
				.andExpect(status().isForbidden());

		assertThat(users.findById(student.getId()).orElseThrow().getProgramId()).isEqualTo(fall.getId());
	}

	@Test
	void aStudentInNoSemesterShowsNoAwardAndAMoveIntoASemesterWithoutPeerMentoringCarriesNoMentoring()
			throws Exception {
		gate(student, fall);
		acceptedRow(student, fall);
		Program officeHoursOnly = semester("2027 Yaz", ProgramStatus.DRAFT);
		officeHoursOnly.setFeatures(EnumSet.of(ProgramFeature.OFFICE_HOURS));
		programs.saveAndFlush(officeHoursOnly);

		send(put("/api/admin/users/{id}/program", student.getId()), admin, officeHoursOnly.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.movedMentorRow").value(true));

		assertThat(programMentors.findByProgramIdAndMentorId(officeHoursOnly.getId(), student.getId())).isEmpty();
		assertThat(users.findById(student.getId()).orElseThrow().getProgramId()).isEqualTo(officeHoursOnly.getId());
	}

	@Test
	void theDialogShowsTheCurrentSemesterTheTargetsAndNothingOpen() throws Exception {
		semester("2025 Bahar", ProgramStatus.COMPLETED);

		call(get("/api/admin/users/{id}/program", student.getId()), admin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.current.id").value(fall.getId().toString()))
				.andExpect(jsonPath("$.targets.length()").value(1))
				.andExpect(jsonPath("$.targets[0].id").value(spring.getId().toString()))
				.andExpect(jsonPath("$.blockers.length()").value(0));
	}

	@Test
	void movingAnAkranMentorKeepsThemMentoringAndDeletesNothing() throws Exception {
		UserBadge gate = gate(student, fall);
		ProgramMentor row = acceptedRow(student, fall);

		send(put("/api/admin/users/{id}/program", student.getId()), admin, spring.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fromProgramId").value(fall.getId().toString()))
				.andExpect(jsonPath("$.toProgramId").value(spring.getId().toString()))
				.andExpect(jsonPath("$.movedMentorRow").value(true));

		assertThat(users.findById(student.getId()).orElseThrow().getProgramId()).isEqualTo(spring.getId());
		assertThat(programMentors.findByProgramIdAndMentorId(spring.getId(), student.getId()).orElseThrow()
				.getStatus()).isEqualTo(ProgramMentorStatus.ACCEPTED);
		assertThat(badges.findFirstByUserIdAndBadgeCodeAndProgramIdAndStatus(student.getId(), BadgeCode.PEER_MENTOR,
				spring.getId(), BadgeStatus.ACTIVE)).isPresent();
		assertThat(programMentors.findById(row.getId()).orElseThrow().getStatus())
				.as("the old row stays, as history").isEqualTo(ProgramMentorStatus.REJECTED);
		assertThat(badges.findById(gate.getId()).orElseThrow().getStatus()).isEqualTo(BadgeStatus.REVOKED);
		assertThat(removals.findAll()).anyMatch(removal -> removal.getMentorId().equals(student.getId())
				&& removal.getProgramId().equals(fall.getId()));
	}

	@Test
	void removingAStudentLeavesThemInNoSemester() throws Exception {
		remove(admin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.toProgramId").isEmpty());

		assertThat(users.findById(student.getId()).orElseThrow().getProgramId()).isNull();
		remove(admin)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_IN_PROGRAM"));
	}

	@Test
	void openWorkRefusesBothWithTheWorkListedAndChangesNothing() throws Exception {
		IskurPlacement placement = new IskurPlacement(fall.getId(), student.getId(), admin.getId(), Instant.now());
		placement.setTeamId(UUID.randomUUID());
		placement.setTeamRole(IskurTeamRole.LEADER);
		placements.saveAndFlush(placement);

		String body = send(put("/api/admin/users/{id}/program", student.getId()), admin, spring.getId())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROGRAM_WORK_OPEN"))
				.andExpect(jsonPath("$.blockers[0].kind").value("ISKUR_PLACEMENT"))
				.andExpect(jsonPath("$.blockers[0].count").value(1))
				.andReturn().getResponse().getContentAsString();
		remove(admin).andExpect(status().isConflict());
		call(get("/api/admin/users/{id}/program", student.getId()), admin)
				.andExpect(jsonPath("$.blockers[0].kind").value("ISKUR_PLACEMENT"));

		assertThat(body).doesNotContain(student.getFullName()).doesNotContain(student.getEmail());
		assertThat(users.findById(student.getId()).orElseThrow().getProgramId()).isEqualTo(fall.getId());
	}

	@Test
	void aStudentGoesOnlyIntoAnotherDraftOrRunningSemester() throws Exception {
		Program completed = semester("2025 Bahar", ProgramStatus.COMPLETED);

		send(put("/api/admin/users/{id}/program", student.getId()), admin, fall.getId())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_IN_PROGRAM"));
		send(put("/api/admin/users/{id}/program", student.getId()), admin, completed.getId())
				.andExpect(status().isBadRequest());
		send(put("/api/admin/users/{id}/program", coordinator.getId()), admin, spring.getId())
				.andExpect(status().isBadRequest());
	}

	@Test
	void theStudentListHasOneRowPerStudentAndThoseInNoSemester() throws Exception {
		AppUser outside = account(UserRole.MENTEE, "Can Er", null);

		call(get("/api/admin/students"), admin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.userId == '" + student.getId() + "')].programId")
						.value(org.hamcrest.Matchers.contains(fall.getId().toString())))
				.andExpect(jsonPath("$[?(@.userId == '" + outside.getId() + "')].programId")
						.value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
	}

	private ResultActions send(MockHttpServletRequestBuilder builder, AppUser actor, UUID programId)
			throws Exception {
		return mockMvc.perform(builder.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(Map.of("programId", programId)))
				.with(authentication(signedIn(actor))));
	}

	private ResultActions remove(AppUser actor) throws Exception {
		return mockMvc.perform(delete("/api/admin/users/{id}/program", student.getId())
				.with(authentication(signedIn(actor))));
	}

	private static UsernamePasswordAuthenticationToken signedIn(AppUser user) {
		return new UsernamePasswordAuthenticationToken(new AppUserPrincipal(
				user.getId(), user.getEmail(), user.getRole(), user.getStatus(), "signed-in-uid"), null, List.of());
	}

	private ResultActions call(MockHttpServletRequestBuilder builder, AppUser actor) throws Exception {
		return mockMvc.perform(builder.param("actorId", actor.getId().toString())
				.with(user("frontend").roles("SERVICE")));
	}

	private AppUser account(UserRole role, String name, Program program) {
		AppUser account = new AppUser();
		account.setFullName(name);
		account.setEmail(UUID.randomUUID() + "@admin-student-program.test");
		account.setRole(role);
		account.setStatus(UserAccountStatus.APPROVED);
		account.setProgramId(program == null ? null : program.getId());
		return users.saveAndFlush(account);
	}

	private Program semester(String name, ProgramStatus status) {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName(name);
		created.setProgramType(ProgramType.SEMESTER);
		created.setFeatures(EnumSet.allOf(ProgramFeature.class));
		created.setCreatedBy(admin.getId());
		created.setCreatedAt(Instant.now());
		created.setStatus(status);
		return programs.saveAndFlush(created);
	}

	private UserBadge gate(AppUser holder, Program program) {
		UserBadge award = new UserBadge();
		award.setId(UUID.randomUUID());
		award.setUserId(holder.getId());
		award.setBadgeCode(BadgeCode.PEER_MENTOR);
		award.setProgramId(program.getId());
		award.setStatus(BadgeStatus.ACTIVE);
		award.setSource(BadgeSource.COORDINATOR);
		award.setAwardedAt(Instant.now());
		award.setAwardedBy(coordinator.getId());
		return badges.saveAndFlush(award);
	}

	private ProgramMentor acceptedRow(AppUser mentor, Program program) {
		ProgramMentor row = new ProgramMentor();
		row.setId(UUID.randomUUID());
		row.setProgramId(program.getId());
		row.setMentorId(mentor.getId());
		row.setStatus(ProgramMentorStatus.ACCEPTED);
		row.setInvitedBy(ProgramMentorInvitedBy.COORDINATOR);
		row.setCreatedAt(Instant.now());
		return programMentors.saveAndFlush(row);
	}
}
