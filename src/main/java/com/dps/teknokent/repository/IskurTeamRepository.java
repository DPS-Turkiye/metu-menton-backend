package com.dps.teknokent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurTeam;

public interface IskurTeamRepository extends JpaRepository<IskurTeam, UUID> {

	List<IskurTeam> findByProgramIdOrderBySortOrderAscCreatedAtAsc(UUID programId);

	List<IskurTeam> findByUnitId(UUID unitId);
}
