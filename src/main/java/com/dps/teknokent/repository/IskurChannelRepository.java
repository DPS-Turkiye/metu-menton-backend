package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurChannel;

public interface IskurChannelRepository extends JpaRepository<IskurChannel, UUID> {

	List<IskurChannel> findByProgramId(UUID programId);

	List<IskurChannel> findByProgramIdIn(Collection<UUID> programIds);
}
