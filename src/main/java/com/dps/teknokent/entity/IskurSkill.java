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

/** A skill in an İŞKUR program's catalogue; units ask about it through {@link IskurUnitSkill}. */
@Entity
@Table(name = "iskur_skill", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_skill_name", columnNames = {
		"program_id", "name" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurSkill {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public IskurSkill(UUID programId, String name, Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.name = name;
		this.createdAt = now;
	}
}
