package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.UUID;

import com.dps.teknokent.domain.IskurTeamSupportStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One team asking another for help, optionally for one of its tasks, and the other team's answer. */
@Entity
@Table(name = "iskur_team_support_request")
@Getter
@Setter
@NoArgsConstructor
public class IskurTeamSupportRequest {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false, updatable = false)
	private UUID programId;

	@Column(name = "from_team_id", nullable = false, updatable = false)
	private UUID fromTeamId;

	@Column(name = "to_team_id", nullable = false, updatable = false)
	private UUID toTeamId;

	/** The task the help is for; null for a general request or once the task is deleted. */
	@Column(name = "task_id")
	private UUID taskId;

	/** The leader who opened it; null once their account is erased. */
	@Column(name = "opened_by")
	private UUID openedBy;

	@Column(nullable = false, length = 500)
	private String need;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private IskurTeamSupportStatus status;

	@Column(name = "answer_note", length = 500)
	private String answerNote;

	@Column(name = "answered_by")
	private UUID answeredBy;

	@Column(name = "answered_at")
	private Instant answeredAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public IskurTeamSupportRequest(UUID programId, UUID fromTeamId, UUID toTeamId, UUID openedBy, String need,
			Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.fromTeamId = fromTeamId;
		this.toTeamId = toTeamId;
		this.openedBy = openedBy;
		this.need = need;
		this.status = IskurTeamSupportStatus.OPEN;
		this.createdAt = now;
	}
}
