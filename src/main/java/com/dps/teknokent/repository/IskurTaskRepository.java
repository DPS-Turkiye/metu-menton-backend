package com.dps.teknokent.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.domain.IskurTaskKind;
import com.dps.teknokent.domain.IskurTaskStatus;
import com.dps.teknokent.entity.IskurTask;

import jakarta.persistence.LockModeType;

public interface IskurTaskRepository extends JpaRepository<IskurTask, UUID> {

	/** The task, locked until the transaction ends: its files are counted and changed one request at a time. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from IskurTask t where t.id = :id")
	Optional<IskurTask> findForUpdate(@Param("id") UUID id);

	/** The person's open tasks of one kind ("öğrenci başına tek katkı projesi"). */
	List<IskurTask> findByProgramIdAndAssigneeIdAndKindAndStatus(UUID programId, UUID assigneeId, IskurTaskKind kind,
			IskurTaskStatus status);

	List<IskurTask> findByTeamIdAndDueDateBetweenOrderByDueDateAscCreatedAtAsc(UUID teamId, LocalDate from,
			LocalDate to);

	List<IskurTask> findByAssigneeIdAndTeamIdAndDueDateBetweenOrderByDueDateAscCreatedAtAsc(UUID assigneeId,
			UUID teamId, LocalDate from, LocalDate to);
}
