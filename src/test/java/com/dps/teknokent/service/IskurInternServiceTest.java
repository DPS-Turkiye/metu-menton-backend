package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.PermissionSource;
import com.dps.teknokent.domain.ProgramMentorInvitedBy;
import com.dps.teknokent.domain.ProgramMentorStatus;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurInternEmail;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramMentor;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurInternEmailRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramMentorRepository;
import com.dps.teknokent.repository.ProgramRepository;

@SpringBootTest(properties = "app.edition=ODTU")
@Transactional
class IskurInternServiceTest {

	@Autowired
	private IskurInternService service;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private ProgramRepository programRepository;
	@Autowired
	private ProgramMentorRepository programMentorRepository;
	@Autowired
	private IskurUnitRepository unitRepository;
	@Autowired
	private IskurTeamRepository teamRepository;
	@Autowired
	private IskurPlacementRepository placementRepository;
	@Autowired
	private IskurInternEmailRepository internEmailRepository;

	private AppUser coordinator;
	private Program iskur;
	private IskurUnit iskurUnit;

	@BeforeEach
	void setUp() {
		coordinator = user(UserRole.COORDINATOR, null);
		iskur = program(ProgramType.PEER_MENTORING, Instant.now().minusSeconds(3600));
		iskurUnit = unitOf(iskur);
	}

	@Test
	void findsTheProgramOfAnApprovedStudent() {
		AppUser student = listed(iskur, user(UserRole.MENTEE, iskur));

		assertThat(service.currentProgram(student.getId())).map(Program::getId).contains(iskur.getId());
	}

	@Test
	void findsTheProgramOfAnAcceptedPeerMentor() {
		AppUser mentor = user(UserRole.MENTOR, null);
		mentorOf(iskur, mentor, ProgramMentorStatus.ACCEPTED);
		listed(iskur, mentor);

		assertThat(service.currentProgram(mentor.getId())).map(Program::getId).contains(iskur.getId());
	}

	@Test
	void ignoresAPeerMentorWhoHasNotAcceptedYet() {
		AppUser mentor = user(UserRole.MENTOR, null);
		mentorOf(iskur, mentor, ProgramMentorStatus.PENDING);
		listed(iskur, mentor);

		assertThat(service.currentProgram(mentor.getId())).isEmpty();
	}

	@Test
	void ignoresAStudentWhoseAccountIsNotApproved() {
		AppUser student = listed(iskur, user(UserRole.MENTEE, iskur));
		student.setStatus(UserAccountStatus.PENDING);
		userRepository.saveAndFlush(student);

		assertThat(service.currentProgram(student.getId())).isEmpty();
	}

	@Test
	void ignoresAProgramWithoutIskurUnits() {
		Program bare = program(ProgramType.PEER_MENTORING, Instant.now());
		AppUser student = listed(bare, user(UserRole.MENTEE, bare));

		assertThat(service.currentProgram(student.getId())).isEmpty();
	}

	@Test
	void ignoresArchivedAndOtherKindsOfPrograms() {
		Program archived = program(ProgramType.PEER_MENTORING, Instant.now());
		archived.setStatus(ProgramStatus.COMPLETED);
		programRepository.saveAndFlush(archived);
		unitOf(archived);
		Program officeHours = program(ProgramType.OFFICE_HOURS, Instant.now());
		unitOf(officeHours);

		assertThat(service.currentProgram(listed(archived, user(UserRole.MENTEE, archived)).getId())).isEmpty();
		assertThat(service.currentProgram(listed(officeHours, user(UserRole.MENTEE, officeHours)).getId()))
				.isEmpty();
	}

	@Test
	void prefersTheNewestProgramWhenThePersonIsInTwo() {
		Program newer = program(ProgramType.PEER_MENTORING, Instant.now());
		unitOf(newer);
		AppUser student = user(UserRole.MENTEE, iskur);
		student.grant(UserRole.MENTOR, PermissionSource.ADMIN, Instant.now());
		userRepository.saveAndFlush(student);
		mentorOf(newer, student, ProgramMentorStatus.ACCEPTED);
		listed(iskur, student);
		listed(newer, student);

		assertThat(service.currentProgram(student.getId())).map(Program::getId).contains(newer.getId());
	}

	@Test
	void aStudentOffTheListIsAPlainStudent() {
		AppUser plain = user(UserRole.MENTEE, iskur);
		AppUser intern = listed(iskur, user(UserRole.MENTEE, iskur));
		AppUser mentor = user(UserRole.MENTOR, null);
		mentorOf(iskur, mentor, ProgramMentorStatus.ACCEPTED);

		assertThat(service.currentProgram(plain.getId())).isEmpty();
		assertThat(service.currentPlacement(plain.getId())).isEmpty();
		assertThat(service.currentProgram(mentor.getId())).isEmpty();
		assertThat(service.internIds(iskur.getId())).containsExactly(intern.getId());
		assertThat(service.isIntern(iskur.getId(), intern.getId())).isTrue();
		assertThat(service.isIntern(iskur.getId(), plain.getId())).isFalse();
		assertThat(service.isIntern(iskur.getId(), mentor.getId())).isFalse();
	}

