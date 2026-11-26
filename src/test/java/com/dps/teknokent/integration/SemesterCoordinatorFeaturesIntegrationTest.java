package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinator;
import com.dps.teknokent.entity.ProgramCoordinatorFeature;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.ProgramCoordinatorFeatureRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Coordinators per feature (Hocam): a semester's coordinator opens only the screens of the features assigned to
 * them, only the admin assigns them, and a feature switched off or a coordinator removed leaves no assignment.
 */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class SemesterCoordinatorFeaturesIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private AppUserRepository appUsers;
	@Autowired
	private ProgramRepository programs;
	@Autowired
	private ProgramCoordinatorRepository programCoordinators;
	@Autowired
	private ProgramCoordinatorFeatureRepository assignments;

	private AppUser admin;
	private AppUser peerCoordinator;
	private AppUser officeHoursCoordinator;
	private Program semester;

	@BeforeEach
	void setUp() {
		admin = account(UserRole.ADMIN, "Türkan Kaya");
		peerCoordinator = account(UserRole.COORDINATOR, "Elif Demir");
		officeHoursCoordinator = account(UserRole.COORDINATOR, "Selin Er");
		semester = semester(EnumSet.allOf(ProgramFeature.class));
		coordinatorOf(semester, peerCoordinator, ProgramFeature.PEER_MENTORING, ProgramFeature.ISKUR);
		coordinatorOf(semester, officeHoursCoordinator, ProgramFeature.OFFICE_HOURS);
	}

	@Test
	void anOfficeHoursCoordinatorOpensOfficeHoursButNotPeerMentoringOrIskur() throws Exception {
		call(get("/api/coordinator/programs/{id}/office-hours/appointments", semester.getId()),
				officeHoursCoordinator).andExpect(status().isOk());
		call(get("/api/coordinator/programs/{id}/academics", semester.getId()), officeHoursCoordinator)
				.andExpect(status().isOk());

		call(get("/api/coordinator/programs/{id}/peer-categories", semester.getId()), officeHoursCoordinator)
				.andExpect(status().isForbidden());
		call(get("/api/coordinator/programs/{id}/badges/roster", semester.getId()), officeHoursCoordinator)
				.andExpect(status().isForbidden());
		call(get("/api/coordinator/programs/{id}/iskur/workspace", semester.getId()), officeHoursCoordinator)
				.andExpect(status().isForbidden());
	}

	@Test
	void aPeerCoordinatorOpensPeerMentoringAndIskurButNotOfficeHours() throws Exception {
		call(get("/api/coordinator/programs/{id}/peer-categories", semester.getId()), peerCoordinator)
				.andExpect(status().isOk());
		call(get("/api/coordinator/programs/{id}/iskur/workspace", semester.getId()), peerCoordinator)
				.andExpect(status().isOk());

		call(get("/api/coordinator/programs/{id}/office-hours/appointments", semester.getId()), peerCoordinator)
				.andExpect(status().isForbidden());
		call(get("/api/coordinator/programs/{id}/academics", semester.getId()), peerCoordinator)
				.andExpect(status().isForbidden());
	}

	@Test
	void eachCoordinatorReadsTheirOwnFeaturesAndEveryonesInTheList() throws Exception {
		call(get("/api/coordinator/programs/{id}", semester.getId()), officeHoursCoordinator)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.program.features.length()").value(3))
				.andExpect(jsonPath("$.program.myFeatures.length()").value(1))
				.andExpect(jsonPath("$.program.myFeatures[0]").value("OFFICE_HOURS"));
		call(get("/api/coordinator/programs/{id}", semester.getId()), admin)
				.andExpect(jsonPath("$.program.myFeatures.length()").value(3));

		call(get("/api/coordinator/programs/{id}/coordinators", semester.getId()), peerCoordinator)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == '" + peerCoordinator.getId() + "')].features[*]")
						.value(containsInAnyOrder("PEER_MENTORING", "ISKUR")))
				.andExpect(jsonPath("$[?(@.id == '" + officeHoursCoordinator.getId() + "')].features[*]")
						.value(contains("OFFICE_HOURS")));
	}

	@Test
	void onlyTheAdminAssignsACoordinatorsFeatures() throws Exception {
		setCoordinatorFeatures(officeHoursCoordinator, peerCoordinator, ProgramFeature.PEER_MENTORING)
				.andExpect(status().isForbidden());
		assertThat(featuresOf(officeHoursCoordinator)).containsExactly(ProgramFeature.OFFICE_HOURS);

		setCoordinatorFeatures(officeHoursCoordinator, admin, ProgramFeature.OFFICE_HOURS,
				ProgramFeature.PEER_MENTORING)
				.andExpect(status().isOk());
		assertThat(featuresOf(officeHoursCoordinator))
				.containsExactlyInAnyOrder(ProgramFeature.OFFICE_HOURS, ProgramFeature.PEER_MENTORING);
		call(get("/api/coordinator/programs/{id}/peer-categories", semester.getId()), officeHoursCoordinator)
				.andExpect(status().isOk());
	}

	@Test
	void anAssignmentIsANonEmptyPartOfWhatTheSemesterRuns() throws Exception {
		Program peerOnly = semester(EnumSet.of(ProgramFeature.PEER_MENTORING));
		AppUser coordinator = account(UserRole.COORDINATOR, "Can Er");
		coordinatorOf(peerOnly, coordinator, ProgramFeature.PEER_MENTORING);

		putCoordinatorFeatures(peerOnly, coordinator, admin, List.of(ProgramFeature.OFFICE_HOURS))
				.andExpect(status().isBadRequest());
		putCoordinatorFeatures(peerOnly, coordinator, admin, List.of())
				.andExpect(status().isBadRequest());
		assertThat(assignments.findByProgramIdAndUserId(peerOnly.getId(), coordinator.getId())).hasSize(1);
	}

	@Test
	void aCoordinatorAddedWithoutFeaturesRunsEverythingAndWithFeaturesOnlyThose() throws Exception {
		AppUser everything = account(UserRole.COORDINATOR, "Deniz Ak");
		AppUser officeHoursOnly = account(UserRole.COORDINATOR, "Ece Su");

		addCoordinator(everything, null).andExpect(status().isOk());
		addCoordinator(officeHoursOnly, List.of(ProgramFeature.OFFICE_HOURS)).andExpect(status().isOk());

		assertThat(featuresOf(everything)).containsExactlyInAnyOrder(ProgramFeature.values());
		assertThat(featuresOf(officeHoursOnly)).containsExactly(ProgramFeature.OFFICE_HOURS);
	}

	@Test
	void aFeatureSwitchedOffIsNobodysUntilItIsSwitchedBackOn() throws Exception {
		switchFeatures(ProgramFeature.PEER_MENTORING, ProgramFeature.ISKUR);

		call(get("/api/coordinator/programs/{id}", semester.getId()), officeHoursCoordinator)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.program.myFeatures.length()").value(0));
		call(get("/api/coordinator/programs/{id}/coordinators", semester.getId()), admin)
				.andExpect(jsonPath("$[?(@.id == '" + officeHoursCoordinator.getId() + "')].features[*]").isEmpty());

		switchFeatures(ProgramFeature.values());

		call(get("/api/coordinator/programs/{id}", semester.getId()), officeHoursCoordinator)
				.andExpect(jsonPath("$.program.myFeatures[0]").value("OFFICE_HOURS"));
	}

	@Test
	void removingACoordinatorRemovesTheirFeatures() throws Exception {
		call(delete("/api/coordinator/programs/{id}/coordinators/{userId}", semester.getId(),
				peerCoordinator.getId()), admin).andExpect(status().isOk());

		assertThat(featuresOf(peerCoordinator)).isEmpty();
	}

	@Test
	void theSharedPeerScreensAreClosedToAnOfficeHoursCoordinator() throws Exception {
		for (String path : List.of("/api/coordinator/programs/{id}/mentors",
				"/api/coordinator/programs/{id}/approvals", "/api/coordinator/programs/{id}/eligible-emails",
				"/api/coordinator/programs/{id}/teams", "/api/coordinator/programs/{id}/matching",
				"/api/coordinator/programs/{id}/report")) {
			call(get(path, semester.getId()), officeHoursCoordinator).andExpect(status().isForbidden());
			call(get(path, semester.getId()), peerCoordinator).andExpect(status().is2xxSuccessful());
		}
	}

	private void switchFeatures(ProgramFeature... features) throws Exception {
		mockMvc.perform(put("/api/coordinator/programs/{id}/features", semester.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(admin, List.of(features)))
				.with(user("frontend").roles("SERVICE")))
				.andExpect(status().isOk());
	}

	private ResultActions setCoordinatorFeatures(AppUser coordinator, AppUser actor, ProgramFeature... features)
			throws Exception {
		return putCoordinatorFeatures(semester, coordinator, actor, List.of(features));
	}

	private ResultActions putCoordinatorFeatures(Program program, AppUser coordinator, AppUser actor,
			List<ProgramFeature> features) throws Exception {
		return mockMvc.perform(put("/api/coordinator/programs/{id}/coordinators/{userId}/features",
				program.getId(), coordinator.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(actor, features))
				.with(user("frontend").roles("SERVICE")));
	}

	private ResultActions addCoordinator(AppUser coordinator, List<ProgramFeature> features) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("actorId", admin.getId());
		body.put("userId", coordinator.getId());
		if (features != null) {
			body.put("features", features);
		}
		return mockMvc.perform(post("/api/coordinator/programs/{id}/coordinators", semester.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(body))
				.with(user("frontend").roles("SERVICE")));
	}

	private String body(AppUser actor, List<ProgramFeature> features) {
		return jsonMapper.writeValueAsString(Map.of("actorId", actor.getId(), "features", features));
	}

	private List<ProgramFeature> featuresOf(AppUser coordinator) {
		return assignments.findByProgramIdAndUserId(semester.getId(), coordinator.getId()).stream()
				.map(ProgramCoordinatorFeature::getFeature)
				.toList();
	}

	private ResultActions call(MockHttpServletRequestBuilder builder, AppUser actor) throws Exception {
		return mockMvc.perform(builder.param("actorId", actor.getId().toString())
				.with(user("frontend").roles("SERVICE")));
	}

	private AppUser account(UserRole role, String name) {
		AppUser account = new AppUser();
		account.setFullName(name);
		account.setEmail(UUID.randomUUID() + "@coordinator-features.test");
		account.setRole(role);
		return appUsers.saveAndFlush(account);
	}

	private Program semester(Set<ProgramFeature> features) {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName("2026 Güz " + UUID.randomUUID());
		created.setProgramType(ProgramType.SEMESTER);
		created.setFeatures(features);
		created.setCreatedBy(admin.getId());
		created.setCreatedAt(Instant.now());
		created.setStatus(ProgramStatus.ACTIVE);
		return programs.saveAndFlush(created);
	}

	private void coordinatorOf(Program coordinated, AppUser user, ProgramFeature... features) {
		ProgramCoordinator link = new ProgramCoordinator();
		link.setId(UUID.randomUUID());
		link.setProgramId(coordinated.getId());
		link.setUserId(user.getId());
		link.setAddedBy(admin.getId());
		link.setCreatedAt(Instant.now());
		programCoordinators.saveAndFlush(link);
		for (ProgramFeature feature : features) {
			assignments.saveAndFlush(new ProgramCoordinatorFeature(coordinated.getId(), user.getId(), feature));
		}
	}
}
