package com.dps.teknokent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.dps.teknokent.entity.OfficeHoursCalendarFeed;
import com.dps.teknokent.entity.OfficeHoursUser;

import jakarta.persistence.EntityManager;

@DataJpaTest
class OfficeHoursCalendarFeedPersistenceTest {

	private static final Instant NOW = Instant.parse("2026-11-12T15:00:00Z");

	@Autowired
	private OfficeHoursCalendarFeedRepository feeds;

	@Autowired
	private EntityManager entityManager;

	private OfficeHoursUser person(String subject) {
		OfficeHoursUser person = new OfficeHoursUser();
		person.setId(UUID.randomUUID());
		person.setIssuer("https://odtuclass.metu.edu.tr");
		person.setSubject(subject);
		person.setFullName("Zeynep Demir");
		person.setCreatedAt(NOW);
		person.setLastLaunchAt(NOW);
		entityManager.persist(person);
		return person;
	}

	@Test
	void aLinkIsFoundByItsTokenAndByItsOwner() {
		OfficeHoursUser person = person("feed-owner");
		OfficeHoursCalendarFeed feed = new OfficeHoursCalendarFeed();
		feed.setId(UUID.randomUUID());
		feed.setUserId(person.getId());
		feed.setToken("c".repeat(43));
		feed.setCreatedAt(NOW);
		feeds.saveAndFlush(feed);
		entityManager.clear();

		assertThat(feeds.findByToken("c".repeat(43))).hasValueSatisfying(
				found -> assertThat(found.getUserId()).isEqualTo(person.getId()));
		assertThat(feeds.findByUserId(person.getId())).isPresent();
		assertThat(feeds.findByToken("d".repeat(43))).isEmpty();
	}

	/** PostgreSQL only (ON CONFLICT): run with the datasource pointed at PostgreSQL; skipped on the H2 test database. */
	@Test
	@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql:.*")
	void aSecondFirstOpenKeepsTheFirstLink() {
		OfficeHoursUser person = person("feed-race");

		assertThat(feeds.insertForUserIfAbsent(UUID.randomUUID(), person.getId(), "e".repeat(43), NOW)).isEqualTo(1);
		assertThat(feeds.insertForUserIfAbsent(UUID.randomUUID(), person.getId(), "f".repeat(43), NOW)).isZero();

		assertThat(feeds.findByUserId(person.getId()).orElseThrow().getToken()).isEqualTo("e".repeat(43));
	}
}
