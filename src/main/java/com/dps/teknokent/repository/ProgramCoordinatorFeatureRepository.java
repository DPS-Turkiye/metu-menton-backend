package com.dps.teknokent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.ProgramCoordinatorFeature;

public interface ProgramCoordinatorFeatureRepository
		extends JpaRepository<ProgramCoordinatorFeature, ProgramCoordinatorFeature.Key> {

	List<ProgramCoordinatorFeature> findByProgramId(UUID programId);

	List<ProgramCoordinatorFeature> findByProgramIdAndUserId(UUID programId, UUID userId);

	void deleteByProgramIdAndUserId(UUID programId, UUID userId);

}
