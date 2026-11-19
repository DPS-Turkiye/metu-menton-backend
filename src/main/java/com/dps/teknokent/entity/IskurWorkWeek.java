package com.dps.teknokent.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dps.teknokent.domain.IskurWeekDecision;
import com.dps.teknokent.domain.IskurWeekStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One intern's weekly log (3.2), sent to their leader, and the leader's decision on it (4.1). */
@Entity
@Table(name = "iskur_work_week", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_work_week", columnNames = {
		"program_id", "user_id", "week_start" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurWorkWeek {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false, updatable = false)
	private UUID programId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	/** The Monday of the week. */
	@Column(name = "week_start", nullable = false, updatable = false)
	private LocalDate weekStart;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private IskurWeekStatus status;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private IskurWeekDecision decision;

	@Column(name = "decision_reason", length = 500)
	private String decisionReason;

	@Column(name = "check_time_matches_work")
	private Boolean checkTimeMatchesWork;

	@Column(name = "check_effort_documented")
	private Boolean checkEffortDocumented;

	@Column(name = "check_outputs_sufficient")
	private Boolean checkOutputsSufficient;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(length = 500)
	private String objection;

	@Column(name = "objected_at")
	private Instant objectedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Boxed so a new week (null) is persisted, not merged: its id is assigned before the first save. */
	@Version
	@Column(nullable = false)
	private Long version;

	public IskurWorkWeek(UUID programId, UUID userId, LocalDate weekStart, Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.userId = userId;
		this.weekStart = weekStart;
		this.status = IskurWeekStatus.DRAFT;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** Sends (or sends again) to the leader; a resend clears the earlier decision and the objection to it. */
	public void submit(Instant now) {
		this.status = IskurWeekStatus.SUBMITTED;
		this.submittedAt = now;
		this.decision = null;
		this.decisionReason = null;
		this.checkTimeMatchesWork = null;
		this.checkEffortDocumented = null;
		this.checkOutputsSufficient = null;
		this.decidedBy = null;
		this.decidedAt = null;
		this.objection = null;
		this.objectedAt = null;
		this.updatedAt = now;
	}

	public void decide(IskurWeekDecision decided, String reason, boolean timeMatchesWork, boolean effortDocumented,
			boolean outputsSufficient, UUID leaderId, Instant now) {
		this.status = decided.status();
		this.decision = decided;
		this.decisionReason = reason;
		this.checkTimeMatchesWork = timeMatchesWork;
		this.checkEffortDocumented = effortDocumented;
		this.checkOutputsSufficient = outputsSufficient;
		this.decidedBy = leaderId;
		this.decidedAt = now;
		this.updatedAt = now;
	}
}
