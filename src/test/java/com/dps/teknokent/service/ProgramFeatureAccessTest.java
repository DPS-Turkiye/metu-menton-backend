package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinatorFeature;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.repository.ProgramCoordinatorFeatureRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;

class ProgramFeatureAccessTest {

	private static final UUID CREATOR = UUID.randomUUID();

	private final ProgramCoordinatorRepository coordinators = mock(ProgramCoordinatorRepository.class);
	private final ProgramCoordinatorFeatureRepository assignments = mock(ProgramCoordinatorFeatureRepository.class);
	private final DomainLookupService lookup = mock(DomainLookupService.class);
	private final ProgramFeatureAccess access = new ProgramFeatureAccess(coordinators, assignments, lookup);
	private final Program semester = program(ProgramType.SEMESTER, EnumSet.allOf(ProgramFeature.class));

	@Test
	void theAdminOpensEverythingTheProgramRuns() {
		Program peerOnly = program(ProgramType.SEMESTER, EnumSet.of(ProgramFeature.PEER_MENTORING));

		assertThat(access.of(user(UserRole.ADMIN), peerOnly)).containsExactly(ProgramFeature.PEER_MENTORING);
	}

	@Test
	void aSemesterCoordinatorOpensOnlyTheirAssignedFeaturesThatStillRun() {
		AppUser coordinator = coordinatorOf(semester);
		Program officeHoursOff = program(ProgramType.SEMESTER, EnumSet.of(ProgramFeature.PEER_MENTORING));
		officeHoursOff.setId(semester.getId());
		when(assignments.findByProgramIdAndUserId(semester.getId(), coordinator.getId())).thenReturn(List.of(
				new ProgramCoordinatorFeature(semester.getId(), coordinator.getId(), ProgramFeature.OFFICE_HOURS)));

		assertThat(access.of(coordinator, semester)).containsExactly(ProgramFeature.OFFICE_HOURS);
		assertThat(access.of(coordinator, officeHoursOff)).isEmpty();
	}

	@Test
	void theCreatorAndCoordinatorsOfOlderProgramsOpenEverything() {
		AppUser creator = user(UserRole.COORDINATOR);
		creator.setId(CREATOR);
		Program peer = program(ProgramType.PEER_MENTORING, Set.of());
		AppUser coordinator = coordinatorOf(peer);

		assertThat(access.of(creator, semester)).containsExactlyInAnyOrder(ProgramFeature.values());
		assertThat(access.of(coordinator, peer)).containsExactly(ProgramFeature.PEER_MENTORING, ProgramFeature.ISKUR);
		verify(assignments, never()).findByProgramIdAndUserId(peer.getId(), coordinator.getId());
	}

	@Test
	void someoneWhoDoesNotRunTheProgramOpensNothing() {
		assertThat(access.of(user(UserRole.COORDINATOR), semester)).isEmpty();
	}

	@Test
	void anUnassignedFeatureIsA403() {
		AppUser coordinator = coordinatorOf(semester);
		when(lookup.requireUser(coordinator.getId())).thenReturn(coordinator);

		assertThatThrownBy(() -> access.requireAssigned(coordinator.getId(), semester, ProgramFeature.ISKUR))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						refused -> assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
	}

	@Test
	void anAssignmentIsANonEmptyPartOfWhatTheSemesterRunsAndAllOfItByDefault() {
		Program peerOnly = program(ProgramType.SEMESTER, EnumSet.of(ProgramFeature.PEER_MENTORING));

		assertThat(access.checkAssignment(peerOnly, null)).containsExactly(ProgramFeature.PEER_MENTORING);
		assertThat(access.checkAssignment(semester, EnumSet.of(ProgramFeature.ISKUR)))
				.containsExactly(ProgramFeature.ISKUR);
		assertThatThrownBy(() -> access.checkAssignment(peerOnly, EnumSet.of(ProgramFeature.OFFICE_HOURS)))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> access.checkAssignment(peerOnly, Set.of()))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(access.checkAssignment(program(ProgramType.PEER_MENTORING, Set.of()), null)).isEmpty();
	}

	private AppUser coordinatorOf(Program program) {
		AppUser coordinator = user(UserRole.COORDINATOR);
		when(coordinators.existsByProgramIdAndUserId(program.getId(), coordinator.getId())).thenReturn(true);
		return coordinator;
	}

	private static AppUser user(UserRole role) {
		AppUser user = new AppUser();
		user.setId(UUID.randomUUID());
		user.setRole(role);
		return user;
	}

	private static Program program(ProgramType type, Set<ProgramFeature> features) {
		Program program = new Program();
		program.setId(UUID.randomUUID());
		program.setProgramType(type);
		program.setFeatures(features);
		program.setCreatedBy(CREATOR);
		return program;
	}
}
