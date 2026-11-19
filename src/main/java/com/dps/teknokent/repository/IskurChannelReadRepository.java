package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurChannelRead;

public interface IskurChannelReadRepository extends JpaRepository<IskurChannelRead, UUID> {

	List<IskurChannelRead> findByUserIdAndChannelIdIn(UUID userId, Collection<UUID> channelIds);

	List<IskurChannelRead> findByChannelId(UUID channelId);

	Optional<IskurChannelRead> findByChannelIdAndUserId(UUID channelId, UUID userId);
}
