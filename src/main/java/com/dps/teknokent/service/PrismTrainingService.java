package com.dps.teknokent.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.config.PeerTrainingProperties;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.PrismDecision;
import com.dps.teknokent.domain.PrismDecisionOutcome;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.TrainingState;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.requests.PrismDecisionRequest;
import com.dps.teknokent.dto.responses.PrismDecisionResultResponse;
import com.dps.teknokent.dto.responses.PrismLinkResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.PrismTrainingLink;
import com.dps.teknokent.entity.PrismTrainingReview;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.PrismTrainingLinkRepository;
import com.dps.teknokent.repository.PrismTrainingReviewRepository;
import com.dps.teknokent.util.EmailDomains;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;

/**
 * ODTÜ's way to the Akran Mentoru badge (weekend launch §C): the training video and test run on Prism, ODTÜ's service
 * app; the Prism admin sends the people and their scores outside Menton; a coordinator (or an admin) of the program
 * then approves each one, which awards the badge through {@link BadgeService#award}, or asks them to take the test
 * again. The program's link to Prism is set in the training editor.
 */
@Service
@RequiredArgsConstructor
public class PrismTrainingService {

	static final String TRAINING_PAGE = "/student/peer-mentoring/training";
	static final int URL_MAX = 500;

	private final PrismTrainingLinkRepository links;
	private final PrismTrainingReviewRepository reviews;
	private final AppUserRepository users;
	private final BadgeService badgeService;
	private final DomainLookupService domainLookupService;
	private final NotificationService notificationService;
	private final PeerTrainingProperties peerTraining;

	/** The training editor's Prism link; empty before the first save. */
	@Transactional(readOnly = true)
	public PrismLinkResponse link(UUID actorId, UUID programId) {
		Program program = badgeService.requirePeerProgram(actorId, programId);
		return links.findById(program.getId())
				.map(this::view)
				.orElseGet(() -> new PrismLinkResponse(null, null, peerTraining.inAppQuiz()));
	}

	/** Saves the program's Prism link; the first one tells the students who can become its mentors. */
	@Transactional
	public PrismLinkResponse saveLink(UUID actorId, UUID programId, String rawUrl) {
		Program program = badgeService.requirePeerProgram(actorId, programId);
		if (program.getStatus() == ProgramStatus.COMPLETED) {
			throw new InvalidRequestException(Texts.forRequest("error.trainingProgramArchived"));
		}
		String url = httpsUrl(rawUrl)
				.orElseThrow(() -> new InvalidRequestException(Texts.forRequest("error.trainingPrismLink")));
		Optional<PrismTrainingLink> existing = links.findById(program.getId());
		if (existing.isPresent() && existing.get().getUrl().equals(url)) {
			return view(existing.get());
		}
		PrismTrainingLink link = existing.orElseGet(() -> {
			PrismTrainingLink created = new PrismTrainingLink();
			created.setProgramId(program.getId());
			return created;
		});
		link.setUrl(url);
		link.setUpdatedAt(Instant.now());
		link.setUpdatedBy(actorId);
		links.save(link);
		if (existing.isEmpty() && !peerTraining.inAppQuiz()) {
			announce(program);
		}
		return view(link);
	}

	/**
	 * Where a student of the peer program stands with its Prism training, once the badge states are ruled out: a
	 * student who cannot become a mentor there sees why, and before the link is set there is nothing to do.
	 */
	StudentPrism forStudent(AppUser student, Program program) {
		if (!badgeService.internRule(program).test(student.getId())) {
			return new StudentPrism(TrainingState.INTERNS_ONLY, null, null);
		}
		Optional<PrismTrainingLink> link = links.findById(program.getId());
		if (link.isEmpty()) {
			return new StudentPrism(TrainingState.NOT_AVAILABLE, null, null);
		}
		@Nullable
		PrismTrainingReview review = reviews.findByProgramIdAndUserId(program.getId(), student.getId()).orElse(null);
		return new StudentPrism(stateOf(review), link.get().getUrl(), review == null ? null : review.getCompletedAt());
	}

	/** "Prism eğitimini tamamladım": the coordinator sees the student waiting for a decision. */
	void markCompleted(AppUser student, Program program) {
		PrismTrainingReview review = reviewOf(program.getId(), student.getId());
		review.setCompletedAt(Instant.now());
		reviews.save(review);
	}

	/** A student's state from their review: saying they finished after the last decision means waiting for one. */
	static TrainingState stateOf(@Nullable PrismTrainingReview review) {
		if (review == null) {
			return TrainingState.PRISM_TODO;
		}
		Instant completed = review.getCompletedAt();
		Instant decided = review.getDecidedAt();
		if (completed != null && (decided == null || completed.isAfter(decided))) {
			return TrainingState.PRISM_PENDING;
		}
		return review.getDecision() == PrismDecision.RETAKE ? TrainingState.PRISM_RETAKE : TrainingState.PRISM_TODO;
	}

