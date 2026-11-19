package com.dps.teknokent.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Marks that the coordinators were told an intern had two weeks without a peer meeting, ending with this week. */
@Entity
@Table(name = "iskur_meeting_gap_alert", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_meeting_gap_alert", columnNames = {
		"program_id", "user_id", "week_start" }))
@Getter
@NoArgsConstructor
public class IskurMeetingGapAlert {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false, updatable = false)
	private UUID programId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	/** The Monday of the second week without a meeting. */
	@Column(name = "week_start", nullable = false, updatable = false)
	private LocalDate weekStart;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public IskurMeetingGapAlert(UUID programId, UUID userId, LocalDate weekStart, Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.userId = userId;
		this.weekStart = weekStart;
		this.createdAt = now;
	}
}
