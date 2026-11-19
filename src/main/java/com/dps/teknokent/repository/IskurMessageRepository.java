package com.dps.teknokent.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.IskurMessage;

public interface IskurMessageRepository extends JpaRepository<IskurMessage, UUID> {

	List<IskurMessage> findByChannelIdOrderByCreatedAtDescIdDesc(UUID channelId, Pageable page);

	List<IskurMessage> findByChannelIdAndCreatedAtBeforeOrderByCreatedAtDescIdDesc(UUID channelId, Instant before,
			Pageable page);

	/** Messages after the reader's mark that someone else wrote. */
	@Query("""
			select count(m) from IskurMessage m
			where m.channelId = :channelId and m.createdAt > :after
			and (m.senderId is null or m.senderId <> :readerId)""")
	long countUnread(@Param("channelId") UUID channelId, @Param("after") Instant after,
			@Param("readerId") UUID readerId);
}
