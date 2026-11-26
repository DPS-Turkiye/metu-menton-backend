package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.PrismTrainingReview;

public interface PrismTrainingReviewRepository extends JpaRepository<PrismTrainingReview, UUID> {

	Optional<PrismTrainingReview> findByProgramIdAndUserId(UUID programId, UUID userId);

	List<PrismTrainingReview> findByProgramIdAndUserIdIn(UUID programId, Collection<UUID> userIds);

	List<PrismTrainingReview> findByProgramId(UUID programId);
}
