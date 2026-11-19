package com.dps.teknokent.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One of the skills an İŞKUR unit asks its applicants to rate, in the order the form shows them. */
@Entity
@Table(name = "iskur_unit_skill", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_unit_skill", columnNames = {
		"unit_id", "skill_id" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurUnitSkill {

	@Id
	private UUID id;

	@Column(name = "unit_id", nullable = false)
	private UUID unitId;

	@Column(name = "skill_id", nullable = false)
	private UUID skillId;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	public IskurUnitSkill(UUID unitId, UUID skillId, int sortOrder) {
		this.id = UUID.randomUUID();
		this.unitId = unitId;
		this.skillId = skillId;
		this.sortOrder = sortOrder;
	}
}
