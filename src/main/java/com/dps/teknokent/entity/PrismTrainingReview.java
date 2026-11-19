package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.UUID;

import com.dps.teknokent.domain.PrismDecision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A student's Prism training in one program: when they said they finished it, and the coordinator's latest decision
 * with the score the Prism admin reported (staff only).
 */
@Entity
@Table(name = "prism_training_review", uniqueConstraints = @UniqueConstraint(name = "uq_prism_training_review", columnNames = {
		"program_id", "user_id" }))
@Getter
@Setter
@NoArgsConstructor
public class PrismTrainingReview {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private PrismDecision decision;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(length = 32)
	private String score;
}
