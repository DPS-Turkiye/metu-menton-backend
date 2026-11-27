package com.dps.teknokent.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.domain.IskurAssessmentStatus;
import com.dps.teknokent.entity.IskurAssessmentStatement;

public interface IskurAssessmentStatementRepository extends JpaRepository<IskurAssessmentStatement, UUID> {

	Optional<IskurAssessmentStatement> findFirstByProgramIdAndUserIdOrderByCreatedAtDesc(UUID programId, UUID userId);

	Optional<IskurAssessmentStatement> findByProgramIdAndUserIdAndStatus(UUID programId, UUID userId,
			IskurAssessmentStatus status);

	List<IskurAssessmentStatement> findByProgramIdOrderByCreatedAtDesc(UUID programId);
}
