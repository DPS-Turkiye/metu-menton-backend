package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.InstitutionDataSource;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.responses.AdminAcademicResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.OfficeHoursMeeting;
import com.dps.teknokent.entity.OfficeHoursOneOff;
import com.dps.teknokent.entity.OfficeHoursRule;
import com.dps.teknokent.entity.OfficeHoursUser;
import com.dps.teknokent.repository.AppUserIdentityRepository;
import com.dps.teknokent.repository.InstitutionAdvisingRepository;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.OfficeHoursMeetingRepository;
import com.dps.teknokent.repository.OfficeHoursUserRepository;

import jakarta.persistence.EntityManager;

/**
 * The admin's academician list with its Office Hours columns (FR-OH-30), on the real queries. The list can hold
 * thousands of academicians, so the query count must not grow with them.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class AdminAcademicOfficeHoursQueryTest {

	/** 15 November 2026: this month in Istanbul runs from 31 October 21:00 UTC to 30 November 21:00 UTC. */
	private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");

	@Autowired
	private EntityManager entityManager;
	@Autowired
	private DomainLookupService domainLookupService;
	@Autowired
	private InstitutionPersonRepository people;
	@Autowired
	private InstitutionAdvisingRepository advising;
	@Autowired
	private AppUserIdentityRepository identities;
	@Autowired
	private CoordinatorProgramService coordinatorProgramService;
	@Autowired
	private OfficeHoursUserRepository officeHoursPeople;
	@Autowired
	private OfficeHoursMeetingRepository meetings;

	private AdminAcademicService service;
	private AppUser admin;
	private int sequence;

	@BeforeEach
	void setUp() {
		service = new AdminAcademicService(domainLookupService, people, advising, identities,
				coordinatorProgramService, officeHoursPeople, meetings, Clock.fixed(NOW, ZoneOffset.UTC));
		admin = persistUser(UserRole.ADMIN);
	}

	@Test
	void usesTheSameNumberOfQueriesForOneAcademicianAndTen() {
		academicianWithHoursAndMeetings();
		long one = countQueries(() -> service.list(admin.getId()));
		for (int i = 0; i < 9; i++) {
			academicianWithHoursAndMeetings();
		}

		long ten = countQueries(() -> service.list(admin.getId()));

		assertThat(ten).isEqualTo(one);
	}

	@Test
	void countsOnlyThisMonthsLiveMeetingsAndOnlyHoursStillAhead() {
		OfficeHoursUser withHours = academician("Ali Demir");
		weeklyRule(withHours);
		// Istanbul's November: from 1 November 00:00 (31 October 21:00 UTC) up to 1 December 00:00.
		meeting(withHours, "2026-10-31T21:00:00Z", false);
		meeting(withHours, "2026-11-20T07:00:00Z", false);
		meeting(withHours, "2026-11-21T07:00:00Z", true);
		meeting(withHours, "2026-10-31T20:30:00Z", false);
		meeting(withHours, "2026-11-30T21:00:00Z", false);
		OfficeHoursUser pastOneOffOnly = academician("Ayşe Kaya");
		oneOff(pastOneOffOnly, "2026-11-10T07:00:00Z", "2026-11-10T08:00:00Z");
		OfficeHoursUser oneOffAhead = academician("Can Er");
		oneOff(oneOffAhead, "2026-11-20T07:00:00Z", "2026-11-20T08:00:00Z");
		entityManager.flush();

		List<AdminAcademicResponse> rows = service.list(admin.getId());

		assertThat(row(rows, "Ali Demir")).isEqualTo(new AdminAcademicResponse.OfficeHours(true, 2));
		assertThat(row(rows, "Ayşe Kaya")).isEqualTo(new AdminAcademicResponse.OfficeHours(false, 0));
		assertThat(row(rows, "Can Er")).isEqualTo(new AdminAcademicResponse.OfficeHours(true, 0));
	}

	private static AdminAcademicResponse.OfficeHours row(List<AdminAcademicResponse> rows, String name) {
		return rows.stream().filter(row -> row.fullName().equals(name)).findFirst().orElseThrow().officeHours();
	}

	private void academicianWithHoursAndMeetings() {
		OfficeHoursUser person = academician("Academician " + sequence);
		weeklyRule(person);
		meeting(person, "2026-11-20T07:00:00Z", false);
		meeting(person, "2026-11-21T07:00:00Z", false);
	}

	private long countQueries(Runnable call) {
		entityManager.flush();
		entityManager.clear();
		Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		call.run();
		return statistics.getPrepareStatementCount();
	}

	private OfficeHoursUser academician(String name) {
		AppUser account = persistUser(UserRole.ACADEMICIAN);
		InstitutionPerson record = new InstitutionPerson("odtu", "pid-" + sequence++);
		record.setUser(account);
		record.setAcademic(true);
		record.setFullName(name);
		record.setFaculty("Mühendislik");
		record.setDepartmentCode("CENG");
		record.setDepartmentName("Bilgisayar Mühendisliği");
		record.setAcademicTitle("Dr.");
		record.setSource(InstitutionDataSource.DEV_STUB);
		record.setSyncedAt(NOW);
		entityManager.persist(record);
		OfficeHoursUser person = new OfficeHoursUser();
		person.setId(UUID.randomUUID());
		person.setAppUserId(account.getId());
		person.setFullName(name);
		person.setCreatedAt(NOW);
		person.setLastLaunchAt(NOW);
		entityManager.persist(person);
		return person;
	}

	private void weeklyRule(OfficeHoursUser person) {
		OfficeHoursRule rule = new OfficeHoursRule();
		rule.setId(UUID.randomUUID());
		rule.setInstructorId(person.getId());
		rule.setDay(DayOfWeek.TUESDAY);
		rule.setStartTime(LocalTime.of(10, 0));
		rule.setEndTime(LocalTime.of(12, 0));
		rule.setSlotMinutes(30);
		rule.setCreatedAt(NOW);
		entityManager.persist(rule);
	}

	private void oneOff(OfficeHoursUser person, String startsAt, String endsAt) {
		OfficeHoursOneOff oneOff = new OfficeHoursOneOff();
		oneOff.setId(UUID.randomUUID());
		oneOff.setInstructorId(person.getId());
		oneOff.setStartsAt(Instant.parse(startsAt));
		oneOff.setEndsAt(Instant.parse(endsAt));
		entityManager.persist(oneOff);
	}

	private void meeting(OfficeHoursUser person, String at, boolean cancelled) {
		OfficeHoursMeeting meeting = new OfficeHoursMeeting();
		meeting.setId(UUID.randomUUID());
		meeting.setRequestId(UUID.randomUUID());
		meeting.setInstructorId(person.getId());
		meeting.setStudentId(UUID.randomUUID());
		meeting.setScheduledAt(Instant.parse(at));
		meeting.setDurationMinutes(30);
		meeting.setCreatedAt(NOW);
		if (cancelled) {
			meeting.setCancelledAt(NOW);
		}
		entityManager.persist(meeting);
	}

	private AppUser persistUser(UserRole role) {
		AppUser user = new AppUser();
		user.setEmail("academics-count-" + sequence++ + "@office-hours.test");
		user.setFullName("User " + sequence);
		user.setRole(role);
		entityManager.persist(user);
		return user;
	}
}
