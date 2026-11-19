package com.dps.teknokent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurUnit;

public interface IskurUnitRepository extends JpaRepository<IskurUnit, UUID> {

	List<IskurUnit> findByProgramIdOrderBySortOrderAscCreatedAtAsc(UUID programId);
}
