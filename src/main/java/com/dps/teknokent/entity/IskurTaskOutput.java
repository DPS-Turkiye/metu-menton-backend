package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A file handed in as a team task's output: which stored document belongs to which task, and who uploaded it. */
@Entity
@Table(name = "iskur_task_output")
@Getter
@NoArgsConstructor
public class IskurTaskOutput {

	@Id
	private UUID id;

	@Column(name = "task_id", nullable = false, updatable = false)
	private UUID taskId;

	@Column(name = "document_id", nullable = false, updatable = false, unique = true)
	private UUID documentId;

	@Column(name = "uploaded_by", nullable = false, updatable = false)
	private UUID uploadedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public IskurTaskOutput(UUID taskId, UUID documentId, UUID uploadedBy, Instant now) {
		this.id = UUID.randomUUID();
		this.taskId = taskId;
		this.documentId = documentId;
		this.uploadedBy = uploadedBy;
		this.createdAt = now;
	}
}
