package com.dps.teknokent.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.domain.IskurTeamChangeStatus;
import com.dps.teknokent.entity.IskurTeamChangeRequest;

public interface IskurTeamChangeRequestRepository extends JpaRepository<IskurTeamChangeRequest, UUID> {

	Optional<IskurTeamChangeRequest> findFirstByProgramIdAndUserIdOrderByCreatedAtDesc(UUID programId, UUID userId);

	Optional<IskurTeamChangeRequest> findByProgramIdAndUserIdAndStatus(UUID programId, UUID userId,
			IskurTeamChangeStatus status);

	List<IskurTeamChangeRequest> findByProgramIdOrderByCreatedAtDesc(UUID programId);
}
