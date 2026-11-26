package com.dps.teknokent.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.dto.responses.CoordinatorProgramResponse;
import com.dps.teknokent.dto.responses.LaunchPreviewResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.PrismTrainingLink;
import com.dps.teknokent.entity.PrismTrainingReview;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramPeerCategory;
import com.dps.teknokent.entity.TrainingModule;
import com.dps.teknokent.entity.TrainingQuestion;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.PrismTrainingLinkRepository;
import com.dps.teknokent.repository.PrismTrainingReviewRepository;
import com.dps.teknokent.repository.ProgramPeerCategoryRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.repository.TrainingModuleRepository;
import com.dps.teknokent.repository.TrainingQuestionRepository;
import com.dps.teknokent.security.AdminAccess;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Launching a semester (Hocam, 26 Nov): the running semester is completed and its people move into the new one.
 * Students' {@code program_id} moves, Akran mentors keep their gate badge and an accepted row, and a training already
 * reviewed stays reviewed, so nobody retrains; the old semester's peer categories, training and Prism link carry over
 * where the draft has none. Mentorships, requests and reports stay in the old semester, readable.
 * Office Hours belong to the university and do not move. Other program types launch as before.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemesterLaunchService {

	public static final String SEVERAL_RUNNING = "SEVERAL_RUNNING_SEMESTERS";

	private final CoordinatorProgramService coordinatorProgramService;
	private final ProgramRepository programs;
	private final AppUserRepository users;
	private final BadgeService badges;
	private final PrismTrainingReviewRepository prismReviews;
	private final DomainLookupService domainLookupService;
	private final ProgramPeerCategoryRepository peerCategories;
	private final TrainingModuleRepository trainingModules;
	private final TrainingQuestionRepository trainingQuestions;
	private final PrismTrainingLinkRepository prismLinks;

	@Transactional(readOnly = true)
	public LaunchPreviewResponse preview(UUID actorId, UUID programId) {
		AdminAccess.requireAdmin(domainLookupService.requireUser(actorId));
		Program next = programs.findById(programId)
				.orElseThrow(() -> new ResourceNotFoundException("Program not found: " + programId));
		if (next.getProgramType() != ProgramType.SEMESTER || next.getStatus() != ProgramStatus.DRAFT) {
			throw new InvalidRequestException("Only a semester draft has a launch preview.");
		}
		return runningBefore(next)
				.map(previous -> new LaunchPreviewResponse(
						new LaunchPreviewResponse.ProgramRef(previous.getId(), previous.getName()),
						movingAccounts(previous).size(),
						badges.peerMentorIds(previous.getId()).size()))
				.orElseGet(() -> new LaunchPreviewResponse(null, 0, 0));
	}

	/**
	 * Taslak → Aktif; for a semester, the roll-over runs in the same transaction, so it happens whole or not at all.
	 */
	@Transactional
	public CoordinatorProgramResponse.Detail launch(UUID actorId, UUID programId) {
		AdminAccess.requireAdmin(domainLookupService.requireUser(actorId));
		Program next = programs.findById(programId)
				.orElseThrow(() -> new ResourceNotFoundException("Program not found: " + programId));
		boolean semester = next.getProgramType() == ProgramType.SEMESTER;
		Optional<Program> previous = semester && next.getStatus() == ProgramStatus.DRAFT
				? runningBefore(next)
				: Optional.empty();
		CoordinatorProgramResponse.Detail launched = coordinatorProgramService.launch(actorId, programId);
		if (previous.isEmpty()) {
			return launched;
		}
		rollOver(actorId, previous.get(), next);
		return coordinatorProgramService.detail(actorId, programId);
	}

	private void rollOver(UUID actorId, Program from, Program to) {
		List<AppUser> moving = movingAccounts(from);
		moving.forEach(account -> account.setProgramId(to.getId()));
		users.saveAll(moving);
		int mentors = badges.carryOverPeerMentors(from, to, actorId);
		int reviews = carryOverTrainingReviews(from, to);
		String setup = carryOverSetup(from, to, actorId);
		from.setStatus(ProgramStatus.COMPLETED);
		programs.save(from);
		log.info("Semester {} launched by {}: {} completed; {} accounts, {} peer mentors and {} training reviews moved;"
				+ " carried setup: {}", to.getId(), actorId, from.getId(), moving.size(), mentors, reviews, setup);
	}

	/** The one running semester the draft follows; several running is a state to sort out first, not to guess. */
	private Optional<Program> runningBefore(Program next) {
		List<Program> running = programs.findByProgramType(ProgramType.SEMESTER).stream()
				.filter(program -> program.getStatus() == ProgramStatus.ACTIVE)
				.filter(program -> !program.getId().equals(next.getId()))
				.toList();
		if (running.size() > 1) {
			throw new ConflictException(Texts.forRequest("error.severalRunningSemesters"), SEVERAL_RUNNING);
		}
		return running.stream().findFirst();
	}

	/** Every account of the semester that still exists, pending ones too, so nobody is left in a closed semester. */
	private List<AppUser> movingAccounts(Program from) {
		return users.findByProgramIdAndStatusNot(from.getId(), UserAccountStatus.DELETED);
	}

	/**
	 * What the coordinator set up in the old semester and did not set again in the draft: its peer categories (so
	 * the carried mentor profiles stay findable by students), its training module with the current questions, and its
	 * Prism link. Whatever the draft already has is kept as the coordinator set it.
	 */
	private String carryOverSetup(Program from, Program to, UUID actorId) {
		List<String> carried = new ArrayList<>();
		Instant now = Instant.now();
		if (peerCategories.findByProgramId(to.getId()).isEmpty()) {
			List<ProgramPeerCategory> copies = peerCategories.findByProgramId(from.getId()).stream()
					.map(row -> new ProgramPeerCategory(to.getId(), row.getCategory(), actorId, now))
					.toList();
			peerCategories.saveAll(copies);
			if (!copies.isEmpty()) {
				carried.add("categories");
			}
		}
		Optional<TrainingModule> oldModule = trainingModules.findByProgramId(from.getId());
		if (oldModule.isPresent() && trainingModules.findByProgramId(to.getId()).isEmpty()) {
			TrainingModule source = oldModule.get();
			TrainingModule module = new TrainingModule();
			module.setId(UUID.randomUUID());
			module.setProgramId(to.getId());
			module.setCurrentVersion(source.getCurrentVersion());
			module.setVideoId(source.getVideoId());
			module.setPublishedAt(source.getPublishedAt());
			module.setUpdatedAt(now);
			module.setUpdatedBy(actorId);
			trainingModules.save(module);
			List<TrainingQuestion> questions = trainingQuestions
					.findByModuleIdAndVersionOrderByPositionAsc(source.getId(), source.getCurrentVersion()).stream()
					.map(question -> copyOf(question, module.getId()))
					.toList();
			trainingQuestions.saveAll(questions);
			carried.add("training");
		}
		Optional<PrismTrainingLink> oldLink = prismLinks.findById(from.getId());
		if (oldLink.isPresent() && !prismLinks.existsById(to.getId())) {
			PrismTrainingLink link = new PrismTrainingLink();
			link.setProgramId(to.getId());
			link.setUrl(oldLink.get().getUrl());
			link.setUpdatedAt(now);
			link.setUpdatedBy(actorId);
			prismLinks.save(link);
			carried.add("prism");
		}
		return carried.isEmpty() ? "none" : String.join(", ", carried);
	}

	private static TrainingQuestion copyOf(TrainingQuestion question, UUID moduleId) {
		TrainingQuestion copy = new TrainingQuestion();
		copy.setId(UUID.randomUUID());
		copy.setModuleId(moduleId);
		copy.setVersion(question.getVersion());
		copy.setPosition(question.getPosition());
		copy.setText(question.getText());
		copy.setOption1(question.getOption1());
		copy.setOption2(question.getOption2());
		copy.setOption3(question.getOption3());
		copy.setOption4(question.getOption4());
		copy.setCorrectOption(question.getCorrectOption());
		return copy;
	}

	/** A training reviewed in the old semester counts in the new one; a review already there is kept. */
	private int carryOverTrainingReviews(Program from, Program to) {
		List<PrismTrainingReview> old = prismReviews.findByProgramId(from.getId());
		Set<UUID> present = Set.copyOf(prismReviews.findByProgramIdAndUserIdIn(to.getId(),
				old.stream().map(PrismTrainingReview::getUserId).toList()).stream()
				.map(PrismTrainingReview::getUserId)
				.toList());
		List<PrismTrainingReview> copies = new ArrayList<>();
		for (PrismTrainingReview review : old) {
			if (present.contains(review.getUserId())) {
				continue;
			}
			PrismTrainingReview copy = new PrismTrainingReview();
			copy.setId(UUID.randomUUID());
			copy.setProgramId(to.getId());
			copy.setUserId(review.getUserId());
			copy.setCompletedAt(review.getCompletedAt() == null ? Instant.now() : review.getCompletedAt());
			copy.setDecision(review.getDecision());
			copy.setDecidedAt(review.getDecidedAt());
			copy.setDecidedBy(review.getDecidedBy());
			copy.setScore(review.getScore());
			copies.add(copy);
		}
		prismReviews.saveAll(copies);
		return copies.size();
	}
}
