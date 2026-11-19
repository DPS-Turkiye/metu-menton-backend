package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.UUID;

import com.dps.teknokent.domain.IskurPlacementStatus;
import com.dps.teknokent.domain.IskurTeamRole;

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
 * Where one intern works in an İŞKUR program: their team, whether they lead it, whose sub-group they are in, and how
 * their week splits between peer mentoring and team work.
 */
@Entity
@Table(name = "iskur_placement", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_placement_user", columnNames = {
		"program_id", "user_id" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurPlacement {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "team_id", nullable = false)
	private UUID teamId;

	@Enumerated(EnumType.STRING)
	@Column(name = "team_role", nullable = false, length = 16)
	private IskurTeamRole teamRole;

	@Column(name = "leader_id")
	private UUID leaderId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private IskurPlacementStatus status;

	@Column(name = "weekly_days")
	private Short weeklyDays;

	@Column(name = "mentoring_minutes")
	private Integer mentoringMinutes;

	@Column(name = "placed_by")
	private UUID placedBy;

	@Column(name = "placed_at", nullable = false)
	private Instant placedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "finalized_by")
	private UUID finalizedBy;

	@Column(name = "finalized_at")
	private Instant finalizedAt;

	public IskurPlacement(UUID programId, UUID userId, UUID placedBy, Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.userId = userId;
		this.status = IskurPlacementStatus.PROVISIONAL;
		this.placedBy = placedBy;
		this.placedAt = now;
		this.updatedAt = now;
	}

	public void finalizeBy(UUID actorId, Instant now) {
		this.status = IskurPlacementStatus.FINAL;
		this.finalizedBy = actorId;
		this.finalizedAt = now;
		this.updatedAt = now;
	}
}
