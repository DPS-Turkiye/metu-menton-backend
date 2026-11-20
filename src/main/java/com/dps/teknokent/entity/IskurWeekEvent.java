package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import com.dps.teknokent.domain.IskurWeekEventKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One step in a week's history, never changed once written: a send, a decision with its reason and checks, or an
 * objection. The week itself keeps only where it stands now.
 */
@Entity
@Table(name = "iskur_week_event", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_week_event_step", columnNames = {
		"week_id", "step" }))
@Getter
@NoArgsConstructor
public class IskurWeekEvent implements Persistable<UUID> {

	/** Assigned ids: without this, Spring Data would merge (a SELECT first) instead of persisting a new row. */
	@Transient
	private boolean fresh = true;

	@Id
	private UUID id;

	@Column(name = "week_id", nullable = false, updatable = false)
	private UUID weekId;

	/** The order within the week, from 0. */
	@Column(nullable = false, updatable = false)
	private int step;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 16)
	private IskurWeekEventKind kind;

	@Column(name = "actor_id")
	private UUID actorId;

	@Column(updatable = false, length = 500)
	private String note;

	@Column(name = "check_time_matches_work", updatable = false)
	private Boolean checkTimeMatchesWork;

	@Column(name = "check_effort_documented", updatable = false)
	private Boolean checkEffortDocumented;

	@Column(name = "check_outputs_sufficient", updatable = false)
	private Boolean checkOutputsSufficient;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public IskurWeekEvent(UUID weekId, int step, IskurWeekEventKind kind, UUID actorId, String note,
			Instant now) {
		this.id = UUID.randomUUID();
		this.weekId = weekId;
		this.step = step;
		this.kind = kind;
		this.actorId = actorId;
		this.note = note;
		this.createdAt = now;
	}

	/** The three boxes the reviewer ticked with a decision. */
	public IskurWeekEvent withChecks(boolean timeMatchesWork, boolean effortDocumented, boolean outputsSufficient) {
		this.checkTimeMatchesWork = timeMatchesWork;
		this.checkEffortDocumented = effortDocumented;
		this.checkOutputsSufficient = outputsSufficient;
		return this;
	}

	@Override
	public boolean isNew() {
		return fresh;
	}

	@PostLoad
	@PostPersist
	void markStored() {
		this.fresh = false;
	}
}
