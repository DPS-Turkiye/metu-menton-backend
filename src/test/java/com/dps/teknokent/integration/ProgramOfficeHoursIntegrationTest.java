package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IdentityKind;
import com.dps.teknokent.domain.InstitutionDataSource;
import com.dps.teknokent.domain.OfficeHoursMessageType;
import com.dps.teknokent.domain.OfficeHoursRequestStatus;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.OfficeHoursRequest;
import com.dps.teknokent.entity.OfficeHoursRequestMessage;
import com.dps.teknokent.entity.OfficeHoursUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinator;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.OfficeHoursRequestMessageRepository;
import com.dps.teknokent.repository.OfficeHoursRequestRepository;
import com.dps.teknokent.repository.OfficeHoursUserRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.MeetingSlotTimes;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Randevular reads every office-hours request at the university, so what leaves is pinned field by field: the title,
 * the time, the two people and the status history. The student's description and the messages never do.
 */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class ProgramOfficeHoursIntegrationTest {

	private static final String SECRET_DESCRIPTION = "Gizli açıklama: yalnızca akademisyen görür";
	private static final String SECRET_MESSAGE = "Gizli mesaj: notlarımı ekledim";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private AppUserRepository appUsers;
	@Autowired
	private InstitutionPersonRepository institutionPeople;
	@Autowired
	private OfficeHoursUserRepository officeHoursUsers;
	@Autowired
	private OfficeHoursRequestRepository requests;
	@Autowired
	private OfficeHoursRequestMessageRepository messages;
	@Autowired
	private ProgramRepository programs;
	@Autowired
	private ProgramCoordinatorRepository programCoordinators;

	private AppUser admin;
	private AppUser coordinator;
	private AppUser otherCoordinator;
	private AppUser student;
	private Program officeHours;
	private OfficeHoursRequest request;

	@BeforeEach
	void setUp() {
		admin = account(UserRole.ADMIN, "Türkan Kaya");
		coordinator = account(UserRole.COORDINATOR, "Elif Demir");
		otherCoordinator = account(UserRole.COORDINATOR, "Selin Er");
		student = account(UserRole.MENTEE, "Ayşe Yılmaz");
		AppUser academician = account(UserRole.ACADEMICIAN, "Ahmet Kaya");
		record(student, IdentityKind.STUDENT, "Endüstri Mühendisliği", null);
		record(academician, IdentityKind.ACADEMIC, "Bilgisayar Mühendisliği", "Prof. Dr.");

		officeHours = program(ProgramType.OFFICE_HOURS);
		coordinatorOf(officeHours, coordinator);
		coordinatorOf(program(ProgramType.OFFICE_HOURS), otherCoordinator);

		request = new OfficeHoursRequest();
		request.setId(UUID.randomUUID());
		request.setStudentId(officeHoursUser(student).getId());
		request.setInstructorId(officeHoursUser(academician).getId());
		request.setStatus(OfficeHoursRequestStatus.PENDING);
		request.setTopic("CV ve yüksek lisans seçenekleri");
		request.setExpectedOutcome(SECRET_DESCRIPTION);
		request.setPreferredSlots(MeetingSlotTimes.formatMachineSlot(
				LocalDate.now(MeetingSlotTimes.APP_ZONE).plusDays(3).atTime(10, 30)
						.atZone(MeetingSlotTimes.APP_ZONE).toInstant()));
		request.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
		request.setUpdatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
		request = requests.saveAndFlush(request);

		OfficeHoursRequestMessage message = new OfficeHoursRequestMessage();
		message.setId(UUID.randomUUID());
		message.setRequestId(request.getId());
		message.setAuthorId(request.getInstructorId());
		message.setType(OfficeHoursMessageType.INSTRUCTOR_QUESTION);
		message.setBody(SECRET_MESSAGE);
		message.setCreatedAt(Instant.now().minus(2, ChronoUnit.HOURS));
		messages.saveAndFlush(message);
	}

	@Test
	void theListCarriesOnlyWhatTheTableShows() throws Exception {
		String body = call(list(), coordinator).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		JsonNode page = jsonMapper.readTree(body);

		assertThat(fields(page)).containsExactlyInAnyOrder("counts", "faculties", "appointments");
		assertThat(fields(page.get("counts"))).containsExactlyInAnyOrder("awaitingDecision", "alternativeProposed",
				"acceptedThisWeek", "declinedThisWeek");
		JsonNode row = page.get("appointments").get("content").get(0);
		assertThat(fields(row)).containsExactlyInAnyOrder("id", "status", "topic", "startsAt", "student", "academic");
		assertThat(fields(row.get("student"))).containsExactlyInAnyOrder("name", "title", "department");
		assertThat(fields(row.get("academic"))).containsExactlyInAnyOrder("name", "title", "department");
		assertThat(row.get("topic").asString()).isEqualTo("CV ve yüksek lisans seçenekleri");
		assertThat(row.get("academic").get("title").asString()).isEqualTo("Prof. Dr.");
		assertThat(row.get("student").get("department").asString()).isEqualTo("Endüstri Mühendisliği");
		assertThat(body).doesNotContain(SECRET_DESCRIPTION).doesNotContain(SECRET_MESSAGE)
				.doesNotContain(student.getEmail());
	}

	@Test
	void theDetailCarriesOnlyWhatTheDrawerShows() throws Exception {
		String body = call(detail(), coordinator).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		JsonNode appointment = jsonMapper.readTree(body);

		assertThat(fields(appointment)).containsExactlyInAnyOrder("id", "status", "topic", "startsAt", "endsAt",
				"student", "academic", "history");
		assertThat(fields(appointment.get("history").get(0))).containsExactlyInAnyOrder("step", "at");
		List<String> steps = new ArrayList<>();
		appointment.get("history").forEach(entry -> steps.add(entry.get("step").asString()));
		assertThat(steps).containsExactly("SUBMITTED", "INFO_REQUESTED");
		assertThat(body).doesNotContain(SECRET_DESCRIPTION).doesNotContain(SECRET_MESSAGE)
				.doesNotContain(student.getEmail());
	}

	@Test
	void aCoordinatorOfAnotherProgramIsRefused() throws Exception {
		call(list(), otherCoordinator).andExpect(status().isBadRequest());
		call(detail(), otherCoordinator).andExpect(status().isBadRequest());
	}

	@Test
	void aCoordinatorWhoseAccountIsNotApprovedIsRefused() throws Exception {
		coordinator.setStatus(UserAccountStatus.PENDING);
		appUsers.saveAndFlush(coordinator);

		// The shared program guard refuses an account that is not approved, before the program is looked at.
		call(list(), coordinator).andExpect(status().isForbidden());
		call(detail(), coordinator).andExpect(status().isForbidden());
	}

	@Test
	void aDateRangeFindsTheRequestByItsAskedForTime() throws Exception {
		LocalDate day = LocalDate.now(MeetingSlotTimes.APP_ZONE).plusDays(3);

		String within = call(list().param("from", day.toString()).param("to", day.toString()), coordinator)
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String before = call(list().param("from", day.minusDays(2).toString()).param("to",
				day.minusDays(1).toString()), coordinator)
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(jsonMapper.readTree(within).get("appointments").get("totalElements").asInt()).isEqualTo(1);
		assertThat(jsonMapper.readTree(before).get("appointments").get("totalElements").asInt()).isZero();
	}

	@Test
	void aStudentIsRefused() throws Exception {
		call(list(), student).andExpect(status().isBadRequest());
		call(detail(), student).andExpect(status().isBadRequest());
	}

	@Test
	void theAdminMayLook() throws Exception {
		call(list(), admin).andExpect(status().isOk());
		call(detail(), admin).andExpect(status().isOk());
	}

	@Test
	void aProgramOfAnotherTypeHasNoAppointments() throws Exception {
		Program peer = program(ProgramType.PEER_MENTORING);
		coordinatorOf(peer, coordinator);

		call(get("/api/coordinator/programs/{id}/office-hours/appointments", peer.getId()), coordinator)
				.andExpect(status().isBadRequest());
	}

	private MockHttpServletRequestBuilder list() {
		return get("/api/coordinator/programs/{id}/office-hours/appointments", officeHours.getId());
	}

	private MockHttpServletRequestBuilder detail() {
		return get("/api/coordinator/programs/{id}/office-hours/appointments/{requestId}", officeHours.getId(),
				request.getId());
	}

	private ResultActions call(MockHttpServletRequestBuilder builder, AppUser actor) throws Exception {
		return mockMvc.perform(builder.param("actorId", actor.getId().toString())
				.with(user("frontend").roles("SERVICE")));
	}

	private static List<String> fields(JsonNode node) {
		return new ArrayList<>(node.propertyNames());
	}

	private AppUser account(UserRole role, String name) {
		AppUser account = new AppUser();
		account.setFullName(name);
		account.setEmail(UUID.randomUUID() + "@program-office-hours.test");
		account.setRole(role);
		return appUsers.saveAndFlush(account);
	}

	private void record(AppUser account, IdentityKind kind, String department, String title) {
		InstitutionPerson person = new InstitutionPerson("odtu", UUID.randomUUID().toString());
		person.setUser(account);
		person.setKinds(Set.of(kind));
		person.setFullName(account.getFullName());
		person.setDepartmentName(department);
		person.setAcademicTitle(title);
		person.setFaculty("Mühendislik Fakültesi");
		person.setSource(InstitutionDataSource.DEV_STUB);
		person.setSyncedAt(Instant.now());
		institutionPeople.saveAndFlush(person);
	}

	private OfficeHoursUser officeHoursUser(AppUser account) {
		OfficeHoursUser person = new OfficeHoursUser();
		person.setId(UUID.randomUUID());
		person.setAppUserId(account.getId());
		person.setFullName(account.getFullName());
		person.setCreatedAt(Instant.now());
		person.setLastLaunchAt(Instant.now());
		return officeHoursUsers.saveAndFlush(person);
	}

	private Program program(ProgramType type) {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName(type + " " + UUID.randomUUID());
		created.setProgramType(type);
		created.setCreatedBy(admin.getId());
		created.setCreatedAt(Instant.now());
		created.setStatus(ProgramStatus.ACTIVE);
		return programs.saveAndFlush(created);
	}

	private void coordinatorOf(Program coordinated, AppUser user) {
		ProgramCoordinator link = new ProgramCoordinator();
		link.setId(UUID.randomUUID());
		link.setProgramId(coordinated.getId());
		link.setUserId(user.getId());
		link.setAddedBy(admin.getId());
		link.setCreatedAt(Instant.now());
		programCoordinators.saveAndFlush(link);
	}
}
