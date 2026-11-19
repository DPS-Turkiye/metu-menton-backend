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

/**
 * One address on a program's İŞKUR intern list: the account with this e-mail, once it is in the program, is an intern.
 * The address is stored trimmed and lowercased.
 */
@Entity
@Table(name = "iskur_intern_email", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_intern_email", columnNames = {
		"program_id", "email" }))
@Getter
@NoArgsConstructor
public class IskurInternEmail {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "added_by")
	private UUID addedBy;

	@Column(name = "added_at", nullable = false)
	private Instant addedAt;

	public IskurInternEmail(UUID programId, String email, UUID addedBy, Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.email = email;
		this.addedBy = addedBy;
		this.addedAt = now;
	}
}
