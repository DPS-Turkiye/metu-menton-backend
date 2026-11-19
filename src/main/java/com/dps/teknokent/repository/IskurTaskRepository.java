package com.dps.teknokent.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurTask;

public interface IskurTaskRepository extends JpaRepository<IskurTask, UUID> {

	List<IskurTask> findByTeamIdAndDueDateBetweenOrderByDueDateAscCreatedAtAsc(UUID teamId, LocalDate from,
			LocalDate to);

	List<IskurTask> findByAssigneeIdAndTeamIdAndDueDateBetweenOrderByDueDateAscCreatedAtAsc(UUID assigneeId,
			UUID teamId, LocalDate from, LocalDate to);
}
