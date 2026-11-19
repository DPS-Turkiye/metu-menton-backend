package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.IskurMessageReceipt;

public interface IskurMessageReceiptRepository extends JpaRepository<IskurMessageReceipt, UUID> {

	/** How many people confirmed each message. */
	interface ReceiptCount {
		UUID getMessageId();

		long getCount();
	}

	List<IskurMessageReceipt> findByMessageId(UUID messageId);

	List<IskurMessageReceipt> findByUserIdAndMessageIdIn(UUID userId, Collection<UUID> messageIds);

	Optional<IskurMessageReceipt> findByMessageIdAndUserId(UUID messageId, UUID userId);

	@Query("""
			select r.messageId as messageId, count(r) as count from IskurMessageReceipt r
			where r.messageId in :messageIds group by r.messageId""")
	List<ReceiptCount> countByMessage(@Param("messageIds") Collection<UUID> messageIds);
}
