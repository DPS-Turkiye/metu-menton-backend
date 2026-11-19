package com.dps.teknokent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.domain.IskurChannelKind;
import com.dps.teknokent.entity.IskurChannelMute;

public interface IskurChannelMuteRepository extends JpaRepository<IskurChannelMute, UUID> {

	List<IskurChannelMute> findByProgramIdAndUserId(UUID programId, UUID userId);

	List<IskurChannelMute> findByProgramIdAndKind(UUID programId, IskurChannelKind kind);
}
