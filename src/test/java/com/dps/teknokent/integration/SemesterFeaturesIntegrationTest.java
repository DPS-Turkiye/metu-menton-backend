package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
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

import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.OfficeHoursRequestStatus;
import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.OfficeHoursMeeting;
import com.dps.teknokent.entity.OfficeHoursRequest;
import com.dps.teknokent.entity.OfficeHoursUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinator;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.OfficeHoursMeetingRepository;
import com.dps.teknokent.repository.OfficeHoursRequestRepository;
import com.dps.teknokent.repository.OfficeHoursUserRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * A semester's feature switches through the API: who may change them and when, and that switching one off with open
 * work is refused with the work listed (counts only) and nothing saved.
 */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class SemesterFeaturesIntegrationTest {

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
	private IskurPlacementRepository placements;
	@Autowired
	private OfficeHoursUserRepository officeHoursUsers;
	@Autowired
	private OfficeHoursRequestRepository officeHoursRequests;
	@Autowired
	private OfficeHoursMeetingRepository officeHoursMeetings;

	private AppUser admin;
	private AppUser coordinator;
	private AppUser otherCoordinator;
	private Program semester;

	@BeforeEach
	void setUp() {
		admin = account(UserRole.ADMIN, "Türkan Kaya");
		coordinator = account(UserRole.COORDINATOR, "Elif Demir");
		otherCoordinator = account(UserRole.COORDINATOR, "Selin Er");
		semester = semester(ProgramStatus.ACTIVE, EnumSet.of(ProgramFeature.PEER_MENTORING, ProgramFeature.ISKUR));
		coordinatorOf(semester, coordinator);
	}

	@Test
	void theAdminSwitchesAFeatureOnInARunningSemester() throws Exception {
		setFeatures(semester, admin, ProgramFeature.PEER_MENTORING, ProgramFeature.ISKUR,
				ProgramFeature.OFFICE_HOURS)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.program.features.length()").value(3))
				.andExpect(jsonPath("$.program.myFeatures.length()").value(3));

		assertThat(programs.findById(semester.getId()).orElseThrow().features())
				.containsExactlyInAnyOrder(ProgramFeature.PEER_MENTORING, ProgramFeature.ISKUR,
						ProgramFeature.OFFICE_HOURS);
	}

	@Test
	void aCoordinatorChangesTheFeaturesOfADraftButNotOfARunningSemester() throws Exception {
		setFeatures(semester, coordinator, ProgramFeature.OFFICE_HOURS).andExpect(status().isForbidden());

		Program draft = semester(ProgramStatus.DRAFT, EnumSet.of(ProgramFeature.PEER_MENTORING));
		coordinatorOf(draft, coordinator);
		setFeatures(draft, coordinator, ProgramFeature.OFFICE_HOURS).andExpect(status().isOk());

		assertThat(programs.findById(semester.getId()).orElseThrow().features())
				.containsExactlyInAnyOrder(ProgramFeature.PEER_MENTORING, ProgramFeature.ISKUR);
		assertThat(programs.findById(draft.getId()).orElseThrow().features())
				.containsExactly(ProgramFeature.OFFICE_HOURS);
	}

	@Test
	void aCoordinatorOfAnotherProgramCannotChangeThem() throws Exception {
		Program draft = semester(ProgramStatus.DRAFT, EnumSet.of(ProgramFeature.PEER_MENTORING));
		coordinatorOf(draft, coordinator);

		setFeatures(draft, otherCoordinator, ProgramFeature.OFFICE_HOURS)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Only the program coordinator can change this program."));

		assertThat(programs.findById(draft.getId()).orElseThrow().features())
				.containsExactly(ProgramFeature.PEER_MENTORING);
	}

	@Test
	void switchingIskurOffWhilePlacementsAreOpenIsRefusedWithTheirCount() throws Exception {
		AppUser intern = account(UserRole.MENTEE, "Ayşe Yılmaz");
		IskurPlacement placement = new IskurPlacement(semester.getId(), intern.getId(), admin.getId(), Instant.now());
		placement.setTeamId(UUID.randomUUID());
		placement.setTeamRole(IskurTeamRole.LEADER);
		placements.saveAndFlush(placement);

		String body = setFeatures(semester, admin, ProgramFeature.PEER_MENTORING)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROGRAM_WORK_OPEN"))
				.andExpect(jsonPath("$.blockers.length()").value(1))
				.andExpect(jsonPath("$.blockers[0].kind").value("ISKUR_PLACEMENT"))
				.andExpect(jsonPath("$.blockers[0].feature").value("ISKUR"))
				.andExpect(jsonPath("$.blockers[0].count").value(1))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(intern.getFullName()).doesNotContain(intern.getEmail())
				.doesNotContain(intern.getId().toString());
		assertThat(programs.findById(semester.getId()).orElseThrow().features())
				.containsExactlyInAnyOrder(ProgramFeature.PEER_MENTORING, ProgramFeature.ISKUR);
	}

	@Test
	void switchingOfficeHoursOffIsRefusedWhileAStudentOfTheSemesterHasAnUpcomingMeeting() throws Exception {
		Program withOfficeHours = semester(ProgramStatus.ACTIVE, EnumSet.of(ProgramFeature.PEER_MENTORING,
				ProgramFeature.OFFICE_HOURS));
		AppUser student = account(UserRole.MENTEE, "Can Er");
		student.setProgramId(withOfficeHours.getId());
		appUsers.saveAndFlush(student);
		meeting(student, Instant.now().plus(2, ChronoUnit.DAYS), null);
		meeting(student, Instant.now().minus(2, ChronoUnit.DAYS), null);
		meeting(student, Instant.now().plus(3, ChronoUnit.DAYS), Instant.now());

		setFeatures(withOfficeHours, admin, ProgramFeature.PEER_MENTORING)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.blockers[0].kind").value("UPCOMING_OFFICE_HOURS_MEETING"))
				.andExpect(jsonPath("$.blockers[0].count").value(1));
	}

	@Test
	void anInvalidSetIsRefusedWithItsCode() throws Exception {
		setFeatures(semester, admin, ProgramFeature.ISKUR)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ProgramFeature.INVALID_SET_CODE))
				.andExpect(jsonPath("$.blockers").doesNotExist());
	}

	@Test
	void aStudentReadsTheFeaturesOfTheirSemester() throws Exception {
		AppUser student = account(UserRole.MENTEE, "Deniz Ak");
		student.setProgramId(semester.getId());
		appUsers.saveAndFlush(student);
		AppUser outside = account(UserRole.MENTEE, "Ece Su");

		call(get("/api/users/{id}", student.getId()), admin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.programFeatures[0]").value("PEER_MENTORING"))
				.andExpect(jsonPath("$.programFeatures[1]").value("ISKUR"));
		call(get("/api/users/{id}", outside.getId()), admin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.programFeatures.length()").value(0));
	}

	private ResultActions setFeatures(Program program, AppUser actor, ProgramFeature... features) throws Exception {
		return mockMvc.perform(put("/api/coordinator/programs/{id}/features", program.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(Map.of("actorId", actor.getId(),
						"features", List.of(features))))
				.with(user("frontend").roles("SERVICE")));
	}

	private ResultActions call(MockHttpServletRequestBuilder builder, AppUser actor) throws Exception {
		return mockMvc.perform(builder.param("actorId", actor.getId().toString())
				.with(user("frontend").roles("SERVICE")));
	}

	private AppUser account(UserRole role, String name) {
		AppUser account = new AppUser();
		account.setFullName(name);
		account.setEmail(UUID.randomUUID() + "@semester-features.test");
		account.setRole(role);
		return appUsers.saveAndFlush(account);
	}

	private Program semester(ProgramStatus status, Set<ProgramFeature> features) {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName("2026 Güz " + UUID.randomUUID());
		created.setProgramType(ProgramType.SEMESTER);
		created.setFeatures(features);
		created.setCreatedBy(admin.getId());
		created.setCreatedAt(Instant.now());
		created.setStatus(status);
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

	private void meeting(AppUser student, Instant at, Instant cancelledAt) {
		OfficeHoursUser studentPerson = officeHoursPerson(student);
		OfficeHoursUser academician = officeHoursPerson(account(UserRole.ACADEMICIAN, "Ahmet Kaya"));
		OfficeHoursRequest request = new OfficeHoursRequest();
		request.setId(UUID.randomUUID());
		request.setStudentId(studentPerson.getId());
		request.setInstructorId(academician.getId());
		request.setStatus(OfficeHoursRequestStatus.ACCEPTED);
		request.setTopic("Staj");
		request.setPreferredSlots("");
		request.setCreatedAt(Instant.now());
		request.setUpdatedAt(Instant.now());
		officeHoursRequests.saveAndFlush(request);
		OfficeHoursMeeting meeting = new OfficeHoursMeeting();
		meeting.setId(UUID.randomUUID());
		meeting.setRequestId(request.getId());
		meeting.setInstructorId(academician.getId());
		meeting.setStudentId(studentPerson.getId());
		meeting.setScheduledAt(at);
		meeting.setDurationMinutes(30);
		meeting.setCreatedAt(Instant.now());
		meeting.setCancelledAt(cancelledAt);
		officeHoursMeetings.saveAndFlush(meeting);
	}

	private OfficeHoursUser officeHoursPerson(AppUser account) {
		return officeHoursUsers.findByAppUserId(account.getId()).orElseGet(() -> {
			OfficeHoursUser person = new OfficeHoursUser();
			person.setId(UUID.randomUUID());
			person.setAppUserId(account.getId());
			person.setFullName(account.getFullName());
			person.setCreatedAt(Instant.now());
			person.setLastLaunchAt(Instant.now());
			return officeHoursUsers.saveAndFlush(person);
		});
	}
}
