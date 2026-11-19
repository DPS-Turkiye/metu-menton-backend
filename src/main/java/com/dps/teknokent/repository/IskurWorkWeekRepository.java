package com.dps.teknokent.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurWorkWeek;

public interface IskurWorkWeekRepository extends JpaRepository<IskurWorkWeek, UUID> {

	Optional<IskurWorkWeek> findByProgramIdAndUserIdAndWeekStart(UUID programId, UUID userId, LocalDate weekStart);

	List<IskurWorkWeek> findByProgramIdAndWeekStartAndUserIdIn(UUID programId, LocalDate weekStart,
			Collection<UUID> userIds);
}