	/**
	 * Approve or "take the test again" for every address on the list, each on its own: an address the program does
	 * not have is NOT_FOUND (whether or not it has an account elsewhere); someone already holding the badge is
	 * ALREADY_MENTOR and left as they are; an unapproved account, the coordinator themself, someone the interns-only
	 * rule leaves out, or someone whose badge here was taken back is NOT_ELIGIBLE (giving that back stays a deliberate
	 * "Rozet ver", not a line in a pasted list). A repeated address counts once. An address without a score keeps the
	 * score recorded before.
	 */
	@Transactional
	public List<PrismDecisionResultResponse> decide(UUID actorId, UUID programId, PrismDecision decision,
			List<PrismDecisionRequest.Entry> entries) {
		Program program = badgeService.requirePeerProgram(actorId, programId);
		AppUser actor = domainLookupService.requireUser(actorId);
		Predicate<UUID> interns = badgeService.internRule(program);
		Map<String, Optional<String>> scores = new LinkedHashMap<>();
		for (PrismDecisionRequest.Entry entry : entries) {
			scores.putIfAbsent(EmailDomains.normalizeEmail(entry.email()),
					Optional.ofNullable(TextUtils.normalizeOptional(entry.score())));
		}
		List<PrismDecisionResultResponse> results = new ArrayList<>(scores.size());
		scores.forEach((email, score) -> results
				.add(decideOne(actor, program, interns, decision, email, score.orElse(null))));
		return results;
	}

	private PrismDecisionResultResponse decideOne(AppUser actor, Program program, Predicate<UUID> interns,
			PrismDecision decision, String email, @Nullable String score) {
		Optional<AppUser> found = EmailDomains.isEmail(email)
				? users.findByEmailIgnoreCase(email).filter(person -> badgeService.belongsTo(person, program))
				: Optional.empty();
		if (found.isEmpty()) {
			return new PrismDecisionResultResponse(email, PrismDecisionOutcome.NOT_FOUND, null);
		}
		AppUser person = found.get();
		if (badgeService.hasActiveGate(person.getId(), program.getId())) {
			return result(email, PrismDecisionOutcome.ALREADY_MENTOR, person);
		}
		if (person.getId().equals(actor.getId()) || person.getStatus() != UserAccountStatus.APPROVED
				|| !interns.test(person.getId()) || badgeService.hasRevokedGate(person.getId(), program.getId())) {
			return result(email, PrismDecisionOutcome.NOT_ELIGIBLE, person);
		}
		PrismTrainingReview review = reviewOf(program.getId(), person.getId());
		// Asked to retake already, and not back since: the decision stands and they are not told twice.
		boolean alreadyToldToRetake = decision == PrismDecision.RETAKE
				&& stateOf(review) == TrainingState.PRISM_RETAKE;
		review.setDecision(decision);
		review.setDecidedAt(Instant.now());
		review.setDecidedBy(actor.getId());
		if (score != null) {
			review.setScore(score);
		}
		reviews.save(review);
		if (decision == PrismDecision.APPROVED) {
			badgeService.award(person, BadgeService.GATE, program, BadgeService.sourceOf(actor), actor.getId());
			return result(email, PrismDecisionOutcome.APPROVED, person);
		}
		if (!alreadyToldToRetake) {
			notificationService.create(person, NotificationCategory.BADGE,
					Texts.get(person, "notification.prismRetake.title"),
					Texts.get(person, "notification.prismRetake.body", program.getName()), TRAINING_PAGE);
		}
		return result(email, PrismDecisionOutcome.RETAKE, person);
	}

	private static PrismDecisionResultResponse result(String email, PrismDecisionOutcome outcome, AppUser person) {
		return new PrismDecisionResultResponse(email, outcome, person.getFullName());
	}

	private PrismTrainingReview reviewOf(UUID programId, UUID userId) {
		return reviews.findByProgramIdAndUserId(programId, userId).orElseGet(() -> {
			PrismTrainingReview created = new PrismTrainingReview();
			created.setId(UUID.randomUUID());
			created.setProgramId(programId);
			created.setUserId(userId);
			return created;
		});
	}

	/**
	 * The link as stored: https only, with a host and no credentials or spaces, the scheme lower-cased. Students open
	 * it from Menton, so nothing that could run script or point at a local file gets through.
	 */
	static Optional<String> httpsUrl(@Nullable String raw) {
		String trimmed = raw == null ? "" : raw.trim();
		if (trimmed.isEmpty() || trimmed.length() > URL_MAX || trimmed.chars().anyMatch(Character::isWhitespace)) {
			return Optional.empty();
		}
		try {
			URI uri = new URI(trimmed);
			String scheme = uri.getScheme();
			String host = uri.getHost();
			if (scheme == null || !"https".equals(scheme.toLowerCase(Locale.ROOT)) || host == null || host.isBlank()
					|| uri.getRawUserInfo() != null) {
				return Optional.empty();
			}
			return Optional.of("https" + trimmed.substring(scheme.length()));
		} catch (URISyntaxException invalid) {
			return Optional.empty();
		}
	}

	private PrismLinkResponse view(PrismTrainingLink link) {
		return new PrismLinkResponse(link.getUrl(), link.getUpdatedAt(), peerTraining.inAppQuiz());
	}

	private void announce(Program program) {
		Predicate<UUID> interns = badgeService.internRule(program);
		for (AppUser student : users.findByProgramIdAndPermissionAndStatusOrderByCreatedAtAsc(program.getId(),
				UserRole.MENTEE, UserAccountStatus.APPROVED)) {
			if (!student.has(UserRole.MENTOR) && interns.test(student.getId())) {
				notificationService.create(student, NotificationCategory.BADGE,
						Texts.get(student, "notification.prismOpened.title"),
						Texts.get(student, "notification.prismOpened.body", program.getName()), TRAINING_PAGE);
			}
		}
	}

	/** A student's Prism state, the link to open and when they said they finished. */
	record StudentPrism(TrainingState state, @Nullable String url, @Nullable Instant completedAt) {
	}
}