	@Test
	void anAddressListedBeforeSignUpMakesAnInternOnceTheAccountJoins() {
		internEmailRepository.saveAndFlush(new IskurInternEmail(iskur.getId(), "yeni.stajyer@example.test",
				coordinator.getId(), Instant.now()));
		assertThat(service.internIds(iskur.getId())).isEmpty();

		AppUser joined = user(UserRole.MENTEE, iskur);
		joined.setEmail("Yeni.Stajyer@Example.test");
		userRepository.saveAndFlush(joined);

		assertThat(service.currentProgram(joined.getId())).map(Program::getId).contains(iskur.getId());
		assertThat(service.internIds(iskur.getId())).containsExactly(joined.getId());
		assertThat(service.isIntern(iskur.getId(), joined.getId())).isTrue();
	}

	@Test
	void aListingCountsOnlyInItsOwnProgram() {
		Program other = program(ProgramType.PEER_MENTORING, Instant.now());
		unitOf(other);
		AppUser student = listed(other, user(UserRole.MENTEE, iskur));

		assertThat(service.currentProgram(student.getId())).isEmpty();
		assertThat(service.internIds(iskur.getId())).isEmpty();
		assertThat(service.internIds(other.getId())).isEmpty();
		assertThat(service.isIntern(other.getId(), student.getId())).isFalse();
	}

	@Test
	void listsAcceptedPeerMentorsOnTheListAsInterns() {
		AppUser mentor = user(UserRole.MENTOR, null);
		mentorOf(iskur, mentor, ProgramMentorStatus.ACCEPTED);
		listed(iskur, mentor);
		AppUser waiting = user(UserRole.MENTOR, null);
		mentorOf(iskur, waiting, ProgramMentorStatus.PENDING);
		listed(iskur, waiting);

		assertThat(service.internIds(iskur.getId())).containsExactly(mentor.getId());
		assertThat(service.isIntern(iskur.getId(), mentor.getId())).isTrue();
		assertThat(service.isIntern(iskur.getId(), waiting.getId())).isFalse();
	}

	@Test
	void aBadgeHolderOrAnAcceptedPeerMentorCanMentorAndEveryOtherInternWaitsForTheBadge() {
		AppUser pending = listed(iskur, user(UserRole.MENTEE, iskur));
		AppUser badged = listed(iskur, user(UserRole.MENTEE, iskur));
		badged.grant(UserRole.MENTOR, PermissionSource.BADGE, Instant.now());
		badged = userRepository.saveAndFlush(badged);
		AppUser accepted = user(UserRole.MENTOR, null);
		mentorOf(iskur, accepted, ProgramMentorStatus.ACCEPTED);
		listed(iskur, accepted);

		assertThat(service.peerMentorIds(iskur.getId(), List.of(pending, badged, accepted)))
				.containsExactly(badged.getId(), accepted.getId());
		assertThat(service.peerMentorIds(iskur.getId(), List.of())).isEmpty();
	}

	@Test
	void findsThePlacementInTheCurrentProgram() {
		AppUser student = listed(iskur, user(UserRole.MENTEE, iskur));
		assertThat(service.currentPlacement(student.getId())).isEmpty();

		IskurTeam team = teamRepository.saveAndFlush(new IskurTeam(iskurUnit, "Ekip", 0, Instant.now()));
		IskurPlacement placement = new IskurPlacement(iskur.getId(), student.getId(), coordinator.getId(),
				Instant.now());
		placement.setTeamId(team.getId());
		placement.setTeamRole(IskurTeamRole.MEMBER);
		placementRepository.saveAndFlush(placement);

		assertThat(service.currentPlacement(student.getId())).map(IskurPlacement::getTeamId).contains(team.getId());
	}

	@Test
	void knowsNothingOfAnUnknownPerson() {
		assertThat(service.currentProgram(UUID.randomUUID())).isEmpty();
		assertThat(service.currentPlacement(UUID.randomUUID())).isEmpty();
		assertThat(service.isIntern(iskur.getId(), UUID.randomUUID())).isFalse();
	}

	/** Puts the person's address on the program's intern list, lowercased as the list stores it. */
	private AppUser listed(Program program, AppUser person) {
		internEmailRepository.saveAndFlush(new IskurInternEmail(program.getId(),
				person.getEmail().toLowerCase(Locale.ROOT), coordinator.getId(), Instant.now()));
		return person;
	}

	private AppUser user(UserRole role, Program joined) {
		AppUser user = new AppUser();
		user.setFullName("Kişi " + UUID.randomUUID());
		user.setEmail(UUID.randomUUID() + "@example.test");
		user.setRole(role);
		if (joined != null) {
			user.setProgramId(joined.getId());
		}
		return userRepository.saveAndFlush(user);
	}

	private Program program(ProgramType type, Instant createdAt) {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName("Program " + UUID.randomUUID());
		created.setCreatedBy(coordinator.getId());
		created.setCreatedAt(createdAt);
		created.setStatus(ProgramStatus.ACTIVE);
		created.setProgramType(type);
		return programRepository.saveAndFlush(created);
	}

	private IskurUnit unitOf(Program program) {
		return unitRepository.saveAndFlush(new IskurUnit(program.getId(), "Birim", 0, coordinator.getId(),
				Instant.now()));
	}

	private void mentorOf(Program pool, AppUser mentor, ProgramMentorStatus status) {
		ProgramMentor membership = new ProgramMentor();
		membership.setId(UUID.randomUUID());
		membership.setProgramId(pool.getId());
		membership.setMentorId(mentor.getId());
		membership.setStatus(status);
		membership.setInvitedBy(ProgramMentorInvitedBy.COORDINATOR);
		membership.setCreatedAt(Instant.now());
		programMentorRepository.saveAndFlush(membership);
	}
}
