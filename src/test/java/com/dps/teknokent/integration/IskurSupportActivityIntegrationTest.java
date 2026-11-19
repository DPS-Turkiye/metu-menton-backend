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
import java.util.UUID;

import org.hamcrest.Matchers;
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

import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurSupportActivityRepository;
import com.dps.teknokent.repository.ProgramRepository;

/** The coordinator's support-activity pool, through the ODTÜ edition's real security chain and an H2 database. */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class IskurSupportActivityIntegrationTest {

	private static final String BASE = "/api/coordinator/programs/{programId}/iskur/support-activities";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private ProgramRepository programRepository;
	@Autowired
	private IskurSupportActivityRepository activityRepository;

	private AppUser coordinator;
	private AppUser otherCoordinator;
	private AppUser intern;
	private Program program;

	@BeforeEach
	void setUp() {
		coordinator = user("Selin Er", UserRole.COORDINATOR, null);
		otherCoordinator = user("Başka Koordinatör", UserRole.COORDINATOR, null);
		program = program(ProgramType.PEER_MENTORING, coordinator);
		intern = user("Mert Demir", UserRole.MENTEE, program);
	}

	@Test
	void seedsTheRulesFramesFiveActivitiesOnlyIntoAnEmptyPool() throws Exception {
		send(post(BASE + "/default", program.getId()), actor(coordinator))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.programId").value(program.getId().toString()))
				.andExpect(jsonPath("$.activities[*].title").value(Matchers.contains(
						"Kaynak rehberi güncelleme", "Açık ofis saati", "Mentee ihtiyaç anketi",
						"Mentör eğitimi / vaka çalışması", "Yeni öğrencilere program tanıtımı")))
				.andExpect(jsonPath("$.activities[*].active").value(Matchers.everyItem(Matchers.is(true))));

		send(post(BASE + "/default", program.getId()), actor(coordinator)).andExpect(status().isConflict());
		assertThat(activityRepository.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId())).hasSize(5);
	}

	@Test
	void addsRenamesRetiresAndDeletesActivities() throws Exception {
		send(post(BASE, program.getId()), body(coordinator, "Açık ofis saati", "Kütüphanede", null))
				.andExpect(status().isOk());
		send(post(BASE, program.getId()), body(coordinator, "Vaka çalışması", null, false))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activities[*].title").value(Matchers.contains("Açık ofis saati",
						"Vaka çalışması")))
				.andExpect(jsonPath("$.activities[0].description").value("Kütüphanede"))
				.andExpect(jsonPath("$.activities[1].active").value(false))
				.andExpect(jsonPath("$.activities[1].sortOrder").value(1));
		// Titles are unique in the pool under Turkish case rules.
		send(post(BASE, program.getId()), body(coordinator, "AÇIK OFİS SAATİ", null, null))
				.andExpect(status().isConflict());

		UUID id = activityRepository.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()).getFirst()
				.getId();
		send(put(BASE + "/{id}", program.getId(), id), body(coordinator, "Açık ofis saati (Salı)", null, false))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activities[0].title").value("Açık ofis saati (Salı)"))
				.andExpect(jsonPath("$.activities[0].description").doesNotExist())
				.andExpect(jsonPath("$.activities[0].active").value(false));

		mockMvc.perform(delete(BASE + "/{id}", program.getId(), id)
				.param("actorId", coordinator.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activities[*].title").value(Matchers.contains("Vaka çalışması")));
	}

	@Test
	void onlyTheProgramsCoordinatorsKeepThePool() throws Exception {
		list(intern).andExpect(status().isBadRequest());
		list(otherCoordinator).andExpect(status().isBadRequest());
		send(post(BASE, program.getId()), body(intern, "Kendi faaliyetim", null, null))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(BASE, program.getId()).param("actorId", coordinator.getId().toString()))
				.andExpect(status().isUnauthorized());
		list(coordinator).andExpect(status().isOk()).andExpect(jsonPath("$.activities").isEmpty());
		assertThat(activityRepository.findAll()).isEmpty();
	}

	@Test
	void anActivityOfAnotherProgramIsNotFound() throws Exception {
		Program other = program(ProgramType.PEER_MENTORING, coordinator);
		send(post(BASE + "/default", other.getId()), actor(coordinator)).andExpect(status().isOk());
		UUID foreign = activityRepository.findByProgramIdOrderBySortOrderAscCreatedAtAsc(other.getId()).getFirst()
				.getId();
		send(put(BASE + "/{id}", program.getId(), foreign), body(coordinator, "Ele geçir", null, null))
				.andExpect(status().isNotFound());
	}

	@Test
	void onlyPeerProgramsHaveAPoolAndAnArchivedOneIsReadOnly() throws Exception {
		Program office = program(ProgramType.OFFICE_HOURS, coordinator);
		mockMvc.perform(get(BASE, office.getId())
				.param("actorId", coordinator.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isBadRequest());

		program.setStatus(ProgramStatus.COMPLETED);
		programRepository.saveAndFlush(program);
		list(coordinator).andExpect(status().isOk());
		send(post(BASE + "/default", program.getId()), actor(coordinator)).andExpect(status().isBadRequest());
	}

	private ResultActions list(AppUser actor) throws Exception {
		return mockMvc.perform(get(BASE, program.getId())
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
		return mockMvc.perform(request.with(httpBasic("test", "test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private static String actor(AppUser actor) {
		return "{\"actorId\":\"" + actor.getId() + "\"}";
	}

	private static String body(AppUser actor, String title, String description, Boolean active) {
		return "{\"actorId\":\"%s\",\"title\":\"%s\",\"description\":%s,\"active\":%s}".formatted(actor.getId(),
				title, description == null ? "null" : "\"" + description + "\"", active);
	}

	private AppUser user(String name, UserRole role, Program joined) {
		AppUser user = new AppUser();
		user.setFullName(name);
		user.setEmail(UUID.randomUUID() + "@example.test");
		user.setRole(role);
		if (joined != null) {
			user.setProgramId(joined.getId());
		}
		return userRepository.saveAndFlush(user);
	}

	private Program program(ProgramType type, AppUser creator) {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName("İŞKUR Güz 2026 " + UUID.randomUUID());
		created.setCreatedBy(creator.getId());
		created.setCreatedAt(Instant.now());
		created.setStatus(ProgramStatus.ACTIVE);
		created.setProgramType(type);
		return programRepository.saveAndFlush(created);
	}
}
