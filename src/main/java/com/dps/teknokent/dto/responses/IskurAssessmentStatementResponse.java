package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurAssessmentReason;
import com.dps.teknokent.domain.IskurAssessmentStatus;
import com.dps.teknokent.domain.IskurMentoringDecision;
import com.dps.teknokent.domain.TrainingState;

/**
 * One statement. The placement fields ({@code teamName}, {@code weeklyDays}, {@code currentMentoringMinutes},
 * {@code mentoringIsDefault}) and {@code trainingState} are where the intern stands now, for the coordinator's
 * decision; {@code trainingState} is filled only while the statement waits. {@code peerMentor}: they have the badge
 * by now. The intern's own copy leaves the coordinator-only fields null.
 */
public record IskurAssessmentStatementResponse(
		UUID id,
		UUID userId,
		String fullName,
		@Nullable String teamName,
		@Nullable Integer weeklyDays,
		@Nullable Integer currentMentoringMinutes,
		boolean mentoringIsDefault,
		@Nullable TrainingState trainingState,
		boolean peerMentor,
		IskurAssessmentReason reason,
		String text,
		IskurAssessmentStatus status,
		@Nullable IskurMentoringDecision mentoringDecision,
		@Nullable Integer mentoringMinutes,
		boolean retakeOpened,
		@Nullable String decisionNote,
		@Nullable String decidedByName,
		Instant createdAt,
		@Nullable Instant decidedAt) {
}
