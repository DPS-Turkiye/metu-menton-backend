package com.dps.teknokent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.dps.teknokent.domain.BadgeCode;
import com.dps.teknokent.domain.BadgeSource;
import com.dps.teknokent.domain.BadgeStatus;
import com.dps.teknokent.domain.InstitutionDataSource;
import com.dps.teknokent.domain.MeetingMode;
import com.dps.teknokent.domain.MentorshipRequestFormat;
import com.dps.teknokent.domain.MentorshipRequestStatus;
import com.dps.teknokent.domain.MentorshipRequestUrgency;
import com.dps.teknokent.domain.PermissionSource;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.MentorshipRequest;
import com.dps.teknokent.entity.OfficeHoursRule;
import com.dps.teknokent.entity.OfficeHoursUser;
import com.dps.teknokent.entity.Organization;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.UserBadge;

import jakarta.persistence.EntityManager;

/** The grouped counts on a peer or Office Hours program's card and its row in the admin's list. */
@DataJpaTest
class ProgramParticipantCountQueryTest {

	private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

	@Autowired
	private AppUserRepository users;
	@Autowired
	private UserBadgeRepository awards;
	@Autowired
	private MentorshipRequestRepository requests;
	@Autowired
	private InstitutionPersonRepository institutionPeople;
	@Autowired
	private EntityManager entityManager;

	@Test
	void countsEachProgramsApprovedStudentsWithTheRosterRule() {
		Program peer = program();
		Program other = program();
		Program empty = program();
		user(UserRole.MENTEE, UserAccountStatus.APPROVED, peer);
		user(UserRole.MENTEE, UserAccountStatus.APPROVED, peer);
		user(UserRole.MENTEE, UserAccountStatus.PENDING, peer);
		user(UserRole.MENTOR, UserAccountStatus.APPROVED, peer);
		user(UserRole.MENTOR, UserAccountStatus.APPROVED, peer).grant(UserRole.MENTEE, PermissionSource.ADMIN, NOW);
		user(UserRole.MENTEE, UserAccountStatus.APPROVED, other);
		entityManager.flush();

		Map<UUID, Long> counted = byProgram(users.countByProgramIdInAndPermissionAndStatus(
				List.of(peer.getId(), other.getId(), empty.getId()), UserRole.MENTEE, UserAccountStatus.APPROVED));

		assertThat(counted).containsOnly(Map.entry(peer.getId(), 3L), Map.entry(other.getId(), 1L));
		assertThat(counted.get(peer.getId())).isEqualTo((long) users
				.findByProgramIdAndPermissionAndStatusOrderByCreatedAtAsc(peer.getId(), UserRole.MENTEE,
						UserAccountStatus.APPROVED)
				.size());
	}

	@Test
	void countsActiveGateBadgesOfEachProgramOnly() {
		Program peer = program();
		Program other = program();
		AppUser holder = user(UserRole.MENTEE, UserAccountStatus.APPROVED, peer);
		award(holder, BadgeCode.PEER_MENTOR, peer, BadgeStatus.ACTIVE);
		award(user(UserRole.MENTEE, UserAccountStatus.APPROVED, peer), BadgeCode.PEER_MENTOR, peer,
				BadgeStatus.REVOKED);
		award(holder, BadgeCode.FIRST_SESSION, peer, BadgeStatus.ACTIVE);
		award(user(UserRole.MENTEE, UserAccountStatus.APPROVED, other), BadgeCode.PEER_MENTOR, other,
				BadgeStatus.ACTIVE);
		entityManager.flush();

		Map<UUID, Long> counted = byProgram(awards.countByProgramIdInAndBadgeCodeAndStatus(List.of(peer.getId()),
				BadgeCode.PEER_MENTOR, BadgeStatus.ACTIVE));

		assertThat(counted).containsOnly(Map.entry(peer.getId(), 1L));
	}

	@Test
	void countsTheProgramsRequestsInTheGivenStatuses() {
		Program peer = program();
		Program other = program();
		request(peer, MentorshipRequestStatus.PENDING);
		request(peer, MentorshipRequestStatus.COUNTER_PROPOSED);
		request(peer, MentorshipRequestStatus.ACCEPTED);
		request(other, MentorshipRequestStatus.PENDING);
		entityManager.flush();

		Map<UUID, Long> counted = byProgram(requests.countByProgramIdInAndStatusIn(List.of(peer.getId()),
				List.of(MentorshipRequestStatus.PENDING, MentorshipRequestStatus.COUNTER_PROPOSED)));

		assertThat(counted).containsOnly(Map.entry(peer.getId(), 2L));
	}

