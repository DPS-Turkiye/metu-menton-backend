package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramMentor;
import com.dps.teknokent.repository.AppUserRepository;
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
		AppUser student = user(UserRole.MENTEE, iskur);

		assertThat(service.currentProgram(student.getId())).map(Program::getId).contains(iskur.getId());
	}

	@Test
	void findsTheProgramOfAnAcceptedPeerMentor() {
		AppUser mentor = user(UserRole.MENTOR, null);
		mentorOf(iskur, mentor, ProgramMentorStatus.ACCEPTED);

		assertThat(service.currentProgram(mentor.getId())).map(Program::getId).contains(iskur.getId());
	}

	@Test
	void ignoresAPeerMentorWhoHasNotAcceptedYet() {
		AppUser mentor = user(UserRole.MENTOR, null);
		mentorOf(iskur, mentor, ProgramMentorStatus.PENDING);

		assertThat(service.currentProgram(mentor.getId())).isEmpty();
	}

	@Test
	void ignoresAStudentWhoseAccountIsNotApproved() {
		AppUser student = user(UserRole.MENTEE, iskur);
		student.setStatus(UserAccountStatus.PENDING);
		userRepository.saveAndFlush(student);

		assertThat(service.currentProgram(student.getId())).isEmpty();
	}

	@Test
	void ignoresAProgramWithoutIskurUnits() {
		Program bare = program(ProgramType.PEER_MENTORING, Instant.now());
		AppUser student = user(UserRole.MENTEE, bare);

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

		assertThat(service.currentProgram(user(UserRole.MENTEE, archived).getId())).isEmpty();
		assertThat(service.currentProgram(user(UserRole.MENTEE, officeHours).getId())).isEmpty();
	}

	@Test
	void prefersTheNewestProgramWhenThePersonIsInTwo() {
		Program newer = program(ProgramType.PEER_MENTORING, Instant.now());
		unitOf(newer);
		AppUser student = user(UserRole.MENTEE, iskur);
		student.grant(UserRole.MENTOR, PermissionSource.ADMIN, Instant.now());
		userRepository.saveAndFlush(student);
		mentorOf(newer, student, ProgramMentorStatus.ACCEPTED);

		assertThat(service.currentProgram(student.getId())).map(Program::getId).contains(newer.getId());
	}

	@Test
	void findsThePlacementInTheCurrentProgram() {
		AppUser student = user(UserRole.MENTEE, iskur);
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
