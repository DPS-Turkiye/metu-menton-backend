package com.dps.teknokent.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.OfficeHoursMeeting;

public interface OfficeHoursMeetingRepository extends JpaRepository<OfficeHoursMeeting, UUID> {

	List<OfficeHoursMeeting> findByInstructorIdAndScheduledAtAfter(UUID instructorId, Instant after);

	List<OfficeHoursMeeting> findByRequestIdIn(Collection<UUID> requestIds);

	Optional<OfficeHoursMeeting> findByRequestId(UUID requestId);

	/** Every meeting the person is in, as the student or as the academic, from {@code from} on. */
	@Query("""
			select meeting from OfficeHoursMeeting meeting
			where (meeting.studentId = :userId or meeting.instructorId = :userId) and meeting.scheduledAt >= :from
			order by meeting.scheduledAt
			""")
	List<OfficeHoursMeeting> findForPersonFrom(@Param("userId") UUID userId, @Param("from") Instant from);
}
