package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
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

import com.dps.teknokent.domain.BadgeCode;
import com.dps.teknokent.domain.BadgeSource;
import com.dps.teknokent.domain.BadgeStatus;
import com.dps.teknokent.domain.PeerTopicCategory;
import com.dps.teknokent.domain.PrismDecision;
import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramMentorInvitedBy;
import com.dps.teknokent.domain.ProgramMentorStatus;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.MentorProfile;
import com.dps.teknokent.entity.PrismTrainingLink;
import com.dps.teknokent.entity.PrismTrainingReview;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinator;
import com.dps.teknokent.entity.ProgramMentor;
import com.dps.teknokent.entity.ProgramPeerCategory;
import com.dps.teknokent.entity.TrainingModule;
import com.dps.teknokent.entity.TrainingQuestion;
import com.dps.teknokent.entity.UserBadge;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.MentorProfileRepository;
import com.dps.teknokent.repository.PrismTrainingLinkRepository;
import com.dps.teknokent.repository.PrismTrainingReviewRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramMentorRepository;
import com.dps.teknokent.repository.ProgramPeerCategoryRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.repository.TrainingModuleRepository;
import com.dps.teknokent.repository.TrainingQuestionRepository;
import com.dps.teknokent.repository.UserBadgeRepository;
import com.dps.teknokent.service.ProgramPeerCategoryService;

