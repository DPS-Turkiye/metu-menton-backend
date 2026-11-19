package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dps.teknokent.config.PeerTrainingProperties;
import com.dps.teknokent.domain.BadgeCode;
import com.dps.teknokent.domain.BadgeSource;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.PermissionSource;
import com.dps.teknokent.domain.PrismDecision;
import com.dps.teknokent.domain.PrismDecisionOutcome;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.TrainingState;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.requests.PrismDecisionRequest;
import com.dps.teknokent.dto.responses.PrismDecisionResultResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.PrismTrainingLink;
import com.dps.teknokent.entity.PrismTrainingReview;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.PrismTrainingLinkRepository;
import com.dps.teknokent.repository.PrismTrainingReviewRepository;

@ExtendWith(MockitoExtension.class)
class PrismTrainingServiceTest {

	private static final String LINK = "https://prism.example.test/akran-mentor";

	@Mock
	private PrismTrainingLinkRepository links;
	@Mock
	private PrismTrainingReviewRepository reviews;
	@Mock
	private AppUserRepository users;
	@Mock
	private BadgeService badgeService;
	@Mock
	private DomainLookupService domainLookupService;
	@Mock
	private NotificationService notificationService;
	@Mock
	private PeerTrainingProperties peerTraining;

	@InjectMocks
	private PrismTrainingService service;

	private final Program iskur = program();
	private final AppUser coordinator = person("koordinator@example.test", UserRole.COORDINATOR);
	private final Map<UUID, PrismTrainingReview> stored = new HashMap<>();
	private Set<UUID> interns = Set.of();

	@BeforeEach
	void setUp() {
		lenient().when(badgeService.requirePeerProgram(coordinator.getId(), iskur.getId())).thenReturn(iskur);
		lenient().when(domainLookupService.requireUser(coordinator.getId())).thenReturn(coordinator);
		lenient().when(badgeService.internRule(iskur)).thenAnswer(call -> (Predicate<UUID>) id -> interns.contains(id));
		lenient().when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
		lenient().when(reviews.findByProgramIdAndUserId(eq(iskur.getId()), any()))
				.thenAnswer(call -> Optional.ofNullable(stored.get(call.<UUID>getArgument(1))));
		lenient().when(reviews.save(any(PrismTrainingReview.class))).thenAnswer(call -> {
			PrismTrainingReview review = call.getArgument(0);
			stored.put(review.getUserId(), review);
			return review;
		});
	}

	// ---- the link ----

	@Test
	void acceptsOnlyHttpsLinksWithAHostAndNoCredentials() {
		assertThat(PrismTrainingService.httpsUrl(" " + LINK + " ")).contains(LINK);
		assertThat(PrismTrainingService.httpsUrl("HTTPS://prism.example.test/x?a=1"))
				.contains("https://prism.example.test/x?a=1");
		for (String bad : List.of("http://prism.example.test", "javascript:alert(1)", "file:///etc/passwd",
				"https://", "https://user:pass@prism.example.test", "https://prism.example.test/a b", "prism",
				"https://" + "a".repeat(500) + ".test", "https://prism.example.test/%%", "")) {
			assertThat(PrismTrainingService.httpsUrl(bad)).as(bad).isEmpty();
		}
		assertThat(PrismTrainingService.httpsUrl(null)).isEmpty();
	}

