package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dps.teknokent.domain.IskurChannelKind;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurChannel;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurChannelMemberRepository;
import com.dps.teknokent.repository.IskurChannelRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.service.IskurPlacementChangedEvent;

/**
 * The channels follow a placement only once it is committed. Not {@code @Transactional}, so the commit and the
 * after-commit listener really happen; the rows it writes are removed afterwards.
 */
@SpringBootTest(properties = "app.edition=ODTU")
class IskurChannelSyncIntegrationTest {

	@Autowired
	private ApplicationEventPublisher events;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private ProgramRepository programRepository;
	@Autowired
	private IskurUnitRepository unitRepository;
	@Autowired
	private IskurTeamRepository teamRepository;
	@Autowired
	private IskurPlacementRepository placementRepository;
	@Autowired
	private IskurChannelRepository channelRepository;
	@Autowired
	private IskurChannelMemberRepository memberRepository;

	private TransactionTemplate tx;
	private Program program;
	private AppUser coordinator;
	private AppUser intern;
	private IskurTeam team;

	@BeforeEach
	void setUp() {
		tx = new TransactionTemplate(transactionManager);
		coordinator = user("Selin Er", UserRole.COORDINATOR);
		program = new Program();
		program.setId(UUID.randomUUID());
		program.setName("İŞKUR Sync");
		program.setCreatedBy(coordinator.getId());
		program.setCreatedAt(Instant.now());
		program.setStatus(ProgramStatus.ACTIVE);
		program.setProgramType(ProgramType.PEER_MENTORING);
		programRepository.saveAndFlush(program);
		intern = user("Ayşe Yıldız", UserRole.MENTEE);
		IskurUnit unit = unitRepository.saveAndFlush(new IskurUnit(program.getId(), "Birim", 0, coordinator.getId(),
				Instant.now()));
		team = teamRepository.saveAndFlush(new IskurTeam(unit, "Ekip", 0, Instant.now()));
	}

	@AfterEach
	void tearDown() {
		List<UUID> channelIds = channelRepository.findByProgramId(program.getId()).stream()
				.map(IskurChannel::getId)
				.toList();
		memberRepository.deleteAll(memberRepository.findByChannelIdIn(channelIds));
		channelRepository.deleteAllById(channelIds);
		placementRepository.deleteAll(placementRepository.findByProgramId(program.getId()));
		teamRepository.delete(team);
		unitRepository.deleteAll(unitRepository.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()));
		programRepository.delete(program);
		userRepository.deleteAll(List.of(intern, coordinator));
	}

	@Test
	void opensTheChannelsAfterTheFinalPlacementIsCommitted() {
		tx.executeWithoutResult(status -> {
			IskurPlacement placement = placementRepository.save(finalPlacement());
			events.publishEvent(new IskurPlacementChangedEvent(program.getId(), intern.getId(), null,
					placement.getTeamId(), placement.getStatus(), placement.getTeamRole(), null));
		});

		assertThat(channelRepository.findByProgramId(program.getId())).extracting(IskurChannel::getKind)
				.containsExactlyInAnyOrder(IskurChannelKind.ANNOUNCEMENTS, IskurChannelKind.UNIT,
						IskurChannelKind.TEAM);
		assertThat(memberRepository.findByUserIdAndLeftAtIsNull(intern.getId())).hasSize(3);
	}

	@Test
	void aRolledBackChangeOpensNothing() {
		tx.executeWithoutResult(status -> {
			IskurPlacement placement = placementRepository.save(finalPlacement());
			events.publishEvent(new IskurPlacementChangedEvent(program.getId(), intern.getId(), null,
					placement.getTeamId(), placement.getStatus(), placement.getTeamRole(), null));
			status.setRollbackOnly();
		});

		assertThat(channelRepository.findByProgramId(program.getId())).isEmpty();
	}

	private IskurPlacement finalPlacement() {
		IskurPlacement placement = new IskurPlacement(program.getId(), intern.getId(), coordinator.getId(),
				Instant.now());
		placement.setTeamId(team.getId());
		placement.setTeamRole(IskurTeamRole.MEMBER);
		placement.finalizeBy(coordinator.getId(), Instant.now());
		return placement;
	}

	private AppUser user(String name, UserRole role) {
		AppUser user = new AppUser();
		user.setFullName(name);
		user.setEmail(UUID.randomUUID() + "@example.test");
		user.setRole(role);
		return userRepository.saveAndFlush(user);
	}
}
