package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A peer program's link to its Akran Mentoru training on Prism, ODTÜ's service app. */
@Entity
@Table(name = "prism_training_link")
@Getter
@Setter
@NoArgsConstructor
public class PrismTrainingLink {

	@Id
	@Column(name = "program_id")
	private UUID programId;

	@Column(nullable = false, length = 500)
	private String url;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "updated_by")
	private UUID updatedBy;
}
