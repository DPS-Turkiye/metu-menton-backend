package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.IskurChannel;

import jakarta.persistence.LockModeType;

public interface IskurChannelRepository extends JpaRepository<IskurChannel, UUID> {

	List<IskurChannel> findByProgramId(UUID programId);

	List<IskurChannel> findByProgramIdIn(Collection<UUID> programIds);

	/** Locked until the transaction ends, so posts in one channel get their times, and lastMessageAt, in order. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from IskurChannel c where c.id = :id")
	Optional<IskurChannel> findByIdForUpdate(@Param("id") UUID id);
}