	@Test
	void theFirstLinkIsSavedAndAnnouncedToTheInternsWhoCanStillBecomeMentors() {
		when(links.findById(iskur.getId())).thenReturn(Optional.empty());
		AppUser intern = person("stajyer@example.test", UserRole.MENTEE);
		AppUser plain = person("ogrenci@example.test", UserRole.MENTEE);
		AppUser mentor = person("mentor@example.test", UserRole.MENTEE);
		mentor.grant(UserRole.MENTOR, PermissionSource.BADGE, Instant.now());
		interns = Set.of(intern.getId(), mentor.getId());
		when(users.findByProgramIdAndPermissionAndStatusOrderByCreatedAtAsc(iskur.getId(), UserRole.MENTEE,
				UserAccountStatus.APPROVED)).thenReturn(List.of(intern, plain, mentor));

		var view = service.saveLink(coordinator.getId(), iskur.getId(), LINK);

		assertThat(view.url()).isEqualTo(LINK);
		assertThat(view.inAppQuiz()).isFalse();
		ArgumentCaptor<PrismTrainingLink> saved = ArgumentCaptor.forClass(PrismTrainingLink.class);
		verify(links).save(saved.capture());
		assertThat(saved.getValue().getProgramId()).isEqualTo(iskur.getId());
		assertThat(saved.getValue().getUpdatedBy()).isEqualTo(coordinator.getId());
		verify(notificationService).create(eq(intern), eq(NotificationCategory.BADGE), anyString(), anyString(),
				eq("/student/peer-mentoring/training"));
		verify(notificationService, never()).create(eq(plain), any(), anyString(), anyString(), anyString());
		verify(notificationService, never()).create(eq(mentor), any(), anyString(), anyString(), anyString());
	}

	@Test
	void changingTheLinkSavesItWithoutAnnouncingAndTheSameLinkChangesNothing() {
		PrismTrainingLink existing = new PrismTrainingLink();
		existing.setProgramId(iskur.getId());
		existing.setUrl(LINK);
		existing.setUpdatedAt(Instant.parse("2026-11-19T10:00:00Z"));
		when(links.findById(iskur.getId())).thenReturn(Optional.of(existing));

		service.saveLink(coordinator.getId(), iskur.getId(), LINK);
		verify(links, never()).save(any());

		var changed = service.saveLink(coordinator.getId(), iskur.getId(), "https://prism.example.test/yeni");
		assertThat(changed.url()).isEqualTo("https://prism.example.test/yeni");
		verify(links).save(existing);
		verify(notificationService, never()).create(any(), any(), anyString(), anyString(), anyString());
	}

	@Test
	void refusesABadLinkAndAnArchivedProgram() {
		assertThatThrownBy(() -> service.saveLink(coordinator.getId(), iskur.getId(), "http://prism.example.test"))
				.isInstanceOf(InvalidRequestException.class);
		iskur.setStatus(ProgramStatus.COMPLETED);
		assertThatThrownBy(() -> service.saveLink(coordinator.getId(), iskur.getId(), LINK))
				.isInstanceOf(InvalidRequestException.class);
		verify(links, never()).save(any());
	}

	@Test
	void theEditorSeesNoLinkBeforeTheFirstSaveAndWhetherTheQuizRuns() {
		when(links.findById(iskur.getId())).thenReturn(Optional.empty());
		when(peerTraining.inAppQuiz()).thenReturn(true);

		var view = service.link(coordinator.getId(), iskur.getId());

		assertThat(view.url()).isNull();
		assertThat(view.inAppQuiz()).isTrue();
	}

	@Test
	void theEditorSeesTheSavedLink() {
		PrismTrainingLink existing = new PrismTrainingLink();
		existing.setUrl(LINK);
		existing.setUpdatedAt(Instant.parse("2026-11-19T10:00:00Z"));
		when(links.findById(iskur.getId())).thenReturn(Optional.of(existing));

		var view = service.link(coordinator.getId(), iskur.getId());

		assertThat(view.url()).isEqualTo(LINK);
		assertThat(view.updatedAt()).isEqualTo(existing.getUpdatedAt());
	}

	// ---- the student's state ----

	@Test
	void aStudentMovesFromToDoToWaitingAndBackToRetakeAfterADecision() {
		PrismTrainingReview review = new PrismTrainingReview();
		assertThat(PrismTrainingService.stateOf(null)).isEqualTo(TrainingState.PRISM_TODO);
		assertThat(PrismTrainingService.stateOf(review)).isEqualTo(TrainingState.PRISM_TODO);

		review.setCompletedAt(Instant.parse("2026-11-20T08:00:00Z"));
		assertThat(PrismTrainingService.stateOf(review)).isEqualTo(TrainingState.PRISM_PENDING);

		review.setDecision(PrismDecision.RETAKE);
		review.setDecidedAt(Instant.parse("2026-11-20T09:00:00Z"));
		assertThat(PrismTrainingService.stateOf(review)).isEqualTo(TrainingState.PRISM_RETAKE);

		review.setCompletedAt(Instant.parse("2026-11-20T10:00:00Z"));
		assertThat(PrismTrainingService.stateOf(review)).isEqualTo(TrainingState.PRISM_PENDING);
	}

