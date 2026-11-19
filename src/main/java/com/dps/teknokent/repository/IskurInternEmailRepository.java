package com.dps.teknokent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.IskurInternEmail;

public interface IskurInternEmailRepository extends JpaRepository<IskurInternEmail, UUID> {

	List<IskurInternEmail> findByProgramIdOrderByEmailAsc(UUID programId);

	/** {@code email} must already be normalised (trimmed, lowercased), as the list stores it. */
	boolean existsByProgramIdAndEmail(UUID programId, String email);

	@Query("select e.email from IskurInternEmail e where e.programId = :programId")
	List<String> findEmailsByProgramId(@Param("programId") UUID programId);
}
