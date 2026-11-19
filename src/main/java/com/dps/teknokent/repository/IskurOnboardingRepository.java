package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurOnboarding;

public interface IskurOnboardingRepository extends JpaRepository<IskurOnboarding, UUID> {

	Optional<IskurOnboarding> findByProgramIdAndUserId(UUID programId, UUID userId);

	List<IskurOnboarding> findByProgramId(UUID programId);

	List<IskurOnboarding> findByProgramIdAndUserIdIn(UUID programId, Collection<UUID> userIds);
}
