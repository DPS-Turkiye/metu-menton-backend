package com.dps.teknokent.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.domain.OfficeHoursRequestStatus;
import com.dps.teknokent.entity.OfficeHoursRequest;

import jakarta.persistence.LockModeType;

public interface OfficeHoursRequestRepository extends JpaRepository<OfficeHoursRequest, UUID> {

	List<OfficeHoursRequest> findByCourseIdAndStudentIdOrderByCreatedAtDesc(UUID courseId, UUID studentId);

	/** The latest requests sent to the academic, from any course or from Menton. */
	List<OfficeHoursRequest> findTop200ByInstructorIdOrderByCreatedAtDesc(UUID instructorId);

	/** The request, locked until the transaction ends, so two answers to it cannot both pass the status check. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select request from OfficeHoursRequest request where request.id = :id")
	Optional<OfficeHoursRequest> findForUpdate(@Param("id") UUID id);

	/** The latest requests the student sent, from any course or from Menton. */
	List<OfficeHoursRequest> findTop200ByStudentIdOrderByCreatedAtDesc(UUID studentId);

	List<OfficeHoursRequest> findByCourseIdAndInstructorIdOrderByCreatedAtDesc(UUID courseId, UUID instructorId);

	List<OfficeHoursRequest> findByInstructorIdAndStatusIn(UUID instructorId,
			Collection<OfficeHoursRequestStatus> statuses);

	List<OfficeHoursRequest> findByStatusIn(Collection<OfficeHoursRequestStatus> statuses);

	/** Expires the request only if nobody moved it on since it was read, so a late answer wins over the job. */
	@Modifying
	@Query("update OfficeHoursRequest r set r.status = com.dps.teknokent.domain.OfficeHoursRequestStatus.EXPIRED,"
			+ " r.updatedAt = :at where r.id = :id and r.status = :status")
	int expireIfStill(@Param("id") UUID id, @Param("status") OfficeHoursRequestStatus status, @Param("at") Instant at);
}
