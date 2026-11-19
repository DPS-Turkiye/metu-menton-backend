package com.dps.teknokent.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.IskurMessage;

public interface IskurMessageRepository extends JpaRepository<IskurMessage, UUID> {

	/** How many messages one channel has unread. */
	interface ChannelCount {
		UUID getChannelId();

		long getCount();
	}

	List<IskurMessage> findByChannelIdOrderByCreatedAtDescIdDesc(UUID channelId, Pageable page);

	/** The page before one message: (created_at, id) is the cursor, so messages sent at the same moment are kept. */
	@Query("""
			select m from IskurMessage m
			where m.channelId = :channelId
			and (m.createdAt < :at or (m.createdAt = :at and m.id < :id))
			order by m.createdAt desc, m.id desc""")
	List<IskurMessage> findPageBefore(@Param("channelId") UUID channelId, @Param("at") Instant at,
			@Param("id") UUID id, Pageable page);

	/** Messages someone else wrote after the reader's mark, per channel; a channel never opened counts them all. */
	@Query("""
			select m.channelId as channelId, count(m) as count from IskurMessage m
			where m.channelId in :channelIds
			and (m.senderId is null or m.senderId <> :readerId)
			and m.createdAt > coalesce((select r.lastReadAt from IskurChannelRead r
				where r.channelId = m.channelId and r.userId = :readerId), :floor)
			group by m.channelId""")
	List<ChannelCount> countUnread(@Param("channelIds") Collection<UUID> channelIds, @Param("readerId") UUID readerId,
			@Param("floor") Instant floor);
}
