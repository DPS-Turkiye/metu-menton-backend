package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurPlacement;

public interface IskurPlacementRepository extends JpaRepository<IskurPlacement, UUID> {

	List<IskurPlacement> findByProgramId(UUID programId);

	Optional<IskurPlacement> findByProgramIdAndUserId(UUID programId, UUID userId);

	List<IskurPlacement> findByTeamId(UUID teamId);

	boolean existsByTeamIdIn(Collection<UUID> teamIds);
}
