package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinator;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.service.SemesterLaunchService;

/**
 * Two admins launch two drafts at the same moment, each in its own transaction. The semester rows are locked first, so
 * the launches run one after the other and the second sees the first one's work: exactly one semester runs
 * afterwards and the student is in it. Its own context and database (the extra properties), because these
 * transactions really commit.
 */
@SpringBootTest(properties = { "app.edition=ODTU", "test.context=semester-launch-race",
		"spring.datasource.url=jdbc:h2:mem:launch-race-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=true;"
				+ "DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=20000" })
class SemesterLaunchRaceIntegrationTest {

	@Autowired
	private SemesterLaunchService launches;
	@Autowired
	private AppUserRepository users;
	@Autowired
	private ProgramRepository programs;
	@Autowired
	private ProgramCoordinatorRepository coordinators;

	@Test
	void twoLaunchesAtOnceLeaveExactlyOneRunningSemester() throws Exception {
		AppUser admin = account(UserRole.ADMIN, null);
		AppUser coordinator = account(UserRole.COORDINATOR, null);
		Program fall = semester(admin, ProgramStatus.ACTIVE, coordinator);
		Program first = semester(admin, ProgramStatus.DRAFT, coordinator);
		Program second = semester(admin, ProgramStatus.DRAFT, coordinator);
		AppUser student = account(UserRole.MENTEE, fall);

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Future<?>> runs = new ArrayList<>();
		for (Program draft : List.of(first, second)) {
			runs.add(pool.submit(() -> {
				start.await();
				return launches.launch(admin.getId(), draft.getId());
			}));
		}
		start.countDown();
		for (Future<?> run : runs) {
			run.get(30, TimeUnit.SECONDS);
		}
		pool.shutdown();

		List<Program> running = programs.findByProgramType(ProgramType.SEMESTER).stream()
				.filter(program -> program.getStatus() == ProgramStatus.ACTIVE)
				.toList();
		assertThat(running).hasSize(1);
		assertThat(programs.findById(fall.getId()).orElseThrow().getStatus()).isEqualTo(ProgramStatus.COMPLETED);
		assertThat(users.findById(student.getId()).orElseThrow().getProgramId()).isEqualTo(running.get(0).getId());
	}

	private AppUser account(UserRole role, Program program) {
		AppUser account = new AppUser();
		account.setFullName(role.name());
		account.setEmail(UUID.randomUUID() + "@launch-race.test");
		account.setRole(role);
		account.setStatus(UserAccountStatus.APPROVED);
		account.setProgramId(program == null ? null : program.getId());
		return users.saveAndFlush(account);
	}

	private Program semester(AppUser admin, ProgramStatus status, AppUser coordinator) {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName("Dönem " + UUID.randomUUID());
		created.setProgramType(ProgramType.SEMESTER);
		created.setFeatures(EnumSet.allOf(ProgramFeature.class));
		created.setCreatedBy(admin.getId());
		created.setCreatedAt(Instant.now());
		created.setStatus(status);
		Program saved = programs.saveAndFlush(created);
		ProgramCoordinator link = new ProgramCoordinator();
		link.setId(UUID.randomUUID());
		link.setProgramId(saved.getId());
		link.setUserId(coordinator.getId());
		link.setAddedBy(admin.getId());
		link.setCreatedAt(Instant.now());
		coordinators.saveAndFlush(link);
		return saved;
	}
}
