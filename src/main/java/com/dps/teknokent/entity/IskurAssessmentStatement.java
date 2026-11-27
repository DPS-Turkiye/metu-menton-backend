package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.UUID;

import com.dps.teknokent.domain.IskurAssessmentReason;
import com.dps.teknokent.domain.IskurAssessmentStatus;
import com.dps.teknokent.domain.IskurMentoringDecision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An intern's statement on the peer mentor assessment and the coordinator's decision on it. {@code mentoringMinutes}
 * is the weekly mentoring time the decision left them with (null when it was kept or the week has no day count).
 */
@Entity
@Table(name = "iskur_assessment_statement")
@Getter
@Setter
@NoArgsConstructor
public class IskurAssessmentStatement {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false, updatable = false)
	private UUID programId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16, updatable = false)
	private IskurAssessmentReason reason;

	@Column(nullable = false, length = 1000, updatable = false)
	private String text;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private IskurAssessmentStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "mentoring_decision", length = 8)
	private IskurMentoringDecision mentoringDecision;

	@Column(name = "mentoring_minutes")
	private Integer mentoringMinutes;

	@Column(name = "retake_opened", nullable = false)
	private boolean retakeOpened;

	@Column(name = "decision_note", length = 500)
	private String decisionNote;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public IskurAssessmentStatement(UUID programId, UUID userId, IskurAssessmentReason reason, String text,
			Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.userId = userId;
		this.reason = reason;
		this.text = text;
		this.status = IskurAssessmentStatus.PENDING;
		this.createdAt = now;
	}
}