	@Test
	void theStudentSeesTheLinkAndANonInternSeesOnlyThatMentoringIsForInterns() {
		AppUser intern = person("stajyer@example.test", UserRole.MENTEE);
		AppUser plain = person("ogrenci@example.test", UserRole.MENTEE);
		interns = Set.of(intern.getId());
		PrismTrainingLink link = new PrismTrainingLink();
		link.setUrl(LINK);
		when(links.findById(iskur.getId())).thenReturn(Optional.of(link));

		var forIntern = service.forStudent(intern, iskur);
		assertThat(forIntern.state()).isEqualTo(TrainingState.PRISM_TODO);
		assertThat(forIntern.url()).isEqualTo(LINK);

		var forPlain = service.forStudent(plain, iskur);
		assertThat(forPlain.state()).isEqualTo(TrainingState.INTERNS_ONLY);
		assertThat(forPlain.url()).isNull();
	}

	@Test
	void beforeTheLinkIsSetThereIsNothingToDo() {
		AppUser intern = person("stajyer@example.test", UserRole.MENTEE);
		interns = Set.of(intern.getId());
		when(links.findById(iskur.getId())).thenReturn(Optional.empty());

		assertThat(service.forStudent(intern, iskur).state()).isEqualTo(TrainingState.NOT_AVAILABLE);
	}

	@Test
	void sayingTheTrainingIsFinishedRecordsWhen() {
		AppUser intern = person("stajyer@example.test", UserRole.MENTEE);

		service.markCompleted(intern, iskur);

		assertThat(stored.get(intern.getId()).getCompletedAt()).isNotNull();
		assertThat(stored.get(intern.getId()).getProgramId()).isEqualTo(iskur.getId());
	}

	// ---- the coordinator's decisions ----

