package com.dps.teknokent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.IskurTeamSupportRequest;

public interface IskurTeamSupportRequestRepository extends JpaRepository<IskurTeamSupportRequest, UUID> {

	/** Everything the team opened or was asked for, newest first. */
	@Query("select r from IskurTeamSupportRequest r where r.fromTeamId = :teamId or r.toTeamId = :teamId "
			+ "order by r.createdAt desc")
	List<IskurTeamSupportRequest> findForTeam(@Param("teamId") UUID teamId);

	List<IskurTeamSupportRequest> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