/**
 * Launching a semester rolls the running one over (Hocam): it is completed, its accounts move, its Akran mentors keep
 * a gate badge and an accepted row, and a reviewed training stays reviewed. Only the admin previews or launches.
 */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class SemesterLaunchIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private AppUserRepository appUsers;
	@Autowired
	private ProgramRepository programs;
	@Autowired
	private ProgramCoordinatorRepository programCoordinators;
	@Autowired
	private ProgramMentorRepository programMentors;
	@Autowired
	private UserBadgeRepository badges;
	@Autowired
	private PrismTrainingReviewRepository prismReviews;
	@Autowired
	private ProgramPeerCategoryRepository peerCategories;
	@Autowired
	private ProgramPeerCategoryService peerCategoryService;
	@Autowired
	private TrainingModuleRepository trainingModules;
	@Autowired
	private TrainingQuestionRepository trainingQuestions;
	@Autowired
	private PrismTrainingLinkRepository prismLinks;
	@Autowired
	private MentorProfileRepository mentorProfiles;

	private AppUser admin;
	private AppUser coordinator;
	private Program fall;
	private Program spring;

	@BeforeEach
	void setUp() {
		admin = account(UserRole.ADMIN, "Türkan Kaya", UserAccountStatus.APPROVED, null);
		coordinator = account(UserRole.COORDINATOR, "Elif Demir", UserAccountStatus.APPROVED, null);
		fall = semester("2026 Güz", ProgramStatus.ACTIVE);
		spring = semester("2027 Bahar", ProgramStatus.DRAFT);
		coordinatorOf(fall);
		coordinatorOf(spring);
	}

	@Test
	void theAdminSeesWhatALaunchWillMoveAndACoordinatorDoesNot() throws Exception {
		account(UserRole.MENTEE, "Ayşe Yılmaz", UserAccountStatus.APPROVED, fall);
		account(UserRole.MENTEE, "Can Er", UserAccountStatus.PENDING, fall);
		account(UserRole.MENTEE, "Silinmiş", UserAccountStatus.DELETED, fall);
		AppUser mentor = account(UserRole.MENTEE, "Deniz Ak", UserAccountStatus.APPROVED, fall);
		gate(mentor, fall);

		call(get("/api/coordinator/programs/{id}/launch-preview", spring.getId()), admin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.previous.id").value(fall.getId().toString()))
				.andExpect(jsonPath("$.previous.name").value("2026 Güz"))
				.andExpect(jsonPath("$.students").value(3))
				.andExpect(jsonPath("$.peerMentors").value(1));
		call(get("/api/coordinator/programs/{id}/launch-preview", spring.getId()), coordinator)
				.andExpect(status().isForbidden());
	}

	@Test
	void launchingCompletesTheRunningSemesterAndMovesItsPeopleWithoutRetraining() throws Exception {
		AppUser student = account(UserRole.MENTEE, "Ayşe Yılmaz", UserAccountStatus.APPROVED, fall);
		AppUser pending = account(UserRole.MENTEE, "Can Er", UserAccountStatus.PENDING, fall);
		AppUser deleted = account(UserRole.MENTEE, "Silinmiş", UserAccountStatus.DELETED, fall);
		AppUser mentor = account(UserRole.MENTEE, "Deniz Ak", UserAccountStatus.APPROVED, fall);
		AppUser oldMentor = account(UserRole.MENTEE, "Ece Su", UserAccountStatus.APPROVED, null);
		gate(mentor, fall);
		acceptedRow(oldMentor, fall);
		reviewed(student, fall);

		call(post("/api/coordinator/programs/{id}/launch", spring.getId()), admin).andExpect(status().isOk());

		assertThat(programs.findById(fall.getId()).orElseThrow().getStatus()).isEqualTo(ProgramStatus.COMPLETED);
		assertThat(programs.findById(spring.getId()).orElseThrow().getStatus()).isEqualTo(ProgramStatus.ACTIVE);
		assertThat(appUsers.findById(student.getId()).orElseThrow().getProgramId()).isEqualTo(spring.getId());
		assertThat(appUsers.findById(pending.getId()).orElseThrow().getProgramId()).isEqualTo(spring.getId());
		assertThat(appUsers.findById(deleted.getId()).orElseThrow().getProgramId()).isEqualTo(fall.getId());
		assertThat(badges.findFirstByUserIdAndBadgeCodeAndProgramIdAndStatus(mentor.getId(), BadgeCode.PEER_MENTOR,
				spring.getId(), BadgeStatus.ACTIVE)).isPresent();
		assertThat(badges.findFirstByUserIdAndBadgeCodeAndProgramIdAndStatus(mentor.getId(), BadgeCode.PEER_MENTOR,
				fall.getId(), BadgeStatus.ACTIVE)).as("the old award stays as history").isPresent();
		assertThat(programMentors.findByProgramIdAndMentorId(spring.getId(), mentor.getId()).orElseThrow().getStatus())
				.isEqualTo(ProgramMentorStatus.ACCEPTED);
		assertThat(programMentors.findByProgramIdAndMentorId(spring.getId(), oldMentor.getId()).orElseThrow()
				.getStatus()).isEqualTo(ProgramMentorStatus.ACCEPTED);
		assertThat(programMentors.findByProgramIdAndMentorId(fall.getId(), oldMentor.getId())).isPresent();
		assertThat(prismReviews.findByProgramIdAndUserId(spring.getId(), student.getId()).orElseThrow().getDecision())
				.isEqualTo(PrismDecision.APPROVED);
	}

	@Test
	void theOldSetupCarriesOverWhereTheDraftHasNoneSoCarriedMentorsStayFindable() throws Exception {
		AppUser mentor = account(UserRole.MENTEE, "Deniz Ak", UserAccountStatus.APPROVED, fall);
		gate(mentor, fall);
		MentorProfile profile = MentorProfile.blankFor(mentor, 0);
		profile.setSpecialties(new ArrayList<>(List.of("CAREER_INTERNSHIP", "Teknik ve Dijital Beceriler")));
		mentorProfiles.saveAndFlush(profile);
		peerCategories.saveAndFlush(new ProgramPeerCategory(fall.getId(), PeerTopicCategory.CAREER_INTERNSHIP,
				coordinator.getId(), Instant.now()));
		peerCategories.saveAndFlush(new ProgramPeerCategory(fall.getId(), PeerTopicCategory.TECHNICAL_DIGITAL,
				coordinator.getId(), Instant.now()));
		TrainingModule module = trainingModule(fall, 2);
		question(module, 2, 1);
		question(module, 2, 2);
		question(module, 1, 1);
		PrismTrainingLink link = new PrismTrainingLink();
		link.setProgramId(fall.getId());
		link.setUrl("https://prism.example/egitim");
		link.setUpdatedAt(Instant.now());
		prismLinks.saveAndFlush(link);

		call(post("/api/coordinator/programs/{id}/launch", spring.getId()), admin).andExpect(status().isOk());

		Set<PeerTopicCategory> active = peerCategoryService.activeIn(List.of(spring.getId()));
		assertThat(active).containsExactlyInAnyOrder(PeerTopicCategory.CAREER_INTERNSHIP,
				PeerTopicCategory.TECHNICAL_DIGITAL);
		assertThat(profile.getSpecialties().stream().map(PeerTopicCategory::fromText).toList())
				.as("every carried mentor's categories resolve in the new semester").allMatch(active::contains);
		TrainingModule copied = trainingModules.findByProgramId(spring.getId()).orElseThrow();
		assertThat(copied.getVideoId()).isEqualTo(module.getVideoId());
		assertThat(trainingQuestions.findByModuleIdAndVersionOrderByPositionAsc(copied.getId(), 2)).hasSize(2);
		assertThat(prismLinks.findById(spring.getId()).orElseThrow().getUrl())
				.isEqualTo("https://prism.example/egitim");
		assertThat(peerCategories.findByProgramId(fall.getId())).as("the old setup stays").hasSize(2);
	}

	@Test
	void whatTheCoordinatorAlreadySetInTheDraftIsKept() throws Exception {
		peerCategories.saveAndFlush(new ProgramPeerCategory(fall.getId(), PeerTopicCategory.CAREER_INTERNSHIP,
				coordinator.getId(), Instant.now()));
		peerCategories.saveAndFlush(new ProgramPeerCategory(spring.getId(), PeerTopicCategory.CAMPUS_LIFE,
				coordinator.getId(), Instant.now()));
		TrainingModule own = trainingModule(spring, 1);
		trainingModule(fall, 3);

		call(post("/api/coordinator/programs/{id}/launch", spring.getId()), admin).andExpect(status().isOk());

		assertThat(peerCategoryService.activeIn(List.of(spring.getId())))
				.containsExactly(PeerTopicCategory.CAMPUS_LIFE);
		assertThat(trainingModules.findByProgramId(spring.getId()).orElseThrow().getId()).isEqualTo(own.getId());
	}

	@Test
	void theFirstSemesterLaunchesWithNothingToMove() throws Exception {
		fall.setStatus(ProgramStatus.COMPLETED);
		programs.saveAndFlush(fall);

		call(get("/api/coordinator/programs/{id}/launch-preview", spring.getId()), admin)
				.andExpect(jsonPath("$.previous").isEmpty())
				.andExpect(jsonPath("$.students").value(0));
		call(post("/api/coordinator/programs/{id}/launch", spring.getId()), admin).andExpect(status().isOk());

		assertThat(programs.findById(spring.getId()).orElseThrow().getStatus()).isEqualTo(ProgramStatus.ACTIVE);
	}

	@Test
	void twoRunningSemestersAreSortedOutBeforeALaunchNotGuessed() throws Exception {
		semester("2026 Yaz", ProgramStatus.ACTIVE);
		AppUser student = account(UserRole.MENTEE, "Ayşe Yılmaz", UserAccountStatus.APPROVED, fall);

		call(post("/api/coordinator/programs/{id}/launch", spring.getId()), admin)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SEVERAL_RUNNING_SEMESTERS"));

		assertThat(programs.findById(spring.getId()).orElseThrow().getStatus()).isEqualTo(ProgramStatus.DRAFT);
		assertThat(programs.findById(fall.getId()).orElseThrow().getStatus()).isEqualTo(ProgramStatus.ACTIVE);
		assertThat(appUsers.findById(student.getId()).orElseThrow().getProgramId()).isEqualTo(fall.getId());
	}

	@Test
	void onlyTheAdminLaunches() throws Exception {
		AppUser student = account(UserRole.MENTEE, "Ayşe Yılmaz", UserAccountStatus.APPROVED, fall);

		call(post("/api/coordinator/programs/{id}/launch", spring.getId()), coordinator)
				.andExpect(status().isForbidden());

		assertThat(programs.findById(spring.getId()).orElseThrow().getStatus()).isEqualTo(ProgramStatus.DRAFT);
		assertThat(appUsers.findById(student.getId()).orElseThrow().getProgramId()).isEqualTo(fall.getId());
	}

	private ResultActions call(MockHttpServletRequestBuilder builder, AppUser actor) throws Exception {
		return mockMvc.perform(builder.param("actorId", actor.getId().toString())
				.with(user("frontend").roles("SERVICE")));
	}

	private AppUser account(UserRole role, String name, UserAccountStatus status, Program program) {
		AppUser account = new AppUser();
		account.setFullName(name);
		account.setEmail(UUID.randomUUID() + "@semester-launch.test");
		account.setRole(role);
		account.setStatus(status);
		account.setProgramId(program == null ? null : program.getId());
		return appUsers.saveAndFlush(account);
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

	private void coordinatorOf(Program coordinated) {
		ProgramCoordinator link = new ProgramCoordinator();
		link.setId(UUID.randomUUID());
		link.setProgramId(coordinated.getId());
		link.setUserId(coordinator.getId());
		link.setAddedBy(admin.getId());
		link.setCreatedAt(Instant.now());
		programCoordinators.saveAndFlush(link);
	}

	private void gate(AppUser holder, Program program) {
		UserBadge award = new UserBadge();
		award.setId(UUID.randomUUID());
		award.setUserId(holder.getId());
		award.setBadgeCode(BadgeCode.PEER_MENTOR);
		award.setProgramId(program.getId());
		award.setStatus(BadgeStatus.ACTIVE);
		award.setSource(BadgeSource.COORDINATOR);
		award.setAwardedAt(Instant.now());
		award.setAwardedBy(coordinator.getId());
		badges.saveAndFlush(award);
		acceptedRow(holder, program);
	}

	private void acceptedRow(AppUser mentor, Program program) {
		ProgramMentor row = new ProgramMentor();
		row.setId(UUID.randomUUID());
		row.setProgramId(program.getId());
		row.setMentorId(mentor.getId());
		row.setStatus(ProgramMentorStatus.ACCEPTED);
		row.setInvitedBy(ProgramMentorInvitedBy.COORDINATOR);
		row.setCreatedAt(Instant.now());
		programMentors.saveAndFlush(row);
	}

	private TrainingModule trainingModule(Program program, int version) {
		TrainingModule module = new TrainingModule();
		module.setId(UUID.randomUUID());
		module.setProgramId(program.getId());
		module.setCurrentVersion(version);
		module.setVideoId("vid" + version);
		module.setPublishedAt(Instant.now());
		module.setUpdatedAt(Instant.now());
		return trainingModules.saveAndFlush(module);
	}

	private void question(TrainingModule module, int version, int position) {
		TrainingQuestion question = new TrainingQuestion();
		question.setId(UUID.randomUUID());
		question.setModuleId(module.getId());
		question.setVersion(version);
		question.setPosition(position);
		question.setText("Soru " + position);
		question.setOption1("A");
		question.setOption2("B");
		question.setOption3("C");
		question.setOption4("D");
		question.setCorrectOption((short) 1);
		trainingQuestions.saveAndFlush(question);
	}

	private void reviewed(AppUser student, Program program) {
		PrismTrainingReview review = new PrismTrainingReview();
		review.setId(UUID.randomUUID());
		review.setProgramId(program.getId());
		review.setUserId(student.getId());
		review.setCompletedAt(Instant.now());
		review.setDecision(PrismDecision.APPROVED);
		review.setDecidedAt(Instant.now());
		review.setDecidedBy(coordinator.getId());
		prismReviews.saveAndFlush(review);
	}
}
