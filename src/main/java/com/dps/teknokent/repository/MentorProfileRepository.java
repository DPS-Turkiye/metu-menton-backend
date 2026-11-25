package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.entity.MentorProfile;

public interface MentorProfileRepository extends JpaRepository<MentorProfile, UUID> {

	@EntityGraph(attributePaths = "user")
	List<MentorProfile> findAllByUserStatusOrderBySortOrderAsc(UserAccountStatus status);

	@EntityGraph(attributePaths = "user")
	Optional<MentorProfile> findOneById(UUID id);

	@Query("select coalesce(max(profile.sortOrder), 0) from MentorProfile profile")
	int findMaxSortOrder();

	/** Those among these mentors who take no new requests. */
	@Query("select profile.id from MentorProfile profile where profile.id in :ids and profile.acceptingRequests = false")
	Set<UUID> findIdsNotAcceptingRequests(@Param("ids") Collection<UUID> ids);
}
