package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurChannelMember;

public interface IskurChannelMemberRepository extends JpaRepository<IskurChannelMember, UUID> {

	List<IskurChannelMember> findByChannelIdIn(Collection<UUID> channelIds);

	List<IskurChannelMember> findByUserIdAndLeftAtIsNull(UUID userId);

	List<IskurChannelMember> findByChannelIdAndLeftAtIsNull(UUID channelId);

	Optional<IskurChannelMember> findByChannelIdAndUserIdAndLeftAtIsNull(UUID channelId, UUID userId);
}
