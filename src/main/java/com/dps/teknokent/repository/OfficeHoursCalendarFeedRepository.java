package com.dps.teknokent.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.OfficeHoursCalendarFeed;

public interface OfficeHoursCalendarFeedRepository extends JpaRepository<OfficeHoursCalendarFeed, UUID> {

	Optional<OfficeHoursCalendarFeed> findByToken(String token);

	Optional<OfficeHoursCalendarFeed> findByUserId(UUID userId);

	Optional<OfficeHoursCalendarFeed> findByCourseId(UUID courseId);

	/**
	 * Makes the person's feed unless they already have one, so two first opens at once (two tabs) share one link
	 * instead of the second failing on the unique column. PostgreSQL only (ON CONFLICT), like the other
	 * office-hours inserts.
	 */
	@Modifying
	@Query(value = """
			INSERT INTO office_hours_calendar_feed (id, user_id, token, created_at)
			VALUES (:id, :userId, :token, :now)
			ON CONFLICT (user_id) DO NOTHING
			""", nativeQuery = true)
	int insertForUserIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId, @Param("token") String token,
			@Param("now") Instant now);

	/** The course's version of {@link #insertForUserIfAbsent}. */
	@Modifying
	@Query(value = """
			INSERT INTO office_hours_calendar_feed (id, course_id, token, created_at)
			VALUES (:id, :courseId, :token, :now)
			ON CONFLICT (course_id) DO NOTHING
			""", nativeQuery = true)
	int insertForCourseIfAbsent(@Param("id") UUID id, @Param("courseId") UUID courseId, @Param("token") String token,
			@Param("now") Instant now);
}
