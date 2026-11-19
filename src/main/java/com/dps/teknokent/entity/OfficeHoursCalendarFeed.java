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

/**
 * A secret calendar link: either one person's feed ({@code userId}, their meetings) or one course's feed
 * ({@code courseId}, its academics' office hours). The token is kept as it is, not hashed, so the same link can be
 * shown again to everyone in the course; whoever can read this table can read the meetings themselves anyway.
 */
@Entity
@Table(name = "office_hours_calendar_feed")
@Getter
@Setter
@NoArgsConstructor
public class OfficeHoursCalendarFeed {

	@Id
	private UUID id;

	@Column(name = "user_id", unique = true)
	private UUID userId;

	@Column(name = "course_id", unique = true)
	private UUID courseId;

	@Column(nullable = false, unique = true, length = 64)
	private String token;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
}
