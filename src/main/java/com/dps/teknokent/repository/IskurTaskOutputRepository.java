package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.IskurTaskOutput;

public interface IskurTaskOutputRepository extends JpaRepository<IskurTaskOutput, UUID> {

	List<IskurTaskOutput> findByTaskIdInOrderByCreatedAtAscIdAsc(Collection<UUID> taskIds);

	Optional<IskurTaskOutput> findByIdAndTaskId(UUID id, UUID taskId);

	long countByTaskId(UUID taskId);

	/** Every output of the teams' tasks. */
	@Query("""
			select o from IskurTaskOutput o
			where o.taskId in (select t.id from IskurTask t where t.teamId in :teamIds)
			""")
	List<IskurTaskOutput> findByTeamIds(@Param("teamIds") Collection<UUID> teamIds);

	/** Every output of a program's tasks, newest first. */
	@Query("""
			select o from IskurTaskOutput o
			where o.taskId in (select t.id from IskurTask t where t.programId = :programId)
			order by o.createdAt desc, o.id desc
			""")
	List<IskurTaskOutput> findByProgramId(@Param("programId") UUID programId);
}
