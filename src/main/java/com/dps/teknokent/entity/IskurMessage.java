package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A message in an İŞKUR channel. A title and the important flag belong to Genel Duyurular posts. */
@Entity
@Table(name = "iskur_message")
@Getter
@Setter
@NoArgsConstructor
public class IskurMessage {

	@Id
	private UUID id;

	@Column(name = "channel_id", nullable = false)
	private UUID channelId;

	@Column(name = "sender_id")
	private UUID senderId;

	@Column(length = 160)
	private String title;

	@Column(nullable = false, length = 4000)
	private String body;

	@Column(nullable = false)
	private boolean important;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public IskurMessage(UUID channelId, UUID senderId, String title, String body, boolean important, Instant now) {
		this.id = UUID.randomUUID();
		this.channelId = channelId;
		this.senderId = senderId;
		this.title = title;
		this.body = body;
		this.important = important;
		this.createdAt = now;
	}
}
