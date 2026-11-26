package com.dps.teknokent.entity;

import java.io.Serializable;
import java.util.UUID;

import com.dps.teknokent.domain.ProgramFeature;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One feature of a semester that one of its coordinators runs (V139); keyed by person, the creator included. */
@Entity
@Table(name = "program_coordinator_feature")
@IdClass(ProgramCoordinatorFeature.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProgramCoordinatorFeature {

	@Id
	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Id
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Id
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ProgramFeature feature;

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class Key implements Serializable {

		private static final long serialVersionUID = 1L;

		private UUID programId;
		private UUID userId;
		private ProgramFeature feature;
	}
}
