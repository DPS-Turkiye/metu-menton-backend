package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.UUID;

import com.dps.teknokent.domain.IskurChannelKind;

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
 * One İŞKUR channel. Which unit, team or pair it belongs to depends on its kind; names come from the unit and the team,
 * so a rename shows at once.
 */
@Entity
@Table(name = "iskur_channel", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_channel_unit_kind", columnNames = {
		"unit_id", "kind" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurChannel {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private IskurChannelKind kind;

	@Column(name = "unit_id")
	private UUID unitId;

	@Column(name = "team_id", unique = true)
	private UUID teamId;

	@Column(name = "leader_id")
	private UUID leaderId;

	@Column(name = "member_id")
	private UUID memberId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_message_at")
	private Instant lastMessageAt;

	public IskurChannel(UUID programId, IskurChannelKind kind, Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.kind = kind;
		this.createdAt = now;
	}
}
