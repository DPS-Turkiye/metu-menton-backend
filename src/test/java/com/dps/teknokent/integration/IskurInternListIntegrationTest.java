package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IdentityKind;
import com.dps.teknokent.domain.InstitutionDataSource;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.IskurInternEmail;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.IskurInternEmailRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramRepository;

/**
 * The coordinator's İŞKUR intern list, and what it decides: only listed accounts get the İŞKUR screens, while a plain
 * student of the same program keeps every other student feature. ODTÜ edition, real security chain, H2.
 */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class IskurInternListIntegrationTest {

	private static final String INTERNS = "/api/coordinator/programs/{programId}/iskur/interns";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private ProgramRepository programRepository;
	@Autowired
	private InstitutionPersonRepository institutionPersonRepository;
	@Autowired
	private IskurUnitRepository unitRepository;
	@Autowired
	private IskurTeamRepository teamRepository;
	@Autowired
	private IskurPlacementRepository placementRepository;
	@Autowired
	private IskurInternEmailRepository internEmailRepository;

	private AppUser admin;
	private AppUser coordinator;
	private AppUser otherCoordinator;
	private Program program;
	private Program otherProgram;
	private IskurUnit unit;

	@BeforeEach
	void setUp() {
		admin = user("Yönetici Test", "yonetici@example.test", UserRole.ADMIN, null);
		coordinator = user("Selin Er", "selin@example.test", UserRole.COORDINATOR, null);
		otherCoordinator = user("Başka Koordinatör", "baska.koordinator@example.test", UserRole.COORDINATOR, null);
		program = program("İŞKUR Güz 2026", coordinator);
		otherProgram = program("Başka Program", otherCoordinator);
		unit = unitRepository.saveAndFlush(new IskurUnit(program.getId(), "Birim", 0, coordinator.getId(),
				Instant.now()));
	}

	@Test
	void addsPastedAddressesOnceAndShowsWhoHasSignedUp() throws Exception {
		AppUser ayse = student("Ayşe Yıldız", "Ayse.Yildiz@example.test", program);
		user("Dış Kişi", "dis.kisi@example.test", UserRole.MENTEE, otherProgram);

		add(coordinator, "\" AYSE.YILDIZ@example.test \"", "\"ayse.yildiz@example.test\"", "\"yeni@example.test\"",
				"\"dis.kisi@example.test\"", "\"adres-degil\"", "\"İlker@example.test\"", "\"  \"")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.programId").value(program.getId().toString()))
				.andExpect(jsonPath("$.entries[*].email").value(Matchers.contains(
						"ayse.yildiz@example.test", "dis.kisi@example.test", "yeni@example.test")))
				.andExpect(jsonPath("$.entries[0].account").value("INTERN"))
				.andExpect(jsonPath("$.entries[0].userId").value(ayse.getId().toString()))
				.andExpect(jsonPath("$.entries[0].fullName").value("Ayşe Yıldız"))
				.andExpect(jsonPath("$.entries[0].placed").value(false))
				.andExpect(jsonPath("$.entries[1].account").value("OUTSIDE_PROGRAM"))
				.andExpect(jsonPath("$.entries[1].fullName").doesNotExist())
				.andExpect(jsonPath("$.entries[1].userId").doesNotExist())
				.andExpect(jsonPath("$.entries[2].account").value("NOT_SIGNED_UP"))
				.andExpect(jsonPath("$.counts.listed").value(3))
				.andExpect(jsonPath("$.counts.interns").value(1))
				.andExpect(jsonPath("$.counts.notSignedUp").value(1))
				.andExpect(jsonPath("$.counts.outsideProgram").value(1))
				.andExpect(jsonPath("$.rejected").value(Matchers.contains("adres-degil", "İlker@example.test")));

		add(coordinator, "\"YENI@example.test\"").andExpect(status().isOk())
				.andExpect(jsonPath("$.counts.listed").value(3))
				.andExpect(jsonPath("$.rejected").isEmpty());
		list(coordinator).andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(3));
	}

	@Test
	void anAddressListedBeforeSignUpBecomesAnInternWhenTheAccountJoins() throws Exception {
		add(coordinator, "\"sonra.gelen@example.test\"").andExpect(status().isOk())
				.andExpect(jsonPath("$.entries[0].account").value("NOT_SIGNED_UP"));

		AppUser joined = student("Sonra Gelen", "sonra.gelen@example.test", program);

		list(coordinator).andExpect(jsonPath("$.entries[0].account").value("INTERN"))
				.andExpect(jsonPath("$.entries[0].userId").value(joined.getId().toString()));
		asStudent(get("/api/iskur/onboarding"), joined).andExpect(status().isOk());
	}

	@Test
	void aPlacedInternIsRemovedOnlyAfterLeavingTheirTeam() throws Exception {
		AppUser ayse = student("Ayşe Yıldız", "ayse@example.test", program);
		add(coordinator, "\"ayse@example.test\"", "\"kimse@example.test\"").andExpect(status().isOk());
		IskurTeam team = teamRepository.saveAndFlush(new IskurTeam(unit, "Ekip", 0, Instant.now()));
		IskurPlacement placement = new IskurPlacement(program.getId(), ayse.getId(), coordinator.getId(),
				Instant.now());
		placement.setTeamId(team.getId());
		placement.setTeamRole(IskurTeamRole.MEMBER);
		placementRepository.saveAndFlush(placement);
		UUID ayseEntry = entryId("ayse@example.test");

		list(coordinator).andExpect(jsonPath("$.entries[0].placed").value(true));
		remove(coordinator, ayseEntry).andExpect(status().isConflict());
		remove(coordinator, entryId("kimse@example.test")).andExpect(status().isOk())
				.andExpect(jsonPath("$.entries[*].email").value(Matchers.contains("ayse@example.test")));

		placementRepository.delete(placement);
		placementRepository.flush();
		remove(coordinator, ayseEntry).andExpect(status().isOk()).andExpect(jsonPath("$.entries").isEmpty());
		// Off the list she is still an intern: the list no longer decides.
		asStudent(get("/api/iskur/onboarding"), ayse).andExpect(status().isOk());
	}

	@Test
	void onlyTheProgramsCoordinatorsAndTheAdminManageTheList() throws Exception {
		AppUser student = student("Ayşe Yıldız", "ayse@example.test", program);
		internEmailRepository.saveAndFlush(new IskurInternEmail(otherProgram.getId(), "baska@example.test",
				otherCoordinator.getId(), Instant.now()));
		UUID foreignEntry = internEmailRepository.findByProgramIdOrderByEmailAsc(otherProgram.getId()).get(0)
				.getId();

		list(admin).andExpect(status().isOk());
		list(otherCoordinator).andExpect(status().isBadRequest());
		list(student).andExpect(status().isBadRequest());
		add(otherCoordinator, "\"sizinti@example.test\"").andExpect(status().isBadRequest());
		add(student, "\"ayse@example.test\"").andExpect(status().isBadRequest());
		remove(coordinator, foreignEntry).andExpect(status().isNotFound());
		mockMvc.perform(get(INTERNS, program.getId()).param("actorId", coordinator.getId().toString()))
				.andExpect(status().isUnauthorized());
		assertThat(internEmailRepository.findByProgramIdOrderByEmailAsc(program.getId())).isEmpty();
		assertThat(internEmailRepository.findByProgramIdOrderByEmailAsc(otherProgram.getId())).hasSize(1);
	}

	@Test
	void anArchivedProgramsListIsReadOnlyAndAnEmptyPasteIsRefused() throws Exception {
		send(post(INTERNS, program.getId()), "{\"actorId\":\"%s\",\"emails\":[]}".formatted(coordinator.getId()))
				.andExpect(status().isBadRequest());
		program.setStatus(ProgramStatus.COMPLETED);
		programRepository.saveAndFlush(program);

		list(coordinator).andExpect(status().isOk());
		add(coordinator, "\"ayse@example.test\"").andExpect(status().isBadRequest());
	}

	@Test
	void everyStudentOfTheProgramIsAnInternWithoutBeingListedAndKeepsEveryStudentFeature() throws Exception {
		AppUser plain = student("Düz Öğrenci", "duz@example.test", program);

		// Nobody is listed: the student still gets the placement form from day one.
		asStudent(get("/api/iskur/onboarding"), plain).andExpect(status().isOk());
		asStudent(get("/api/iskur/me"), plain).andExpect(status().isOk());
		// Not placed yet, so there is no week to show.
		asStudent(get("/api/iskur/week"), plain).andExpect(status().isNotFound());

		asStudent(get("/api/peer-mentoring/mentors"), plain).andExpect(status().isOk());
		asStudent(get("/api/peer-mentoring/requests"), plain).andExpect(status().isOk());
		asStudent(get("/api/peer-mentoring/appointments"), plain).andExpect(status().isOk());
		asStudent(get("/api/student/profile-completion"), plain).andExpect(status().isOk());
		// Office Hours answers only the web server's own credential (ROLE_SERVICE), which names the student.
		mockMvc.perform(get("/api/office-hours/me/academicians").param("actorId", plain.getId().toString())
				.with(SecurityMockMvcRequestPostProcessors.user("frontend").roles("SERVICE")))
				.andExpect(status().isOk());

		// The web's İŞKUR home and "Mentorluk Ver" open on this flag.
		asStudent(get("/api/institution/me"), plain).andExpect(status().isOk())
				.andExpect(jsonPath("$.iskurParticipant").value(true));
	}

	private ResultActions list(AppUser actor) throws Exception {
		return mockMvc.perform(get(INTERNS, program.getId())
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions add(AppUser actor, String... quotedEmails) throws Exception {
		return send(post(INTERNS, program.getId()), "{\"actorId\":\"%s\",\"emails\":[%s]}"
				.formatted(actor.getId(), String.join(",", quotedEmails)));
	}

	private ResultActions remove(AppUser actor, UUID entryId) throws Exception {
		return mockMvc.perform(delete(INTERNS + "/{entryId}", program.getId(), entryId)
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
		return mockMvc.perform(request.with(httpBasic("test", "test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions asStudent(MockHttpServletRequestBuilder request, AppUser student) throws Exception {
		return mockMvc.perform(request.param("actorId", student.getId().toString()).with(httpBasic("test", "test")));
	}

	private UUID entryId(String email) {
		return internEmailRepository.findByProgramIdOrderByEmailAsc(program.getId()).stream()
				.filter(entry -> entry.getEmail().equals(email))
				.findFirst()
				.orElseThrow()
				.getId();
	}

	/** A student of the program with an ODTÜ student record, as sign-up or the institution import leaves them. */
	private AppUser student(String name, String email, Program joined) {
		AppUser user = user(name, email, UserRole.MENTEE, joined);
		InstitutionPerson person = new InstitutionPerson("odtu-test", "student-" + user.getId());
		person.setFullName(name);
		person.setEmail(email);
		person.setSource(InstitutionDataSource.IMPORT);
		person.setSyncedAt(Instant.now());
		person.setKinds(Set.of(IdentityKind.STUDENT));
		person.setUser(user);
		institutionPersonRepository.saveAndFlush(person);
		return user;
	}

	private AppUser user(String name, String email, UserRole role, Program joined) {
		AppUser user = new AppUser();
		user.setFullName(name);
		user.setEmail(email);
		user.setRole(role);
		if (joined != null) {
			user.setProgramId(joined.getId());
		}
		return userRepository.saveAndFlush(user);
	}

	private Program program(String name, AppUser creator) {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName(name);
		created.setCreatedBy(creator.getId());
		created.setCreatedAt(Instant.now());
		created.setStatus(ProgramStatus.ACTIVE);
		created.setProgramType(ProgramType.PEER_MENTORING);
		return programRepository.saveAndFlush(created);
	}
}
