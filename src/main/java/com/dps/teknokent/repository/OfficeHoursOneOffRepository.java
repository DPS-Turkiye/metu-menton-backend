package com.dps.teknokent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.OfficeHoursOneOff;

public interface OfficeHoursOneOffRepository extends JpaRepository<OfficeHoursOneOff, UUID> {

	List<OfficeHoursOneOff> findByInstructorIdOrderByStartsAtAsc(UUID instructorId);
}
