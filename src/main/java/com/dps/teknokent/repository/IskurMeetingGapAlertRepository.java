package com.dps.teknokent.repository;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurMeetingGapAlert;

public interface IskurMeetingGapAlertRepository extends JpaRepository<IskurMeetingGapAlert, UUID> {

	boolean existsByProgramIdAndUserIdAndWeekStart(UUID programId, UUID userId, LocalDate weekStart);
}
