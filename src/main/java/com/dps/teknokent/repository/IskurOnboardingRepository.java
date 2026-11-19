package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.IskurOnboarding;

import jakarta.persistence.LockModeType;

public interface IskurOnboardingRepository extends JpaRepository<IskurOnboarding, UUID> {

	Optional<IskurOnboarding> findByProgramIdAndUserId(UUID programId, UUID userId);

	/** The form locked until the transaction ends, so a draft save cannot write over a submit it raced. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from IskurOnboarding o where o.programId = :programId and o.userId = :userId")
	Optional<IskurOnboarding> findForUpdate(@Param("programId") UUID programId, @Param("userId") UUID userId);

	List<IskurOnboarding> findByProgramId(UUID programId);

	List<IskurOnboarding> findByProgramIdAndUserIdIn(UUID programId, Collection<UUID> userIds);
}
