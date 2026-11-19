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

/**
 * One reader's place in one channel: how far they have read it (empty until they first open it) and when they were
 * last notified about it.
 */
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

	@Column(name = "last_read_at")
	private Instant lastReadAt;

	@Column(name = "notified_at")
	private Instant notifiedAt;

	public IskurChannelRead(UUID channelId, UUID userId) {
		this.id = UUID.randomUUID();
		this.channelId = channelId;
		this.userId = userId;
	}

	/** Whether a new message should notify: never notified, or read since the last notification. */
	public boolean readSinceLastNotified() {
		return notifiedAt == null || (lastReadAt != null && !lastReadAt.isBefore(notifiedAt));
	}
}
