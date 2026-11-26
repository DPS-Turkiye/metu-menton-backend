package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An intern in a channel, kept in step with their placement. {@code leftAt} is set while they are out of it. */
@Entity
@Table(name = "iskur_channel_member", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_channel_member", columnNames = {
		"channel_id", "user_id" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurChannelMember {

	@Id
	private UUID id;

	@Column(name = "channel_id", nullable = false)
	private UUID channelId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "can_write", nullable = false)
	private boolean canWrite;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	@Column(name = "left_at")
	private Instant leftAt;

	public IskurChannelMember(UUID channelId, UUID userId, boolean canWrite, Instant now) {
		this.id = UUID.randomUUID();
		this.channelId = channelId;
		this.userId = userId;
		this.canWrite = canWrite;
		this.joinedAt = now;
	}

	public boolean isActive() {
		return leftAt == null;
	}
}
