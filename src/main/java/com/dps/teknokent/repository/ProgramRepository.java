package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.entity.Program;

import jakarta.persistence.LockModeType;

public interface ProgramRepository extends JpaRepository<Program, UUID> {
	List<Program> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

	List<Program> findAllByOrderByCreatedAtDesc();

	List<Program> findByProgramType(ProgramType programType);

	List<Program> findByProgramTypeIn(Collection<ProgramType> programTypes);

	/**
	 * Every program that runs {@code feature}, in any status. A semester keeps its switches in three columns and the
	 * older types imply theirs ({@link Program#features}), so the few ODTÜ rows are read and filtered here.
	 */
	default List<Program> findWithFeature(ProgramFeature feature) {
		return findByProgramTypeIn(EnumSet.of(ProgramType.SEMESTER, ProgramType.PEER_MENTORING,
				ProgramType.OFFICE_HOURS)).stream()
				.filter(program -> program.has(feature))
				.toList();
	}

	List<Program> findBySyllabusDocumentId(UUID syllabusDocumentId);

	/**
	 * Every program of the type, each row locked until the transaction ends, in id order so two callers never wait
	 * on each other crosswise. Read first in a transaction, the rows come back as the last committer left them.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Program p where p.programType = :type order by p.id")
	List<Program> lockAllOfType(@Param("type") ProgramType type);

	/** The row locked until the transaction ends, so changes to one program's İŞKUR structure run one at a time. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Program p where p.id = :id")
	Optional<Program> findByIdForUpdate(@Param("id") UUID id);
}
