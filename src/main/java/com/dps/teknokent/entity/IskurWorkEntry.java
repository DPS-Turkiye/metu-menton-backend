package com.dps.teknokent.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import com.dps.teknokent.domain.IskurWorkEntryKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One line of a weekly log: team work (optionally on a task) or a support activity, with its own title. */
@Entity
@Table(name = "iskur_work_entry")
@Getter
@Setter
@NoArgsConstructor
public class IskurWorkEntry implements Persistable<UUID> {

	/** Assigned ids: without this, Spring Data would merge (one SELECT per row) instead of persisting a new row. */
	@Transient
	private boolean fresh = true;

	@Id
	private UUID id;

	@Column(name = "week_id", nullable = false, updatable = false)
	private UUID weekId;

	@Column(name = "entry_date", nullable = false)
	private LocalDate entryDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private IskurWorkEntryKind kind;

	@Column(name = "task_id")
	private UUID taskId;

	@Column(name = "support_activity_id")
	private UUID supportActivityId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false)
	private int minutes;

	@Column(length = 500)
	private String output;

	@Column(length = 500)
	private String note;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public IskurWorkEntry(UUID weekId, IskurWorkEntryKind kind, int sortOrder, Instant now) {
		this.id = UUID.randomUUID();
		this.weekId = weekId;
		this.kind = kind;
		this.sortOrder = sortOrder;
		this.createdAt = now;
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
