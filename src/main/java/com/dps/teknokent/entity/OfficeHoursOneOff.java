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

/** Office hours on one date only, next to the weekly ranges. */
@Entity
@Table(name = "office_hours_one_off")
@Getter
@Setter
@NoArgsConstructor
public class OfficeHoursOneOff {

	@Id
	private UUID id;

	@Column(name = "instructor_id", nullable = false)
	private UUID instructorId;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Column(length = 200)
	private String place;
}
