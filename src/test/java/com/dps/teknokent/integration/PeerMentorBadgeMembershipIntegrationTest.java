package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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

import com.dps.teknokent.domain.IdentityKind;
import com.dps.teknokent.domain.InstitutionDataSource;
import com.dps.teknokent.domain.PeerTopicCategory;
import com.dps.teknokent.domain.ProgramMentorStatus;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.responses.MentorProfileResponse;
import com.dps.teknokent.dto.responses.MentorProgramResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.IskurInternEmail;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramMentor;
import com.dps.teknokent.entity.ProgramPeerCategory;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.IskurInternEmailRepository;
import com.dps.teknokent.repository.ProgramMentorRepository;
import com.dps.teknokent.repository.ProgramPeerCategoryRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.service.MentorProfileService;

/**
 * The Akran Mentoru badge makes the peer mentor's program membership with no role. Every mentor list must still
 * answer once that mentor has a profile; Keşfet answered 500 while the role projection was non-null. ODTÜ edition,
 * real security chain, H2.
 */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class PeerMentorBadgeMembershipIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private ProgramRepository programRepository;
	@Autowired
	private ProgramMentorRepository programMentorRepository;
	@Autowired
	private ProgramPeerCategoryRepository categoryRepository;
	@Autowired
	private IskurInternEmailRepository internEmailRepository;
	@Autowired
	private InstitutionPersonRepository institutionPersonRepository;
	@Autowired
	private MentorProfileService mentorProfileService;

	private AppUser coordinator;
	private AppUser mentor;
	private AppUser mentee;
	private Program program;

	@BeforeEach
	void setUp() {
		coordinator = user("Selin Er", "selin@example.test", UserRole.COORDINATOR, null);
		program = program("İŞKUR Güz 2026", coordinator);
		mentor = student("Mehmet Kaya", "mehmet@example.test");
		mentee = student("Ayşe Yılmaz", "ayse@example.test");
		categoryRepository.saveAndFlush(new ProgramPeerCategory(program.getId(), PeerTopicCategory.CAREER_INTERNSHIP,
				coordinator.getId(), Instant.now()));
		internEmailRepository.saveAndFlush(new IskurInternEmail(program.getId(), "mehmet@example.test",
				coordinator.getId(), Instant.now()));
	}

	@Test
	void aBadgeMadePeerMentorIsListedOnceTheProfileIsPublished() throws Exception {
		send(post("/api/coordinator/programs/{programId}/badges/awards", program.getId()),
				"{\"actorId\":\"%s\",\"userId\":\"%s\"}".formatted(coordinator.getId(), mentor.getId()))
				.andExpect(status().isOk());
		ProgramMentor membership = programMentorRepository.findByProgramIdAndMentorId(program.getId(), mentor.getId())
				.orElseThrow();
		assertThat(membership.getStatus()).isEqualTo(ProgramMentorStatus.ACCEPTED);
		assertThat(membership.getRole()).isNull();

		send(put("/api/peer-mentoring/profile"), """
				{"actorId":"%s","categories":["CAREER_INTERNSHIP"],
				 "helpWith":"Staj başvurusu ve CV üzerine birlikte çalışabiliriz."}
				""".formatted(mentor.getId()))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/peer-mentoring/mentors").param("actorId", mentee.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].mentorId").value(mentor.getId().toString()))
				.andExpect(jsonPath("$[0].fullName").value("Mehmet Kaya"));

		assertProgramWithoutRole(mentorProfileService.findAll());
		assertProgramWithoutRole(mentorProfileService.findReachable(mentee.getId()));
		assertProgramWithoutRole(mentorProfileService.findAmongExcept(Set.of(mentor.getId()), mentee.getId()));
	}

	private void assertProgramWithoutRole(List<MentorProfileResponse> mentors) {
		List<MentorProgramResponse> programs = mentors.stream()
				.filter(profile -> profile.id().equals(mentor.getId()))
				.findFirst()
				.orElseThrow()
				.programs();
		assertThat(programs).containsExactly(
				new MentorProgramResponse(program.getId(), program.getName(), ProgramStatus.ACTIVE, null));
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
		return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body)
				.with(httpBasic("test", "test")));
	}

	/** A student of the program with an ODTÜ student record, as sign-up or the institution import leaves them. */
	private AppUser student(String name, String email) {
		AppUser user = user(name, email, UserRole.MENTEE, program);
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
