package com.dps.teknokent.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dps.teknokent.domain.IskurTaskKind;
import com.dps.teknokent.domain.IskurTaskStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A team task a leader gives to someone in their sub-group (2.1 "Görev ata"), with what came of it. */
@Entity
@Table(name = "iskur_task")
@Getter
@Setter
@NoArgsConstructor
public class IskurTask {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false, updatable = false)
	private UUID programId;

	@Column(name = "team_id", nullable = false, updatable = false)
	private UUID teamId;

	@Column(name = "assignee_id", nullable = false, updatable = false)
	private UUID assigneeId;

	@Column(name = "created_by")
	private UUID createdBy;

	@Column(nullable = false, length = 200)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private IskurTaskKind kind;

	@Column(name = "estimated_minutes", nullable = false)
	private int estimatedMinutes;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Column(name = "needs_output", nullable = false)
	private boolean needsOutput;

	@Column(length = 1000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private IskurTaskStatus status;

	@Column(name = "actual_minutes")
	private Integer actualMinutes;

	@Column(name = "output_link", length = 500)
	private String outputLink;

	@Column(name = "output_note", length = 500)
	private String outputNote;

	@Column(name = "handover_reason", length = 500)
	private String handoverReason;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public IskurTask(UUID programId, UUID teamId, UUID assigneeId, UUID createdBy, Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.teamId = teamId;
		this.assigneeId = assigneeId;
		this.createdBy = createdBy;
		this.status = IskurTaskStatus.OPEN;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void complete(Instant now) {
		this.status = IskurTaskStatus.DONE;
		this.handoverReason = null;
		this.completedAt = now;
		this.updatedAt = now;
	}

	public void handOver(String reason, Instant now) {
		this.status = IskurTaskStatus.HANDED_OVER;
		this.handoverReason = reason;
		this.completedAt = null;
		this.updatedAt = now;
	}

	public void reopen(Instant now) {
		this.status = IskurTaskStatus.OPEN;
		this.handoverReason = null;
		this.completedAt = null;
		this.updatedAt = now;
	}
}
