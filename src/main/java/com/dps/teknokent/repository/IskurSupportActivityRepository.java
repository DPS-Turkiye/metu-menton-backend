package com.dps.teknokent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurSupportActivity;

public interface IskurSupportActivityRepository extends JpaRepository<IskurSupportActivity, UUID> {

	List<IskurSupportActivity> findByProgramIdOrderBySortOrderAscCreatedAtAsc(UUID programId);
}
