package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurWorkEntry;

public interface IskurWorkEntryRepository extends JpaRepository<IskurWorkEntry, UUID> {

	List<IskurWorkEntry> findByWeekIdOrderByEntryDateAscSortOrderAsc(UUID weekId);

	List<IskurWorkEntry> findByWeekIdIn(Collection<UUID> weekIds);
}
