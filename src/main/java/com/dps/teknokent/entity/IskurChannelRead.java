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

/** How far one reader has read one channel. */
@Entity
@Table(name = "iskur_channel_read", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_channel_read", columnNames = {
		"channel_id", "user_id" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurChannelRead {

	@Id
	private UUID id;

	@Column(name = "channel_id", nullable = false)
	private UUID channelId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "last_read_at", nullable = false)
	private Instant lastReadAt;

	public IskurChannelRead(UUID channelId, UUID userId, Instant now) {
		this.id = UUID.randomUUID();
		this.channelId = channelId;
		this.userId = userId;
		this.lastReadAt = now;
	}
}
