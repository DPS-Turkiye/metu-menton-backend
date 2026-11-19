package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.InstitutionDataSource;
import com.dps.teknokent.domain.IskurPlacementStatus;
import com.dps.teknokent.domain.IskurSkillLevel;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.IskurTeamRolePreference;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.requests.IskurOnboardingRequest;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.IskurInternEmail;
import com.dps.teknokent.entity.IskurOnboarding;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurSkill;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.IskurInternEmailRepository;
import com.dps.teknokent.repository.IskurOnboardingRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurSkillRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.repository.StoredDocumentRepository;
import com.dps.teknokent.security.AppUserPrincipal;
import com.dps.teknokent.service.IskurOnboardingService;
import com.dps.teknokent.service.ObjectStorage;
import com.dps.teknokent.util.MeetingSlotTimes;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/**
 * İŞKUR onboarding (Figma 284:2, 02–05) through the ODTÜ edition's real security chain and an H2 database: the intern's
 * form, its documents with file storage on and off, the team reveal, and the coordinators' side.
 */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class IskurOnboardingIntegrationTest {

	private static final String ONBOARDING = "/api/iskur/onboarding";
	private static final String COORDINATOR = "/api/coordinator/programs/{programId}/iskur";
	private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private IskurInternEmailRepository internEmailRepository;
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
	private IskurSkillRepository skillRepository;
	@Autowired
	private IskurOnboardingRepository onboardingRepository;
	@Autowired
	private StoredDocumentRepository storedDocumentRepository;
	@Autowired
	private IskurOnboardingService onboardingService;

	@MockitoBean
	private ObjectStorage objectStorage;

	private AppUser coordinator;
	private AppUser otherCoordinator;
	private Program program;
	private AppUser mert;
	private IskurUnit kreatif;
	private IskurUnit etki;
	private List<UUID> kreatifSkills;
	private List<UUID> etkiSkills;

	@BeforeEach
	void setUp() throws Exception {
		coordinator = user("Selin Er", UserRole.COORDINATOR, null);
		otherCoordinator = user("Başka Koordinatör", UserRole.COORDINATOR, null);
		program = program("İŞKUR Güz 2026", coordinator);
		mert = user("Mert Demir", UserRole.MENTEE, program);
		record(mert, "Endüstri Mühendisliği", 3);

		mockMvc.perform(post(COORDINATOR + "/structure/default", program.getId()).with(httpBasic("test", "test"))
				.contentType(MediaType.APPLICATION_JSON).content(actor(coordinator)))
				.andExpect(status().isOk());
		List<IskurUnit> units = unitRepository.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId());
		kreatif = units.get(0);
		etki = units.get(3);
		kreatifSkills = skillIdsOf(kreatif);
		etkiSkills = skillIdsOf(etki);
	}

	@Test
	void theDefaultStructureAsksFigmasSkillsInTwoOfItsUnits() throws Exception {
		read(mert)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.form.submittedAt").doesNotExist())
				.andExpect(jsonPath("$.form.unitIds").isEmpty())
				.andExpect(jsonPath("$.context.programName").value("İŞKUR Güz 2026"))
				.andExpect(jsonPath("$.context.department").value("Endüstri Mühendisliği"))
				.andExpect(jsonPath("$.context.classYear").value(3))
				.andExpect(jsonPath("$.context.units[0].name").value("Kreatif ve Dijital Üretim"))
				.andExpect(jsonPath("$.context.units[0].teamNames").value(Matchers.contains(
						"Medya ve Kreatif Üretim", "Dijital Dönüşüm ve Teknoloji")))
				.andExpect(jsonPath("$.context.units[0].skills[*].name").value(Matchers.contains(
						"Görsel tasarım", "Video ve fotoğraf", "Sosyal medya", "Web ve UX", "Yazılım ve veri")))
				.andExpect(jsonPath("$.context.units[1].skills").isEmpty())
				.andExpect(jsonPath("$.context.units[3].skills.length()").value(4))
				.andExpect(jsonPath("$.context.documentsEnabled").value(false))
				.andExpect(jsonPath("$.context.maxIskurDays").value(140))
				.andExpect(jsonPath("$.context.termWeeks").value(24))
				.andExpect(jsonPath("$.context.answerMaxLength").value(500));
	}

	@Test
	void countsTheTermFromTheProgramsDates() throws Exception {
		program.setStartDate(LocalDate.of(2026, 9, 28));
		program.setEndDate(LocalDate.of(2026, 12, 31));
		programRepository.saveAndFlush(program);

		read(mert).andExpect(jsonPath("$.context.termWeeks").value(14));
	}

	@Test
	void refusesAnyoneWhoIsNotAnInternOfAnIskurProgram() throws Exception {
		AppUser outsider = user("Dış Öğrenci", UserRole.MENTEE, program("Yapısız Program", coordinator));

		read(outsider).andExpect(status().isNotFound());
		me(outsider).andExpect(status().isNotFound());
		save(outsider, complete()).andExpect(status().isNotFound());
	}

	@Test
	void keepsADraftAsItIsAndReturnsIt() throws Exception {
		IskurOnboardingRequest draft = new IskurOnboardingRequest(null, 2,
				List.of(range(DayOfWeek.TUESDAY, "10:00", "13:30")), null, List.of(kreatif.getId()),
				List.of(new IskurOnboardingRequest.SkillAnswer(kreatifSkills.getFirst(), IskurSkillLevel.GOOD)),
				List.of(IskurTeamRolePreference.PLANNER), null);

		save(mert, draft)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.form.weeklyDays").value(2))
				.andExpect(jsonPath("$.form.schedule[0].day").value("TUESDAY"))
				.andExpect(jsonPath("$.form.schedule[0].start").value("10:00"))
				.andExpect(jsonPath("$.form.schedule[0].end").value("13:30"))
				.andExpect(jsonPath("$.form.skills[0].level").value("GOOD"))
				.andExpect(jsonPath("$.form.submittedAt").doesNotExist());
		read(mert).andExpect(jsonPath("$.form.unitIds[0]").value(kreatif.getId().toString()))
				.andExpect(jsonPath("$.form.teamRoles").value(Matchers.contains("PLANNER")));

		save(mert, withUnits(withSkills(draft, List.of()), List.of(etki.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.form.unitIds").value(Matchers.contains(etki.getId().toString())))
				.andExpect(jsonPath("$.form.skills").isEmpty());
	}

	@Test
	void refusesADraftWithAnotherProgramsUnitsOrSkillsOrABadRange() throws Exception {
		Program other = program("Başka İŞKUR", otherCoordinator);
		IskurUnit foreignUnit = unitRepository.saveAndFlush(new IskurUnit(other.getId(), "Birim", 0,
				otherCoordinator.getId(), Instant.now()));
		IskurSkill foreignSkill = skillRepository.saveAndFlush(new IskurSkill(other.getId(), "Yetkinlik",
				Instant.now()));

		save(mert, withUnits(complete(), List.of(foreignUnit.getId()))).andExpect(status().isBadRequest());
		save(mert, withSkills(complete(), List.of(
				new IskurOnboardingRequest.SkillAnswer(foreignSkill.getId(), IskurSkillLevel.GOOD))))
				.andExpect(status().isBadRequest());
		save(mert, withSchedule(complete(), List.of(range(DayOfWeek.MONDAY, "14:00", "10:00"))))
				.andExpect(status().isBadRequest());
		save(mert, withSchedule(complete(), List.of(range(DayOfWeek.MONDAY, "9:00", "10:00"))))
				.andExpect(status().isBadRequest());
		save(mert, withSchedule(complete(), List.of(range(DayOfWeek.MONDAY, "08:00", "09:00"),
				range(DayOfWeek.MONDAY, "09:00", "10:00"), range(DayOfWeek.MONDAY, "10:00", "11:00"),
				range(DayOfWeek.MONDAY, "11:00", "12:00"), range(DayOfWeek.MONDAY, "12:00", "13:00"))))
				.andExpect(status().isBadRequest());
		save(mert, withRoles(complete(), List.of(IskurTeamRolePreference.PLANNER, IskurTeamRolePreference.EXECUTOR,
				IskurTeamRolePreference.CONNECTOR))).andExpect(status().isBadRequest());
		save(mert, withLeadership(complete(), new IskurOnboardingRequest.Leadership(true, "a".repeat(501), "b", "c")))
				.andExpect(status().isBadRequest());
		save(mert, withLeadership(complete(), new IskurOnboardingRequest.Leadership(true, "a".repeat(500), "b", "c")))
				.andExpect(status().isOk());
	}

	@Test
	void submitsACompleteFormWithoutACvWhileDocumentsAreOff() throws Exception {
		submit(mert, complete())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.form.submittedAt").exists())
				.andExpect(jsonPath("$.context.documentsEnabled").value(false));

		save(mert, complete()).andExpect(status().isConflict());
		submit(mert, complete()).andExpect(status().isConflict());
		assertThat(onboardingService.workDays(program.getId(), mert.getId()))
				.containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
	}

	@Test
	void dropsRatingsOfUnitsTheInternDidNotPick() throws Exception {
		List<IskurOnboardingRequest.SkillAnswer> answers = new ArrayList<>(complete().skills());
		answers.add(new IskurOnboardingRequest.SkillAnswer(etkiSkills.getFirst(), IskurSkillLevel.EXPERT));

		submit(mert, withSkills(complete(), answers))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.form.skills.length()").value(kreatifSkills.size()));
	}

	@Test
	void refusesToSubmitAFormThatIsNotComplete() throws Exception {
		submit(mert, withDays(complete(), null)).andExpect(status().isBadRequest());
		submit(mert, withDays(complete(), 2)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(Matchers.containsString("7,5")));
		submit(mert, withSchedule(complete(), List.of(range(DayOfWeek.MONDAY, "09:00", "16:30"),
				range(DayOfWeek.MONDAY, "16:00", "23:30"), range(DayOfWeek.TUESDAY, "09:00", "16:30"))))
				.andExpect(status().isBadRequest());
		submit(mert, withSkills(complete(), complete().skills().subList(1, kreatifSkills.size())))
				.andExpect(status().isBadRequest());
		submit(mert, withRoles(complete(), List.of())).andExpect(status().isBadRequest());
		submit(mert, withUnits(complete(), List.of())).andExpect(status().isBadRequest());
		submit(mert, withPrior(complete(), null)).andExpect(status().isBadRequest());
		// A refused submit rolls back in production; inside this test's one transaction the draft row stays, unsent.
		assertThat(onboardingRepository.findByProgramIdAndUserId(program.getId(), mert.getId())
				.map(IskurOnboarding::isSubmitted)).hasValue(false);
		assertThat(onboardingService.workDays(program.getId(), mert.getId())).isEmpty();
	}

	@Test
	void checksTheLeadershipApplication() throws Exception {
		IskurOnboardingRequest.Leadership answers = new IskurOnboardingRequest.Leadership(true, "Planlamayı severim.",
				"Bahar şenliğinde tanıtım ekibini yürüttüm.", "Önce birebir konuşurum.");
		IskurOnboardingRequest oneDay = withSchedule(withDays(complete(), 1),
				List.of(range(DayOfWeek.MONDAY, "09:00", "16:30")));

		submit(mert, withLeadership(oneDay, answers)).andExpect(status().isBadRequest());
		submit(mert, withPrior(withLeadership(complete(), answers), 100)).andExpect(status().isBadRequest());
		submit(mert, withLeadership(complete(), new IskurOnboardingRequest.Leadership(true, "Neden", " ", "Çatışma")))
				.andExpect(status().isBadRequest());
		submit(mert, withLeadership(complete(), answers))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.form.leadership.wants").value(true))
				.andExpect(jsonPath("$.form.leadership.motivation").value("Planlamayı severim."));
	}

	@Test
	void keepsNoLeadershipAnswersInADraftThatDoesNotApply() throws Exception {
		save(mert, withLeadership(complete(), new IskurOnboardingRequest.Leadership(false, "Taslak", "Deneyim", null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.form.leadership.motivation").doesNotExist())
				.andExpect(jsonPath("$.form.leadership.experience").doesNotExist());
	}

	@Test
	void forgetsTheAnswersOfAWithdrawnLeadershipApplication() throws Exception {
		submit(mert, withLeadership(complete(), new IskurOnboardingRequest.Leadership(false, "Taslak", null, null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.form.leadership.wants").value(false))
				.andExpect(jsonPath("$.form.leadership.motivation").doesNotExist());
	}

	@Test
	void refusesUploadsWhileDocumentsAreOff() throws Exception {
		upload(mert, "CV").andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.message").value("Belge yükleme şu an kapalı."));
	}

	@Test
	void needsACvWhenDocumentsAreOnAndReplacesAnUploadedOne() throws Exception {
		when(objectStorage.isConfigured()).thenReturn(true);
		submit(mert, complete()).andExpect(status().isBadRequest());

		upload(mert, "CV").andExpect(status().isOk())
				.andExpect(jsonPath("$.form.documents.cv.fileName").value("cv.pdf"))
				.andExpect(jsonPath("$.context.documentsEnabled").value(true));
		UUID first = onboardingRepository.findByProgramIdAndUserId(program.getId(), mert.getId()).orElseThrow()
				.getCvDocumentId();
		upload(mert, "CV").andExpect(status().isOk());
		IskurOnboarding form = onboardingRepository.findByProgramIdAndUserId(program.getId(), mert.getId())
				.orElseThrow();
		assertThat(form.getCvDocumentId()).isNotEqualTo(first);
		assertThat(storedDocumentRepository.findById(first)).isEmpty();
		assertThat(storedDocumentRepository.findById(form.getCvDocumentId()).orElseThrow().getKind().value())
				.isEqualTo("iskur-document");

		upload(mert, "TRANSCRIPT").andExpect(status().isOk());
		mockMvc.perform(delete(ONBOARDING + "/documents/TRANSCRIPT").param("actorId", mert.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.form.documents.transcript").doesNotExist());

		submit(mert, complete()).andExpect(status().isOk());
		upload(mert, "CV").andExpect(status().isConflict());
	}

	@Test
	void keepsIskurFilesOffTheGeneralUploadAndFileList() throws Exception {
		when(objectStorage.isConfigured()).thenReturn(true);
		MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF);
		mockMvc.perform(multipart("/api/documents").file(file).param("kind", "iskur-document")
				.with(authentication(signedIn(mert))))
				.andExpect(status().isBadRequest());
		upload(mert, "CV").andExpect(status().isOk());
		mockMvc.perform(get("/api/documents").with(authentication(signedIn(mert))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void aSignedInInternIsAlwaysTheActor() throws Exception {
		AppUser classmate = user("Ayşe Yıldız", UserRole.MENTEE, program);
		mockMvc.perform(put(ONBOARDING).with(authentication(signedIn(mert)))
				.contentType(MediaType.APPLICATION_JSON).content(json(withActor(complete(), classmate.getId()))))
				.andExpect(status().isOk());

		assertThat(onboardingRepository.findByProgramIdAndUserId(program.getId(), mert.getId())).isPresent();
		assertThat(onboardingRepository.findByProgramIdAndUserId(program.getId(), classmate.getId())).isEmpty();
	}

	@Test
	void waitsForPlacementBeforeRevealingATeam() throws Exception {
		me(mert)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.programName").value("İŞKUR Güz 2026"))
				.andExpect(jsonPath("$.onboardingSubmitted").value(false))
				.andExpect(jsonPath("$.placement").doesNotExist());
	}

	@Test
	void revealsTheInternsTeamWithItsLeadersAndUnitCoordinator() throws Exception {
		program.setStartDate(LocalDate.now(MeetingSlotTimes.APP_ZONE).minusDays(8));
		programRepository.saveAndFlush(program);
		kreatif.setCoordinatorId(coordinator.getId());
		unitRepository.saveAndFlush(kreatif);
		IskurTeam medya = teamRepository.findByUnitId(kreatif.getId()).stream()
				.filter(team -> team.getName().equals("Medya ve Kreatif Üretim"))
				.findFirst().orElseThrow();
		AppUser zeynep = user("Zeynep Kaya", UserRole.MENTEE, program);
		AppUser can = user("Can Tekin", UserRole.MENTEE, program);
		AppUser deniz = user("Deniz Arslan", UserRole.MENTEE, program);
		place(zeynep, medya, IskurTeamRole.LEADER, null);
		place(can, medya, IskurTeamRole.LEADER, null);
		place(mert, medya, IskurTeamRole.MEMBER, zeynep);
		place(deniz, medya, IskurTeamRole.MEMBER, can);
		submit(mert, complete()).andExpect(status().isOk());
		submit(zeynep, withSchedule(complete(), List.of(range(DayOfWeek.TUESDAY, "09:00", "16:30"),
				range(DayOfWeek.THURSDAY, "09:00", "16:30"), range(DayOfWeek.FRIDAY, "09:00", "16:30"))))
				.andExpect(status().isOk());

		me(mert)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.onboardingSubmitted").value(true))
				.andExpect(jsonPath("$.placement.status").value("PROVISIONAL"))
				.andExpect(jsonPath("$.placement.trialWeek").value(2))
				.andExpect(jsonPath("$.placement.myRole").value("MEMBER"))
				.andExpect(jsonPath("$.placement.myLeader.fullName").value("Zeynep Kaya"))
				.andExpect(jsonPath("$.placement.unit.name").value("Kreatif ve Dijital Üretim"))
				.andExpect(jsonPath("$.placement.unit.coordinator.fullName").value("Selin Er"))
				.andExpect(jsonPath("$.placement.team.name").value("Medya ve Kreatif Üretim"))
				.andExpect(jsonPath("$.placement.team.people[*].fullName").value(Matchers.contains(
						"Can Tekin", "Deniz Arslan", "Zeynep Kaya", "Mert Demir")))
				.andExpect(jsonPath("$.placement.team.people[3].me").value(true))
				.andExpect(jsonPath("$.placement.team.people[3].leaderId").value(zeynep.getId().toString()))
				.andExpect(jsonPath("$.placement.commonDays").value(Matchers.contains("TUESDAY", "THURSDAY")))
				.andExpect(content().string(Matchers.not(Matchers.containsString("@example.test"))));
	}

	@Test
	void showsNoTrialWeekOnceThePlacementIsFinalOrTheTrialIsOver() throws Exception {
		IskurTeam team = teamRepository.findByUnitId(kreatif.getId()).getFirst();
		IskurPlacement placement = place(mert, team, IskurTeamRole.LEADER, null);
		me(mert).andExpect(jsonPath("$.placement.trialWeek").doesNotExist())
				.andExpect(jsonPath("$.placement.myLeader").doesNotExist())
				.andExpect(jsonPath("$.placement.unit.coordinator").doesNotExist())
				.andExpect(jsonPath("$.placement.commonDays").isEmpty());

		program.setStartDate(LocalDate.now(MeetingSlotTimes.APP_ZONE).minusWeeks(5));
		programRepository.saveAndFlush(program);
		me(mert).andExpect(jsonPath("$.placement.trialWeek").doesNotExist());

		program.setStartDate(LocalDate.now(MeetingSlotTimes.APP_ZONE).plusDays(3));
		programRepository.saveAndFlush(program);
		me(mert).andExpect(jsonPath("$.placement.trialWeek").value(1));

		placement.finalizeBy(coordinator.getId(), Instant.now());
		placementRepository.saveAndFlush(placement);
		me(mert).andExpect(jsonPath("$.placement.status").value("FINAL"))
				.andExpect(jsonPath("$.placement.trialWeek").doesNotExist());
	}

	@Test
	void showsCoordinatorsTheSubmittedFormsAndCountsTheDrafts() throws Exception {
		AppUser ayse = user("Ayşe Yıldız", UserRole.MENTEE, program);
		AppUser leaver = user("Ayrılan Öğrenci", UserRole.MENTEE, program);
		submit(mert, withLeadership(complete(), new IskurOnboardingRequest.Leadership(true, "Neden", "Deneyim",
				"Çatışma"))).andExpect(status().isOk());
		save(ayse, complete()).andExpect(status().isOk());
		submit(leaver, complete()).andExpect(status().isOk());
		leaver.setProgramId(null);
		userRepository.saveAndFlush(leaver);

		applications(coordinator)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.units[0].skills.length()").value(5))
				.andExpect(jsonPath("$.applications.length()").value(1))
				.andExpect(jsonPath("$.applications[0].fullName").value("Mert Demir"))
				.andExpect(jsonPath("$.applications[0].weeklyDays").value(3))
				.andExpect(jsonPath("$.applications[0].remainingIskurDays").value(140))
				.andExpect(jsonPath("$.applications[0].leadership.conflict").value("Çatışma"))
				.andExpect(jsonPath("$.drafts").value(1));
	}

	@Test
	void keepsTheApplicationsFromInternsAndOtherProgramsCoordinators() throws Exception {
		applications(mert).andExpect(status().isBadRequest());
		applications(otherCoordinator).andExpect(status().isBadRequest());
		document(mert, mert, "CV").andExpect(status().isBadRequest());
		mockMvc.perform(put(COORDINATOR + "/units/{unitId}/skills", program.getId(), kreatif.getId())
				.with(httpBasic("test", "test")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"" + mert.getId() + "\",\"names\":[\"Yeni\"]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void letsCoordinatorsDownloadTheFilesOfASubmittedFormOnly() throws Exception {
		when(objectStorage.isConfigured()).thenReturn(true);
		when(objectStorage.require(anyString())).thenReturn(PDF);
		upload(mert, "CV").andExpect(status().isOk());
		document(coordinator, mert, "CV").andExpect(status().isNotFound());

		submit(mert, complete()).andExpect(status().isOk());
		document(coordinator, mert, "CV")
				.andExpect(status().isOk())
				.andExpect(content().bytes(PDF));
		document(coordinator, mert, "TRANSCRIPT").andExpect(status().isNotFound());
	}

	@Test
	void replacesAUnitsSkillsAndKeepsAnswersToTheOnesItKeeps() throws Exception {
		submit(mert, complete()).andExpect(status().isOk());

		mockMvc.perform(put(COORDINATOR + "/units/{unitId}/skills", program.getId(), kreatif.getId())
				.with(httpBasic("test", "test")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"" + coordinator.getId()
						+ "\",\"names\":[\"  web ve ux \",\"Etkinlik organizasyonu\",\"Fotoğraf\",\"Fotoğraf\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.units[0].skills[*].name").value(Matchers.contains(
						"Web ve UX", "Etkinlik organizasyonu", "Fotoğraf")))
				.andExpect(jsonPath("$.units[0].skills[0].id").value(kreatifSkills.get(3).toString()))
				.andExpect(jsonPath("$.units[0].skills[1].id").value(etkiSkills.getFirst().toString()))
				.andExpect(jsonPath("$.applications[0].skills[*].skillId").value(Matchers.hasItem(
						kreatifSkills.get(3).toString())));
		assertThat(skillIdsOf(kreatif)).hasSize(3);
	}

	private IskurOnboardingRequest complete() {
		return new IskurOnboardingRequest(null, 3,
				List.of(range(DayOfWeek.MONDAY, "10:00", "14:00"), range(DayOfWeek.TUESDAY, "10:00", "13:30"),
						range(DayOfWeek.WEDNESDAY, "09:00", "13:00"), range(DayOfWeek.WEDNESDAY, "14:00", "17:30"),
						range(DayOfWeek.THURSDAY, "09:00", "16:30")),
				0, List.of(kreatif.getId()),
				kreatifSkills.stream()
						.map(id -> new IskurOnboardingRequest.SkillAnswer(id, IskurSkillLevel.BEGINNER))
						.toList(),
				List.of(IskurTeamRolePreference.IDEA_GENERATOR, IskurTeamRolePreference.EXECUTOR),
				new IskurOnboardingRequest.Leadership(false, null, null, null));
	}

	private static IskurOnboardingRequest withDays(IskurOnboardingRequest r, Integer days) {
		return new IskurOnboardingRequest(r.actorId(), days, r.schedule(), r.priorIskurDays(), r.unitIds(),
				r.skills(), r.teamRoles(), r.leadership());
	}

	private static IskurOnboardingRequest withSchedule(IskurOnboardingRequest r,
			List<IskurOnboardingRequest.Range> schedule) {
		return new IskurOnboardingRequest(r.actorId(), r.weeklyDays(), schedule, r.priorIskurDays(), r.unitIds(),
				r.skills(), r.teamRoles(), r.leadership());
	}

	private static IskurOnboardingRequest withPrior(IskurOnboardingRequest r, Integer prior) {
		return new IskurOnboardingRequest(r.actorId(), r.weeklyDays(), r.schedule(), prior, r.unitIds(), r.skills(),
				r.teamRoles(), r.leadership());
	}

	private static IskurOnboardingRequest withUnits(IskurOnboardingRequest r, List<UUID> unitIds) {
		return new IskurOnboardingRequest(r.actorId(), r.weeklyDays(), r.schedule(), r.priorIskurDays(), unitIds,
				r.skills(), r.teamRoles(), r.leadership());
	}

	private static IskurOnboardingRequest withSkills(IskurOnboardingRequest r,
			List<IskurOnboardingRequest.SkillAnswer> skills) {
		return new IskurOnboardingRequest(r.actorId(), r.weeklyDays(), r.schedule(), r.priorIskurDays(), r.unitIds(),
				skills, r.teamRoles(), r.leadership());
	}

	private static IskurOnboardingRequest withRoles(IskurOnboardingRequest r, List<IskurTeamRolePreference> roles) {
		return new IskurOnboardingRequest(r.actorId(), r.weeklyDays(), r.schedule(), r.priorIskurDays(), r.unitIds(),
				r.skills(), roles, r.leadership());
	}

	private static IskurOnboardingRequest withLeadership(IskurOnboardingRequest r,
			IskurOnboardingRequest.Leadership leadership) {
		return new IskurOnboardingRequest(r.actorId(), r.weeklyDays(), r.schedule(), r.priorIskurDays(), r.unitIds(),
				r.skills(), r.teamRoles(), leadership);
	}

	private static IskurOnboardingRequest withActor(IskurOnboardingRequest r, UUID actorId) {
		return new IskurOnboardingRequest(actorId, r.weeklyDays(), r.schedule(), r.priorIskurDays(), r.unitIds(),
				r.skills(), r.teamRoles(), r.leadership());
	}

	private static IskurOnboardingRequest.Range range(DayOfWeek day, String start, String end) {
		return new IskurOnboardingRequest.Range(day, start, end);
	}

	private ResultActions read(AppUser actor) throws Exception {
		return mockMvc.perform(get(ONBOARDING).param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions save(AppUser actor, IskurOnboardingRequest request) throws Exception {
		return send(put(ONBOARDING), withActor(request, actor.getId()));
	}

	private ResultActions submit(AppUser actor, IskurOnboardingRequest request) throws Exception {
		return send(post(ONBOARDING + "/submit"), withActor(request, actor.getId()));
	}

	private ResultActions send(MockHttpServletRequestBuilder request, IskurOnboardingRequest body) throws Exception {
		return mockMvc.perform(request.with(httpBasic("test", "test")).contentType(MediaType.APPLICATION_JSON)
				.content(json(body)));
	}

	private ResultActions upload(AppUser actor, String slot) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF);
		return mockMvc.perform(multipart(ONBOARDING + "/documents/{slot}", slot).file(file)
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions me(AppUser actor) throws Exception {
		return mockMvc.perform(get("/api/iskur/me").param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions applications(AppUser actor) throws Exception {
		return mockMvc.perform(get(COORDINATOR + "/applications", program.getId())
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions document(AppUser actor, AppUser intern, String slot) throws Exception {
		return mockMvc.perform(get(COORDINATOR + "/applications/{userId}/documents/{slot}", program.getId(),
				intern.getId(), slot)
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private String json(IskurOnboardingRequest request) {
		return jsonMapper.writeValueAsString(request);
	}

	private static String actor(AppUser actor) {
		return "{\"actorId\":\"" + actor.getId() + "\"}";
	}

	private List<UUID> skillIdsOf(IskurUnit unit) throws Exception {
		String body = read(mert).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		List<String> ids = JsonPath.read(body, "$.context.units[?(@.id == '" + unit.getId() + "')].skills[*].id");
		return ids.stream().map(UUID::fromString).toList();
	}

	private IskurPlacement place(AppUser who, IskurTeam team, IskurTeamRole role, AppUser leader) {
		IskurPlacement placement = new IskurPlacement(program.getId(), who.getId(), coordinator.getId(),
				Instant.now());
		placement.setTeamId(team.getId());
		placement.setTeamRole(role);
		placement.setLeaderId(leader == null ? null : leader.getId());
		placement.setStatus(IskurPlacementStatus.PROVISIONAL);
		return placementRepository.saveAndFlush(placement);
	}

	private AppUser user(String name, UserRole role, Program joined) {
		AppUser user = new AppUser();
		user.setFullName(name);
		user.setEmail(UUID.randomUUID() + "@example.test");
		user.setRole(role);
		if (joined != null) {
			user.setProgramId(joined.getId());
		}
		AppUser saved = userRepository.saveAndFlush(user);
		if (joined != null && role == UserRole.MENTEE) {
			internOf(joined, saved);
		}
		return saved;
	}

	/** Puts the person's address on the program's İŞKUR intern list, as the coordinator would. */
	private void internOf(Program listing, AppUser person) {
		internEmailRepository.saveAndFlush(new IskurInternEmail(listing.getId(),
				person.getEmail().toLowerCase(Locale.ROOT), null, Instant.now()));
	}

	private void record(AppUser user, String department, int classYear) {
		InstitutionPerson person = new InstitutionPerson("odtu", UUID.randomUUID().toString());
		person.setUser(user);
		person.setFullName(user.getFullName());
		person.setDepartmentName(department);
		person.setClassYear(classYear);
		person.setSource(InstitutionDataSource.DEV_STUB);
		person.setSyncedAt(Instant.now());
		institutionPersonRepository.saveAndFlush(person);
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

	private static UsernamePasswordAuthenticationToken signedIn(AppUser user) {
		return new UsernamePasswordAuthenticationToken(new AppUserPrincipal(
				user.getId(), user.getEmail(), user.getRole(), user.getStatus(), "iskur-onboarding-uid"), null,
				List.of());
	}
}
