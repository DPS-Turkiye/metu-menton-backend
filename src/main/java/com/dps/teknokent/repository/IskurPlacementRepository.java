package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.IskurPlacement;

import jakarta.persistence.LockModeType;

public interface IskurPlacementRepository extends JpaRepository<IskurPlacement, UUID> {

	List<IskurPlacement> findByProgramId(UUID programId);

	Optional<IskurPlacement> findByProgramIdAndUserId(UUID programId, UUID userId);

	List<IskurPlacement> findByTeamId(UUID teamId);

	boolean existsByTeamIdIn(Collection<UUID> teamIds);

	/**
	 * The person's placement, locked until the transaction ends: their one contribution project is checked under it.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from IskurPlacement p where p.programId = :programId and p.userId = :userId")
	Optional<IskurPlacement> findForUpdate(@Param("programId") UUID programId, @Param("userId") UUID userId);

	long countByProgramId(UUID programId);
}
