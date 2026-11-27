package com.dps.teknokent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.entity.PrismTrainingReview;
import com.dps.teknokent.entity.TrainingAttempt;
import com.dps.teknokent.entity.TrainingModule;
import com.dps.teknokent.entity.TrainingProgress;
import com.dps.teknokent.repository.PrismTrainingReviewRepository;
import com.dps.teknokent.repository.TrainingAttemptRepository;
import com.dps.teknokent.repository.TrainingModuleRepository;
import com.dps.teknokent.repository.TrainingProgressRepository;

import lombok.RequiredArgsConstructor;

/**
 * A student's training follows them from one semester to the next, so nobody retrains: at a launch for everyone who
 * moves, and when an admin moves one student. The old semester keeps its rows.
 */
@Service
@RequiredArgsConstructor
public class TrainingCarryOver {

	private final PrismTrainingReviewRepository prismReviews;
	private final TrainingModuleRepository trainingModules;
	private final TrainingProgressRepository trainingProgress;
	private final TrainingAttemptRepository trainingAttempts;

	/**
	 * One student moved by an admin: their Prism review, and their in-app progress when both semesters run the same
	 * training (the same video and version, as after a launch copied it).
	 */
	@Transactional
	public void forStudent(UUID from, UUID to, UUID userId) {
		reviews(from, to, Set.of(userId));
		TrainingModule source = trainingModules.findByProgramId(from).orElse(null);
		TrainingModule target = trainingModules.findByProgramId(to).orElse(null);
		if (source != null && target != null && source.getCurrentVersion() == target.getCurrentVersion()
				&& source.getVideoId().equals(target.getVideoId())) {
			progress(source.getId(), source.getCurrentVersion(), target.getId(), Set.of(userId));
		}
	}

	/**
	 * The same training in another semester's module: a watched video and the quiz tries so far still count, for
	 * {@code users} (everyone when null). Someone who already started the training there keeps what they have.
	 */
	@Transactional
	public void progress(UUID fromModule, int version, UUID toModule, @Nullable Set<UUID> users) {
		Set<UUID> present = presentIn(toModule, version);
		trainingProgress.saveAll(trainingProgress.findByModuleId(fromModule).stream()
				.filter(progress -> progress.getVersion() == version)
				.filter(progress -> users == null || users.contains(progress.getUserId()))
				.filter(progress -> !present.contains(progress.getUserId()))
				.map(progress -> {
					TrainingProgress copy = new TrainingProgress();
					copy.setId(UUID.randomUUID());
					copy.setModuleId(toModule);
					copy.setUserId(progress.getUserId());
					copy.setVersion(progress.getVersion());
					copy.setVideoCompletedAt(progress.getVideoCompletedAt());
					return copy;
				}).toList());
		trainingAttempts.saveAll(trainingAttempts.findByModuleId(fromModule).stream()
				.filter(attempt -> attempt.getVersion() == version)
				.filter(attempt -> users == null || users.contains(attempt.getUserId()))
				.filter(attempt -> !present.contains(attempt.getUserId()))
				.map(attempt -> {
					TrainingAttempt copy = new TrainingAttempt();
					copy.setId(UUID.randomUUID());
					copy.setModuleId(toModule);
					copy.setUserId(attempt.getUserId());
					copy.setVersion(attempt.getVersion());
					copy.setScore(attempt.getScore());
					copy.setPassed(attempt.isPassed());
					copy.setAnswers(attempt.getAnswers());
					copy.setSubmittedAt(attempt.getSubmittedAt());
					return copy;
				}).toList());
	}

	private Set<UUID> presentIn(UUID module, int version) {
		return trainingProgress.findByModuleId(module).stream()
				.filter(progress -> progress.getVersion() == version)
				.map(TrainingProgress::getUserId)
				.collect(Collectors.toSet());
	}

	/** A mover's training reviewed in the old semester counts in the new one; a review already there is kept. */
	@Transactional
	public int reviews(UUID from, UUID to, Set<UUID> movers) {
		List<PrismTrainingReview> old = prismReviews.findByProgramId(from).stream()
				.filter(review -> movers.contains(review.getUserId()))
				.toList();
		if (old.isEmpty()) {
			return 0;
		}
		Set<UUID> present = Set.copyOf(prismReviews.findByProgramIdAndUserIdIn(to,
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
			copy.setProgramId(to);
			copy.setUserId(review.getUserId());
			copy.setCompletedAt(review.getCompletedAt());
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
