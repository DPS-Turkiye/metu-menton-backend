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

/** An intern's "Okudum" on an important announcement. */
@Entity
@Table(name = "iskur_message_receipt", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_message_receipt", columnNames = {
		"message_id", "user_id" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurMessageReceipt {

	@Id
	private UUID id;

	@Column(name = "message_id", nullable = false)
	private UUID messageId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "read_at", nullable = false)
	private Instant readAt;

	public IskurMessageReceipt(UUID messageId, UUID userId, Instant now) {
		this.id = UUID.randomUUID();
		this.messageId = messageId;
		this.userId = userId;
		this.readAt = now;
	}
}
