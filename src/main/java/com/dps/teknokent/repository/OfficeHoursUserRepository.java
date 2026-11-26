package com.dps.teknokent.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dps.teknokent.entity.OfficeHoursUser;

import jakarta.persistence.LockModeType;

public interface OfficeHoursUserRepository extends JpaRepository<OfficeHoursUser, UUID> {

	Optional<OfficeHoursUser> findByIssuerAndSubject(String issuer, String subject);

	Optional<OfficeHoursUser> findByAppUserId(UUID appUserId);

	/**
	 * Each account's office-hours person, and whether it has hours: a weekly range, or one-off hours that are not over
	 * at {@code now}. One query for any number of accounts.
	 */
	@Query("""
			select person.appUserId as accountId, person.id as personId,
			  (case when exists (select 1 from OfficeHoursRule rule where rule.instructorId = person.id)
			      or exists (select 1 from OfficeHoursOneOff oneOff
			                 where oneOff.instructorId = person.id and oneOff.endsAt > :now)
			    then true else false end) as hoursDefined
			from OfficeHoursUser person
			where person.appUserId in :accountIds
			""")
	List<PersonHours> findHoursByAccountIdIn(@Param("accountIds") Collection<UUID> accountIds,
			@Param("now") Instant now);

	interface PersonHours {

		UUID getAccountId();

		UUID getPersonId();

		boolean isHoursDefined();
	}

	/** The person, locked until the transaction ends: one request of theirs is checked and saved at a time. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select person from OfficeHoursUser person where person.id = :id")
	Optional<OfficeHoursUser> findForUpdate(@Param("id") UUID id);

	/**
	 * The Menton side of {@link #insertIfAbsent}: two first visits at once leave one row for the account. The
	 * language is only Moodle's; a Menton account's mail follows app_user's.
	 */
	@Modifying
	@Query(value = """
			INSERT INTO office_hours_user (id, app_user_id, full_name, preferred_language, created_at, last_launch_at)
			VALUES (:id, :appUserId, :fullName, 'TR', :now, :now)
			ON CONFLICT DO NOTHING
			""", nativeQuery = true)
	int insertForAccountIfAbsent(@Param("id") UUID id, @Param("appUserId") UUID appUserId,
			@Param("fullName") String fullName, @Param("now") Instant now);

	/**
	 * Adds the person unless they are already there. Two first launches at once both succeed this way, where a
	 * plain insert would fail one of them on the unique (issuer, subject).
	 */
	@Modifying
	@Query(value = """
			INSERT INTO office_hours_user (id, issuer, subject, full_name, preferred_language, created_at, last_launch_at)
			VALUES (:id, :issuer, :subject, :fullName, 'TR', :now, :now)
			ON CONFLICT (issuer, subject) DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(@Param("id") UUID id, @Param("issuer") String issuer, @Param("subject") String subject,
			@Param("fullName") String fullName, @Param("now") Instant now);
}
