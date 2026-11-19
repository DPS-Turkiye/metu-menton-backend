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

/** A unit (birim) of an İŞKUR peer-mentoring program; it holds thematic teams and has one unit coordinator. */
@Entity
@Table(name = "iskur_unit", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_unit_name", columnNames = {
		"program_id", "name" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurUnit {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "coordinator_id")
	private UUID coordinatorId;

	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public IskurUnit(UUID programId, String name, int sortOrder, UUID createdBy, Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.name = name;
		this.sortOrder = sortOrder;
		this.createdBy = createdBy;
		this.createdAt = now;
	}
}
