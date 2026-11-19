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

/**
 * One activity of a program's support pool (rules frame 322:3413, "Görüşme olmayan hafta"): what an intern can do for
 * peer mentoring in a week without a meeting. Kept by the program's coordinators.
 */
@Entity
@Table(name = "iskur_support_activity", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_support_activity_title", columnNames = {
		"program_id", "title" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurSupportActivity {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false, updatable = false)
	private UUID programId;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(length = 500)
	private String description;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public IskurSupportActivity(UUID programId, String title, int sortOrder, UUID createdBy, Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.title = title;
		this.active = true;
		this.sortOrder = sortOrder;
		this.createdBy = createdBy;
		this.createdAt = now;
		this.updatedAt = now;
	}
}