	@Test
	void countsActiveAcademiciansAndThoseWithWeeklyHours() {
		AppUser withHours = user(UserRole.MENTOR, UserAccountStatus.APPROVED, null);
		academician(withHours, true);
		OfficeHoursUser instructor = instructor(withHours);
		weeklyHours(instructor);
		weeklyHours(instructor);
		academician(user(UserRole.MENTOR, UserAccountStatus.APPROVED, null), true);
		academician(null, true);
		AppUser leftWithHours = user(UserRole.MENTOR, UserAccountStatus.APPROVED, null);
		academician(leftWithHours, false);
		weeklyHours(instructor(leftWithHours));
		entityManager.flush();

		assertThat(institutionPeople.countByAcademicTrueAndActiveTrue()).isEqualTo(3);
		assertThat(institutionPeople.countActiveAcademicsWithWeeklyHours()).isEqualTo(1);
	}

	private static Map<UUID, Long> byProgram(List<ProgramTally> tallies) {
		return tallies.stream().collect(Collectors.toMap(ProgramTally::getProgramId, ProgramTally::getTotal));
	}

	private Program program() {
		AppUser creator = user(UserRole.COORDINATOR, UserAccountStatus.APPROVED, null);
		Program program = new Program();
		program.setId(UUID.randomUUID());
		program.setName("Akran Mentörlüğü");
		program.setProgramType(ProgramType.PEER_MENTORING);
		program.setCreatedBy(creator.getId());
		program.setCreatedAt(NOW);
		entityManager.persist(program);
		return program;
	}

	private AppUser user(UserRole role, UserAccountStatus status, @Nullable Program program) {
		AppUser user = new AppUser();
		user.setEmail(UUID.randomUUID() + "@test");
		user.setFullName("Kişi");
		user.setRole(role);
		user.setStatus(status);
		user.setProgramId(program == null ? null : program.getId());
		entityManager.persist(user);
		return user;
	}

	private void award(AppUser user, BadgeCode code, Program program, BadgeStatus status) {
		UserBadge award = new UserBadge();
		award.setId(UUID.randomUUID());
		award.setUserId(user.getId());
		award.setBadgeCode(code);
		award.setProgramId(program.getId());
		award.setStatus(status);
		award.setSource(BadgeSource.ADMIN);
		award.setAwardedAt(NOW);
		if (status == BadgeStatus.REVOKED) {
			award.setRevokedAt(NOW);
		}
		entityManager.persist(award);
	}

	private void request(Program program, MentorshipRequestStatus status) {
		Organization team = new Organization();
		team.setName("Takım");
		team.setApprovalStatus(UserAccountStatus.APPROVED);
		entityManager.persist(team);
		MentorshipRequest request = new MentorshipRequest();
		request.setRequester(user(UserRole.MENTEE, UserAccountStatus.APPROVED, program));
		request.setOrganization(team);
		request.setProgram(program);
		request.setMentor(user(UserRole.MENTEE, UserAccountStatus.APPROVED, program));
		request.setRequestedForName("Öğrenci");
		request.setSupportArea("Kariyer");
		request.setSummary("Staj");
		request.setUrgency(MentorshipRequestUrgency.THIS_WEEK);
		request.setMentorshipFormat(MentorshipRequestFormat.SINGLE_MEETING);
		request.setMeetingMode(MeetingMode.ONLINE);
		request.setStatus(status);
		request.setCreatedAt(NOW);
		request.setUpdatedAt(NOW);
		entityManager.persist(request);
	}

	private void academician(@Nullable AppUser user, boolean active) {
		InstitutionPerson person = new InstitutionPerson();
		person.setInstitution("ODTU");
		person.setPersistentId(UUID.randomUUID().toString());
		person.setUser(user);
		person.setAcademic(true);
		person.setFullName("Akademisyen");
		person.setActive(active);
		person.setSource(InstitutionDataSource.IMPORT);
		person.setSyncedAt(NOW);
		entityManager.persist(person);
	}

	private OfficeHoursUser instructor(AppUser account) {
		OfficeHoursUser instructor = new OfficeHoursUser();
		instructor.setId(UUID.randomUUID());
		instructor.setAppUserId(account.getId());
		instructor.setFullName("Akademisyen");
		instructor.setCreatedAt(NOW);
		instructor.setLastLaunchAt(NOW);
		entityManager.persist(instructor);
		return instructor;
	}

	private void weeklyHours(OfficeHoursUser instructor) {
		OfficeHoursRule rule = new OfficeHoursRule();
		rule.setId(UUID.randomUUID());
		rule.setInstructorId(instructor.getId());
		rule.setDayOfWeek((short) 2);
		rule.setStartTime(LocalTime.of(14, 0));
		rule.setEndTime(LocalTime.of(16, 0));
		rule.setSlotMinutes(30);
		rule.setCreatedAt(NOW);
		entityManager.persist(rule);
	}
}
