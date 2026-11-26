package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurTeamChangeStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An intern's request to change team, and the coordinator's answer to it. */
@Entity
@Table(name = "iskur_team_change_request")
@Getter
@Setter
@NoArgsConstructor
public class IskurTeamChangeRequest {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false, updatable = false)
	private UUID programId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	/** The team the intern asked to leave; null once that team is deleted. */
	@Column(name = "from_team_id")
	private UUID fromTeamId;

	@Column(nullable = false, length = 500)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private IskurTeamChangeStatus status;

	@Column(name = "decision_note", length = 500)
	private String decisionNote;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public IskurTeamChangeRequest(UUID programId, UUID userId, UUID fromTeamId, String reason, Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.userId = userId;
		this.fromTeamId = fromTeamId;
		this.reason = reason;
		this.status = IskurTeamChangeStatus.PENDING;
		this.createdAt = now;
	}

	/** {@code decider} is null when a move or an unplacing in the workspace closed it. */
	public void close(IskurTeamChangeStatus outcome, @Nullable UUID decider, Instant now) {
		this.status = outcome;
		this.decidedBy = decider;
		this.decidedAt = now;
	}
}
