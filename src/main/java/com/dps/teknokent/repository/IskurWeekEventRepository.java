package com.dps.teknokent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurWeekEvent;

public interface IskurWeekEventRepository extends JpaRepository<IskurWeekEvent, UUID> {

	List<IskurWeekEvent> findByWeekIdOrderByStepAsc(UUID weekId);

	int countByWeekId(UUID weekId);
}
