package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dps.teknokent.domain.BadgeCode;
import com.dps.teknokent.domain.BadgeStatus;
import com.dps.teknokent.domain.PeerTopicCategory;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.responses.ProgramCountsResponse;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramPeerCategory;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.MentorshipRequestRepository;
import com.dps.teknokent.repository.OfficeHoursRequestRepository;
import com.dps.teknokent.repository.ProgramPeerCategoryRepository;
import com.dps.teknokent.repository.ProgramTally;
import com.dps.teknokent.repository.UserBadgeRepository;

@ExtendWith(MockitoExtension.class)
class ProgramParticipantCountsTest {

	private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

	@Mock
	private AppUserRepository users;
	@Mock
	private UserBadgeRepository awards;
	@Mock
	private MentorshipRequestRepository mentorshipRequests;
	@Mock
	private ProgramPeerCategoryRepository categories;
	@Mock
	private InstitutionPersonRepository institutionPeople;
	@Mock
	private OfficeHoursRequestRepository officeHoursRequests;

	private ProgramParticipantCounts counts;

	@BeforeEach
	void setUp() {
		counts = new ProgramParticipantCounts(users, awards, mentorshipRequests, categories, institutionPeople,
				officeHoursRequests, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void givesEachPeerProgramItsOwnNumbersAndZeroWhereNothingIsCounted() {
		Program busy = program(ProgramType.PEER_MENTORING);
		Program fresh = program(ProgramType.PEER_MENTORING);
		List<UUID> ids = List.of(busy.getId(), fresh.getId());
		when(users.countByProgramIdInAndPermissionAndStatus(ids, UserRole.MENTEE, UserAccountStatus.APPROVED))
				.thenReturn(List.of(tally(busy, 312)));
		when(awards.countByProgramIdInAndBadgeCodeAndStatus(ids, BadgeCode.PEER_MENTOR, BadgeStatus.ACTIVE))
				.thenReturn(List.of(tally(busy, 38), tally(fresh, 2)));
		when(mentorshipRequests.countByProgramIdInAndStatusIn(ids, PeerMentoringService.OPEN))
				.thenReturn(List.of(tally(busy, 14)));
		when(categories.findByProgramIdIn(ids)).thenReturn(List.of(
				category(busy, PeerTopicCategory.ACADEMIC_DEVELOPMENT),
				category(busy, PeerTopicCategory.CAREER_INTERNSHIP)));

		Map<UUID, ProgramCountsResponse> result = counts.of(List.of(busy, fresh));

		assertThat(result).containsOnly(
				Map.entry(busy.getId(),
						new ProgramCountsResponse(new ProgramCountsResponse.Peer(312, 38, 14, 2), null)),
				Map.entry(fresh.getId(), new ProgramCountsResponse(new ProgramCountsResponse.Peer(0, 2, 0, 0), null)));
		verifyNoInteractions(institutionPeople, officeHoursRequests);
	}

	@Test
	void givesEveryOfficeHoursProgramTheUniversityWideNumbers() {
		Program officeHours = program(ProgramType.OFFICE_HOURS);
		when(institutionPeople.countByAcademicTrueAndActiveTrue()).thenReturn(2410L);
		when(institutionPeople.countActiveAcademicsWithWeeklyHours()).thenReturn(1280L);
		when(officeHoursRequests.countByStatusInAndCreatedAtGreaterThanEqual(
				ProgramOfficeHoursService.AWAITING_DECISION, NOW.minus(ProgramOfficeHoursService.RECENT_WINDOW)))
				.thenReturn(86L);

		Map<UUID, ProgramCountsResponse> result = counts.of(List.of(officeHours));

		assertThat(result).containsOnly(Map.entry(officeHours.getId(),
				new ProgramCountsResponse(null, new ProgramCountsResponse.OfficeHours(2410, 1280, 86))));
		verifyNoInteractions(users, awards, mentorshipRequests, categories);
	}

	@Test
	void leavesStartupProgramsOutWithoutQuerying() {
		Map<UUID, ProgramCountsResponse> result = counts.of(List.of(program(ProgramType.STARTUP_MENTORING)));

		assertThat(result).isEmpty();
		verifyNoInteractions(users, awards, mentorshipRequests, categories, institutionPeople, officeHoursRequests);
	}

	@Test
	void queriesOnlyThePeerProgramsOfAMixedList() {
		Program peer = program(ProgramType.PEER_MENTORING);
		Program startup = program(ProgramType.STARTUP_MENTORING);
		when(users.countByProgramIdInAndPermissionAndStatus(any(), any(), any())).thenReturn(List.of());
		when(awards.countByProgramIdInAndBadgeCodeAndStatus(any(), any(), any())).thenReturn(List.of());
		when(mentorshipRequests.countByProgramIdInAndStatusIn(any(), any())).thenReturn(List.of());
		when(categories.findByProgramIdIn(List.of(peer.getId()))).thenReturn(List.of());

		assertThat(counts.of(List.of(peer, startup))).containsOnlyKeys(peer.getId());
	}

	private static Program program(ProgramType type) {
		Program program = new Program();
		program.setId(UUID.randomUUID());
		program.setProgramType(type);
		return program;
	}

	private static ProgramPeerCategory category(Program program, PeerTopicCategory topic) {
		ProgramPeerCategory category = new ProgramPeerCategory();
		category.setId(UUID.randomUUID());
		category.setProgramId(program.getId());
		category.setCategory(topic);
		return category;
	}

	private static ProgramTally tally(Program program, long total) {
		return new ProgramTally() {
			@Override
			public UUID getProgramId() {
				return program.getId();
			}

			@Override
			public Long getTotal() {
				return total;
			}
		};
	}
}
