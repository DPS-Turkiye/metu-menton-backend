package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.IskurWorkEntry;

public interface IskurWorkEntryRepository extends JpaRepository<IskurWorkEntry, UUID> {

	List<IskurWorkEntry> findByWeekIdOrderByEntryDateAscSortOrderAsc(UUID weekId);

	List<IskurWorkEntry> findByWeekIdIn(Collection<UUID> weekIds);

	/**
	 * The task goes to someone else: the lines that used it keep their own title and minutes, as when it is deleted.
	 */
	@Modifying(flushAutomatically = true)
	@Query("update IskurWorkEntry entry set entry.taskId = null where entry.taskId = :taskId")
	void detachTask(@Param("taskId") UUID taskId);
}