	private AppUser member(String email) {
		AppUser person = person(email, UserRole.MENTEE);
		lenient().when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(person));
		lenient().when(badgeService.belongsTo(person, iskur)).thenReturn(true);
		return person;
	}

	private List<PrismDecisionResultResponse> decide(PrismDecision decision, PrismDecisionRequest.Entry... entries) {
		return service.decide(coordinator.getId(), iskur.getId(), decision, List.of(entries));
	}

	@Test
	void approvingAwardsTheBadgeAsTheCoordinatorAndKeepsTheScoreForStaff() {
		AppUser intern = member("stajyer@example.test");
		interns = Set.of(intern.getId());

		var results = decide(PrismDecision.APPROVED, new PrismDecisionRequest.Entry(" Stajyer@Example.test ", " 86 "));

		assertThat(results).singleElement().satisfies(result -> {
			assertThat(result.email()).isEqualTo("stajyer@example.test");
			assertThat(result.outcome()).isEqualTo(PrismDecisionOutcome.APPROVED);
			assertThat(result.fullName()).isEqualTo(intern.getFullName());
		});
		verify(badgeService).award(intern, BadgeCode.PEER_MENTOR, iskur, BadgeSource.COORDINATOR,
				coordinator.getId());
		PrismTrainingReview review = stored.get(intern.getId());
		assertThat(review.getDecision()).isEqualTo(PrismDecision.APPROVED);
		assertThat(review.getDecidedBy()).isEqualTo(coordinator.getId());
		assertThat(review.getScore()).isEqualTo("86");
	}

	@Test
	void anAdminApprovesAsAdmin() {
		AppUser admin = person("yonetici@example.test", UserRole.ADMIN);
		when(badgeService.requirePeerProgram(admin.getId(), iskur.getId())).thenReturn(iskur);
		when(domainLookupService.requireUser(admin.getId())).thenReturn(admin);
		AppUser intern = member("stajyer@example.test");
		interns = Set.of(intern.getId());

		service.decide(admin.getId(), iskur.getId(), PrismDecision.APPROVED,
				List.of(new PrismDecisionRequest.Entry("stajyer@example.test", null)));

		verify(badgeService).award(intern, BadgeCode.PEER_MENTOR, iskur, BadgeSource.ADMIN, admin.getId());
	}

	@Test
	void retakeRecordsTheDecisionAndTellsTheStudentInTheApp() {
		AppUser intern = member("stajyer@example.test");
		interns = Set.of(intern.getId());

		var results = decide(PrismDecision.RETAKE, new PrismDecisionRequest.Entry("stajyer@example.test", "41"));

		assertThat(results).singleElement()
				.satisfies(result -> assertThat(result.outcome()).isEqualTo(PrismDecisionOutcome.RETAKE));
		verify(badgeService, never()).award(any(), any(), any(), any(), any());
		verify(notificationService).create(eq(intern), eq(NotificationCategory.BADGE),
				eq("Testi tekrar alman gerekiyor"), anyString(), eq("/student/peer-mentoring/training"));
		assertThat(PrismTrainingService.stateOf(stored.get(intern.getId()))).isEqualTo(TrainingState.PRISM_RETAKE);
		assertThat(stored.get(intern.getId()).getScore()).isEqualTo("41");
	}

	@Test
	void someoneWhoAlreadyHoldsTheBadgeIsLeftAsTheyAreWhateverTheDecision() {
		AppUser mentor = member("mentor@example.test");
		interns = Set.of(mentor.getId());
		when(badgeService.hasActiveGate(mentor.getId(), iskur.getId())).thenReturn(true);

		for (PrismDecision decision : PrismDecision.values()) {
			assertThat(decide(decision, new PrismDecisionRequest.Entry("mentor@example.test", "10"))).singleElement()
					.satisfies(result -> assertThat(result.outcome()).isEqualTo(PrismDecisionOutcome.ALREADY_MENTOR));
		}
		verify(badgeService, never()).award(any(), any(), any(), any(), any());
		verify(reviews, never()).save(any());
		verify(notificationService, never()).create(any(), any(), anyString(), anyString(), anyString());
	}

	@Test
	void someoneWhoseBadgeWasTakenBackIsNotReapprovedFromAList() {
		AppUser revoked = member("geri@example.test");
		interns = Set.of(revoked.getId());
		when(badgeService.hasRevokedGate(revoked.getId(), iskur.getId())).thenReturn(true);

		assertThat(decide(PrismDecision.APPROVED, new PrismDecisionRequest.Entry("geri@example.test", "90")))
				.singleElement()
				.satisfies(result -> assertThat(result.outcome()).isEqualTo(PrismDecisionOutcome.NOT_ELIGIBLE));
		verify(badgeService, never()).award(any(), any(), any(), any(), any());
		verify(reviews, never()).save(any());
	}

	@Test
	void aSecondRetakeIsNotToldTwiceAndAnAddressWithoutAScoreKeepsTheOneBefore() {
		AppUser intern = member("stajyer@example.test");
		interns = Set.of(intern.getId());

		decide(PrismDecision.RETAKE, new PrismDecisionRequest.Entry("stajyer@example.test", "41"));
		decide(PrismDecision.RETAKE, new PrismDecisionRequest.Entry("stajyer@example.test", null));

		verify(notificationService, org.mockito.Mockito.times(1)).create(eq(intern), any(), anyString(), anyString(),
				anyString());
		assertThat(stored.get(intern.getId()).getScore()).isEqualTo("41");

		service.markCompleted(intern, iskur);
		stored.get(intern.getId()).setCompletedAt(Instant.now().plusSeconds(60));
		decide(PrismDecision.RETAKE, new PrismDecisionRequest.Entry("stajyer@example.test", "55"));
		verify(notificationService, org.mockito.Mockito.times(2)).create(eq(intern), any(), anyString(), anyString(),
				anyString());
		assertThat(stored.get(intern.getId()).getScore()).isEqualTo("55");
	}

	@Test
	void anAddressTheProgramDoesNotHaveIsNotFoundEvenWithAnAccountElsewhere() {
		AppUser elsewhere = person("baska@example.test", UserRole.MENTEE);
		when(users.findByEmailIgnoreCase("baska@example.test")).thenReturn(Optional.of(elsewhere));
		when(badgeService.belongsTo(elsewhere, iskur)).thenReturn(false);

		var results = decide(PrismDecision.APPROVED, new PrismDecisionRequest.Entry("baska@example.test", null),
				new PrismDecisionRequest.Entry("yok@example.test", null),
				new PrismDecisionRequest.Entry("adres-degil", null));

		assertThat(results).extracting(PrismDecisionResultResponse::outcome)
				.containsExactly(PrismDecisionOutcome.NOT_FOUND, PrismDecisionOutcome.NOT_FOUND,
						PrismDecisionOutcome.NOT_FOUND);
		assertThat(results).extracting(PrismDecisionResultResponse::fullName).containsOnlyNulls();
		verify(users, never()).findByEmailIgnoreCase("adres-degil");
		verify(badgeService, never()).award(any(), any(), any(), any(), any());
	}

	@Test
	void aNonInternAnUnapprovedAccountAndTheCoordinatorThemselfAreNotEligible() {
		AppUser plain = member("ogrenci@example.test");
		AppUser pending = member("bekleyen@example.test");
		pending.setStatus(UserAccountStatus.PENDING);
		when(users.findByEmailIgnoreCase("koordinator@example.test")).thenReturn(Optional.of(coordinator));
		when(badgeService.belongsTo(coordinator, iskur)).thenReturn(true);
		interns = Set.of(pending.getId(), coordinator.getId());

		var results = decide(PrismDecision.APPROVED, new PrismDecisionRequest.Entry("ogrenci@example.test", null),
				new PrismDecisionRequest.Entry("bekleyen@example.test", null),
				new PrismDecisionRequest.Entry("koordinator@example.test", null));

		assertThat(results).extracting(PrismDecisionResultResponse::outcome)
				.containsOnly(PrismDecisionOutcome.NOT_ELIGIBLE);
		assertThat(results).extracting(PrismDecisionResultResponse::fullName)
				.containsExactly(plain.getFullName(), pending.getFullName(), coordinator.getFullName());
		verify(badgeService, never()).award(any(), any(), any(), any(), any());
		verify(reviews, never()).save(any());
	}

	@Test
	void aRepeatedAddressCountsOnceWithItsFirstScore() {
		AppUser intern = member("stajyer@example.test");
		interns = Set.of(intern.getId());

		var results = decide(PrismDecision.RETAKE, new PrismDecisionRequest.Entry("stajyer@example.test", "40"),
				new PrismDecisionRequest.Entry("STAJYER@example.test", "90"));

		assertThat(results).hasSize(1);
		assertThat(stored.get(intern.getId()).getScore()).isEqualTo("40");
	}

	@Test
	void onlyThePeerProgramsCoordinatorsAndAdminsDecide() {
		UUID stranger = UUID.randomUUID();
		when(badgeService.requirePeerProgram(stranger, iskur.getId())).thenThrow(new InvalidRequestException("no"));

		assertThatThrownBy(() -> service.decide(stranger, iskur.getId(), PrismDecision.APPROVED,
				List.of(new PrismDecisionRequest.Entry("stajyer@example.test", null))))
				.isInstanceOf(InvalidRequestException.class);
		verify(reviews, never()).save(any());
	}

	private static AppUser person(String email, UserRole role) {
		AppUser person = new AppUser();
		person.setId(UUID.randomUUID());
		person.setFullName("Kişi " + email);
		person.setEmail(email);
		person.setRole(role);
		person.setStatus(UserAccountStatus.APPROVED);
		return person;
	}

	private static Program program() {
		Program program = new Program();
		program.setId(UUID.randomUUID());
		program.setName("İŞKUR 2026 Bahar");
		program.setProgramType(ProgramType.PEER_MENTORING);
		program.setStatus(ProgramStatus.ACTIVE);
		return program;
	}
}
