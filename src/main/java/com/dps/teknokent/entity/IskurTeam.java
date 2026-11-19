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

/** A thematic team inside an İŞKUR unit. Its name is unique in the program, since its channel is named after it. */
@Entity
@Table(name = "iskur_team", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_team_name", columnNames = {
		"program_id", "name" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurTeam {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Column(name = "unit_id", nullable = false)
	private UUID unitId;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public IskurTeam(IskurUnit unit, String name, int sortOrder, Instant now) {
		this.id = UUID.randomUUID();
		this.programId = unit.getProgramId();
		this.unitId = unit.getId();
		this.name = name;
		this.sortOrder = sortOrder;
		this.createdAt = now;
	}
}
