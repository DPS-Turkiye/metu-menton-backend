package com.dps.teknokent.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

	/** Requests made in a window, newest first: the university-wide list a coordinator reads. */
	List<OfficeHoursRequest> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(Instant from,
			Instant to);

	List<OfficeHoursRequest> findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
			OfficeHoursRequestStatus status, Instant from, Instant to);

	long countByStatusInAndCreatedAtGreaterThanEqual(Collection<OfficeHoursRequestStatus> statuses, Instant from);

	/**
	 * Requests not booked yet whose shown time is from {@code fromSlot} up to (not including) {@code toSlot}: the
	 * first proposed time of an alternative, the first asked-for time otherwise. Times are stored as
	 * "yyyy-MM-ddTHH:mm" in Turkey time, which sorts as text, so the first 16 characters are the first time.
	 */
	@Query("""
			select request from OfficeHoursRequest request
			where request.status <> com.dps.teknokent.domain.OfficeHoursRequestStatus.ACCEPTED
			  and (case when request.status = com.dps.teknokent.domain.OfficeHoursRequestStatus.COUNTER_PROPOSED
			              and request.proposedSlots is not null and request.proposedSlots <> ''
			            then substring(request.proposedSlots, 1, 16)
			            else substring(request.preferredSlots, 1, 16) end) >= :fromSlot
			  and (case when request.status = com.dps.teknokent.domain.OfficeHoursRequestStatus.COUNTER_PROPOSED
			              and request.proposedSlots is not null and request.proposedSlots <> ''
			            then substring(request.proposedSlots, 1, 16)
			            else substring(request.preferredSlots, 1, 16) end) < :toSlot""")
	List<OfficeHoursRequest> findUnbookedShowingBetween(@Param("fromSlot") String fromSlot,
			@Param("toSlot") String toSlot);

	long countByStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(OfficeHoursRequestStatus status, Instant from,
			Instant to);
}
