package com.dps.teknokent.entity;

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

/** A kind of channel one person gets no notifications for in one program. */
@Entity
@Table(name = "iskur_channel_mute", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_channel_mute", columnNames = {
		"program_id", "user_id", "kind" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurChannelMute {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private IskurChannelKind kind;

	public IskurChannelMute(UUID programId, UUID userId, IskurChannelKind kind) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.userId = userId;
		this.kind = kind;
	}
}
