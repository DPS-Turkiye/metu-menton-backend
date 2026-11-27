package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.InstitutionDataSource;
import com.dps.teknokent.domain.IskurPlacementStatus;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.PermissionSource;
import com.dps.teknokent.domain.ProgramMentorInvitedBy;
import com.dps.teknokent.domain.ProgramMentorStatus;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.IskurInternEmail;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramCoordinator;
import com.dps.teknokent.entity.ProgramMentor;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.IskurInternEmailRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramMentorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.security.AppUserPrincipal;
import com.dps.teknokent.service.IskurPlacementChangedEvent;

/** The coordinator's İŞKUR placement screen, through the ODTÜ edition's real security chain and an H2 database. */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class IskurWorkspaceIntegrationTest {

	private static final String BASE = "/api/coordinator/programs/{programId}/iskur";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ApplicationEvents events;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private IskurInternEmailRepository internEmailRepository;
	@Autowired
	private ProgramRepository programRepository;
	@Autowired
	private ProgramCoordinatorRepository programCoordinatorRepository;
	@Autowired
	private ProgramMentorRepository programMentorRepository;
	@Autowired
	private InstitutionPersonRepository institutionPersonRepository;
	@Autowired
	private IskurUnitRepository unitRepository;
	@Autowired
	private IskurTeamRepository teamRepository;
	@Autowired
	private IskurPlacementRepository placementRepository;

	private AppUser admin;
	private AppUser coordinator;
	private AppUser coCoordinator;
	private AppUser otherCoordinator;
	private Program program;
	private AppUser ayse;
	private AppUser burak;
	private AppUser ceren;
	private AppUser deniz;
	private AppUser peerMentor;

	@BeforeEach
	void setUp() {
		admin = user("Yönetici Test", UserRole.ADMIN, null);
		coordinator = user("Selin Er", UserRole.COORDINATOR, null);
		coCoordinator = user("Kaan Yurt", UserRole.COORDINATOR, null);
		otherCoordinator = user("Başka Koordinatör", UserRole.COORDINATOR, null);
		program = program("İŞKUR Güz 2026", ProgramType.PEER_MENTORING, coordinator);
		coordinatorOf(program, coCoordinator);

		ayse = user("Ayşe Yıldız", UserRole.MENTEE, program);
		burak = user("Burak Tan", UserRole.MENTEE, program);
		ceren = user("Ceren Su", UserRole.MENTEE, program);
		deniz = user("Deniz Arslan", UserRole.MENTEE, program);
		record(ayse, "Endüstri Mühendisliği", true);
		peerMentor = user("Zeynep Kaya", UserRole.MENTOR, null);
		mentorOf(program, peerMentor, ProgramMentorStatus.ACCEPTED);

		Program elsewhere = program("Başka Program", ProgramType.PEER_MENTORING, otherCoordinator);
		user("Dış Öğrenci", UserRole.MENTEE, elsewhere);
		mentorOf(program, user("Bekleyen Mentor", UserRole.MENTOR, null), ProgramMentorStatus.PENDING);
		AppUser pending = user("Onaysız Öğrenci", UserRole.MENTEE, program);
		pending.setStatus(UserAccountStatus.PENDING);
		userRepository.saveAndFlush(pending);
	}

	@Test
	void listsTheProgramsApprovedStudentsAndAcceptedPeerMentorsAsThePool() throws Exception {
		workspace(coordinator)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.programId").value(program.getId().toString()))
				.andExpect(jsonPath("$.units").isEmpty())
				.andExpect(jsonPath("$.pool[*].fullName").value(Matchers.contains(
						"Ayşe Yıldız", "Burak Tan", "Ceren Su", "Deniz Arslan", "Zeynep Kaya")))
				.andExpect(jsonPath("$.pool[0].department").value("Endüstri Mühendisliği"))
				.andExpect(jsonPath("$.pool[0].iskurParticipant").value(true))
				.andExpect(jsonPath("$.pool[1].department").doesNotExist())
				.andExpect(jsonPath("$.pool[1].iskurParticipant").value(false))
				.andExpect(jsonPath("$.pool[4].peerMentor").value(true))
				.andExpect(jsonPath("$.coordinators[*].fullName").value(Matchers.contains("Selin Er", "Kaan Yurt")))
				.andExpect(jsonPath("$.counts.interns").value(5))
				.andExpect(jsonPath("$.counts.placed").value(0));
	}

	@Test
	void marksBadgeHoldersAsPeerMentorsAndEveryOtherInternAsBadgePending() throws Exception {
		burak.grant(UserRole.MENTOR, PermissionSource.BADGE, Instant.now());
		userRepository.saveAndFlush(burak);

		workspace(coordinator)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pool[*].fullName").value(Matchers.contains(
						"Ayşe Yıldız", "Burak Tan", "Ceren Su", "Deniz Arslan", "Zeynep Kaya")))
				// No badge yet: still in the pool, placed like anyone else ("Rozet bekleniyor").
				.andExpect(jsonPath("$.pool[0].peerMentor").value(false))
				// The badge grants MENTOR; no accepted mentor row is needed any more.
				.andExpect(jsonPath("$.pool[1].peerMentor").value(true))
				// An accepted peer mentor from before badges still counts.
				.andExpect(jsonPath("$.pool[4].peerMentor").value(true));

		IskurTeam team = team(unit("Kreatif ve Dijital Üretim"), "Medya ve Kreatif Üretim");
		place(coordinator, ayse, team, IskurTeamRole.MEMBER, null, 2, 180).andExpect(status().isOk());
		workspace(coordinator)
				.andExpect(jsonPath("$.units[*].teams[*].people[?(@.fullName == 'Ayşe Yıldız')].peerMentor")
						.value(Matchers.contains(false)));
	}

	@Test
	void createsTheOrgChartsFourUnitsAndEightTeamsOnlyOnce() throws Exception {
		send(post(BASE + "/structure/default", program.getId()), actor(coordinator))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.units.length()").value(4))
				.andExpect(jsonPath("$.units[0].name").value("Kreatif ve Dijital Üretim"))
				.andExpect(jsonPath("$.units[0].teams[*].name").value(Matchers.contains(
						"Medya ve Kreatif Üretim", "Dijital Dönüşüm ve Teknoloji")))
				.andExpect(jsonPath("$.units[3].teams[1].name").value("Etkinlik ve Topluluk Yönetimi"))
				.andExpect(jsonPath("$.units[0].teams[0].warnings").value(Matchers.contains("TEAM_SIZE", "NO_LEADER")));

		send(post(BASE + "/structure/default", program.getId()), actor(coordinator))
				.andExpect(status().isConflict());
		assertThat(teamRepository.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId())).hasSize(8);
	}

	@Test
	void placesLeadersAndTheirSubgroupsWithTheWeeklySplit() throws Exception {
		IskurTeam team = team(unit("Kreatif ve Dijital Üretim"), "Medya ve Kreatif Üretim");

		place(coordinator, ayse, team, IskurTeamRole.LEADER, null, 3, null).andExpect(status().isOk());
		place(coordinator, peerMentor, team, IskurTeamRole.MEMBER, ayse, 2, 0).andExpect(status().isOk());
		place(coordinator, burak, team, IskurTeamRole.MEMBER, ayse, 1, 90).andExpect(status().isOk());
		place(coordinator, ceren, team, IskurTeamRole.MEMBER, null, null, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.units[0].teams[0].people[*].fullName").value(Matchers.contains(
						"Ayşe Yıldız", "Burak Tan", "Zeynep Kaya", "Ceren Su")))
				.andExpect(jsonPath("$.units[0].teams[0].people[0].role").value("LEADER"))
				.andExpect(jsonPath("$.units[0].teams[0].people[0].status").value("PROVISIONAL"))
				.andExpect(jsonPath("$.units[0].teams[0].people[0].split.totalMinutes").value(1350))
				.andExpect(jsonPath("$.units[0].teams[0].people[0].split.mentoringMinutes").value(360))
				.andExpect(jsonPath("$.units[0].teams[0].people[0].split.teamMinutes").value(990))
				.andExpect(jsonPath("$.units[0].teams[0].people[0].split.mentoringIsDefault").value(true))
				.andExpect(jsonPath("$.units[0].teams[0].people[0].warnings").isEmpty())
				.andExpect(jsonPath("$.units[0].teams[0].people[1].leaderId").value(ayse.getId().toString()))
				.andExpect(jsonPath("$.units[0].teams[0].people[1].split.mentoringMinutes").value(90))
				.andExpect(jsonPath("$.units[0].teams[0].people[2].split.mentoringMinutes").value(0))
				.andExpect(jsonPath("$.units[0].teams[0].people[2].split.teamMinutes").value(900))
				.andExpect(jsonPath("$.units[0].teams[0].people[2].split.mentoringIsDefault").value(false))
				.andExpect(jsonPath("$.units[0].teams[0].people[3].split").doesNotExist())
				.andExpect(jsonPath("$.units[0].teams[0].people[3].warnings")
						.value(Matchers.contains("DAYS_MISSING", "NO_LEADER_ASSIGNED")))
				.andExpect(jsonPath("$.units[0].teams[0].warnings").value(Matchers.contains("TEAM_SIZE")))
				.andExpect(jsonPath("$.pool[*].fullName").value(Matchers.contains("Deniz Arslan")))
				.andExpect(jsonPath("$.counts.placed").value(4))
				.andExpect(jsonPath("$.counts.leaders").value(1));

		assertThat(events.stream(IskurPlacementChangedEvent.class)).hasSize(4)
				.allSatisfy(event -> assertThat(event.teamId()).isEqualTo(team.getId()));
	}

	@Test
	void warnsAboutALeaderWithOneDayAndAnOversizedSubgroup() throws Exception {
		IskurTeam team = team(unit("Etki"), "Etkinlik");
		place(coordinator, ayse, team, IskurTeamRole.LEADER, null, 1, null).andExpect(status().isOk());
		for (AppUser member : List.of(burak, ceren, deniz, peerMentor, user("Ece Kurt", UserRole.MENTEE, program))) {
			place(coordinator, member, team, IskurTeamRole.MEMBER, ayse, 2, null).andExpect(status().isOk());
		}

		workspace(coordinator)
				.andExpect(jsonPath("$.units[0].teams[0].warnings").value(Matchers.contains("TEAM_SIZE",
						"SUBGROUP_SIZE")))
				.andExpect(jsonPath("$.units[0].teams[0].people[0].warnings")
						.value(Matchers.contains("LEADER_FEW_DAYS")));
	}

	@Test
	void aTeamHasAtMostTwoLeaders() throws Exception {
		IskurTeam team = team(unit("Etki"), "Etkinlik");
		place(coordinator, ayse, team, IskurTeamRole.LEADER, null, 3, null).andExpect(status().isOk());
		place(coordinator, burak, team, IskurTeamRole.LEADER, null, 2, null).andExpect(status().isOk());
		// Saving the same leader again is not a third one.
		place(coordinator, burak, team, IskurTeamRole.LEADER, null, 3, null).andExpect(status().isOk());

		place(coordinator, ceren, team, IskurTeamRole.LEADER, null, 3, null).andExpect(status().isConflict());
		assertThat(placementRepository.findByProgramIdAndUserId(program.getId(), ceren.getId())).isEmpty();
	}

	@Test
	void aMembersLeaderLeadsTheSameTeamAndALeaderHasNone() throws Exception {
		IskurUnit unit = unit("Etki");
		IskurTeam first = team(unit, "Etkinlik");
		IskurTeam second = team(unit, "Sürdürülebilirlik");
		place(coordinator, ayse, first, IskurTeamRole.LEADER, null, 3, null).andExpect(status().isOk());
		place(coordinator, burak, first, IskurTeamRole.MEMBER, null, 3, null).andExpect(status().isOk());

		place(coordinator, ceren, second, IskurTeamRole.MEMBER, ayse, 3, null).andExpect(status().isBadRequest());
		place(coordinator, ceren, first, IskurTeamRole.MEMBER, burak, 3, null).andExpect(status().isBadRequest());
		place(coordinator, ceren, first, IskurTeamRole.LEADER, ayse, 3, null).andExpect(status().isBadRequest());
	}

	@Test
	void mentoringTimeNeedsDaysHalfHourStepsAndFitsTheWeek() throws Exception {
		IskurTeam team = team(unit("Etki"), "Etkinlik");

		place(coordinator, ayse, team, IskurTeamRole.MEMBER, null, null, 60).andExpect(status().isBadRequest());
		place(coordinator, ayse, team, IskurTeamRole.MEMBER, null, 2, 45).andExpect(status().isBadRequest());
		place(coordinator, ayse, team, IskurTeamRole.MEMBER, null, 1, 480).andExpect(status().isBadRequest());
		place(coordinator, ayse, team, IskurTeamRole.MEMBER, null, 4, null).andExpect(status().isBadRequest());
		place(coordinator, ayse, team, IskurTeamRole.MEMBER, null, 1, 450).andExpect(status().isOk());
	}

	@Test
	void aLeaderWhoStopsLeadingLeavesTheirSubgroupWithoutALeader() throws Exception {
		IskurUnit unit = unit("Etki");
		IskurTeam first = team(unit, "Etkinlik");
		IskurTeam second = team(unit, "Sürdürülebilirlik");
		place(coordinator, ayse, first, IskurTeamRole.LEADER, null, 3, null).andExpect(status().isOk());
		place(coordinator, burak, first, IskurTeamRole.MEMBER, ayse, 3, null).andExpect(status().isOk());
		place(coordinator, ceren, first, IskurTeamRole.LEADER, null, 3, null).andExpect(status().isOk());
		place(coordinator, deniz, first, IskurTeamRole.MEMBER, ceren, 3, null).andExpect(status().isOk());

		place(coordinator, ayse, first, IskurTeamRole.MEMBER, ceren, 3, null).andExpect(status().isOk());
		assertThat(leaderOf(burak)).isNull();
		assertThat(leaderOf(ayse)).isEqualTo(ceren.getId());
		assertThat(events.stream(IskurPlacementChangedEvent.class)
				.filter(event -> event.userId().equals(burak.getId()) && event.leaderId() == null))
				.singleElement()
				.satisfies(event -> assertThat(event.previousTeamId()).isEqualTo(first.getId()));

		place(coordinator, ceren, second, IskurTeamRole.LEADER, null, 3, null).andExpect(status().isOk());
		assertThat(leaderOf(ayse)).isNull();
		assertThat(leaderOf(deniz)).isNull();

		place(coordinator, burak, second, IskurTeamRole.MEMBER, ceren, 3, null).andExpect(status().isOk());
		send(delete(BASE + "/placements/{userId}", program.getId(), ceren.getId())
				.param("actorId", coordinator.getId().toString()), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pool[*].fullName").value(Matchers.contains("Ceren Su", "Zeynep Kaya")));
		assertThat(leaderOf(burak)).isNull();

		IskurPlacementChangedEvent removal = events.stream(IskurPlacementChangedEvent.class)
				.filter(event -> event.userId().equals(ceren.getId()) && event.teamId() == null)
				.findFirst()
				.orElseThrow();
		assertThat(removal.previousTeamId()).isEqualTo(second.getId());
		assertThat(removal.status()).isNull();
	}

	@Test
	void unplacingAMemberKeepsTheRestAndUnplacingNobodyIsNotFound() throws Exception {
		IskurTeam team = team(unit("Etki"), "Etkinlik");
		place(coordinator, ayse, team, IskurTeamRole.MEMBER, null, 3, null).andExpect(status().isOk());

		send(delete(BASE + "/placements/{userId}", program.getId(), ayse.getId())
				.param("actorId", coordinator.getId().toString()), null).andExpect(status().isOk());
		send(delete(BASE + "/placements/{userId}", program.getId(), ayse.getId())
				.param("actorId", coordinator.getId().toString()), null).andExpect(status().isNotFound());
	}

	@Test
	void onlyThePeopleOfTheProgramArePlaced() throws Exception {
		IskurTeam team = team(unit("Etki"), "Etkinlik");
		AppUser outsider = user("Dışarıdan Biri", UserRole.MENTEE, null);

		place(coordinator, outsider, team, IskurTeamRole.MEMBER, null, 3, null).andExpect(status().isBadRequest());
		place(coordinator, ayse, teamOfAnotherProgram(), IskurTeamRole.MEMBER, null, 3, null)
				.andExpect(status().isNotFound());
	}

	@Test
	void everyApprovedStudentAndAcceptedMentorIsInThePoolWithoutAList() throws Exception {
		AppUser plain = userRepository.saveAndFlush(account("Liste Dışı Öğrenci", program));
		AppUser unlistedMentor = user("Listesiz Mentor", UserRole.MENTOR, null);
		ProgramMentor membership = new ProgramMentor();
		membership.setId(UUID.randomUUID());
		membership.setProgramId(program.getId());
		membership.setMentorId(unlistedMentor.getId());
		membership.setStatus(ProgramMentorStatus.ACCEPTED);
		membership.setInvitedBy(ProgramMentorInvitedBy.COORDINATOR);
		membership.setCreatedAt(Instant.now());
		programMentorRepository.saveAndFlush(membership);
		IskurTeam team = team(unit("Etki"), "Etkinlik");

		workspace(coordinator)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pool[*].fullName").value(Matchers.contains("Ayşe Yıldız", "Burak Tan",
						"Ceren Su", "Deniz Arslan", "Liste Dışı Öğrenci", "Listesiz Mentor", "Zeynep Kaya")))
				.andExpect(jsonPath("$.counts.interns").value(7));
		place(coordinator, plain, team, IskurTeamRole.MEMBER, null, 3, null).andExpect(status().isOk());
		place(coordinator, unlistedMentor, team, IskurTeamRole.MEMBER, null, 3, null).andExpect(status().isOk());
		assertThat(placementRepository.findByProgramId(program.getId())).hasSize(2);
	}

	@Test
	void finalizesOneTeamOneUnitOrTheWholeProgram() throws Exception {
		IskurUnit creative = unit("Kreatif");
		IskurTeam media = team(creative, "Medya");
		IskurTeam digital = team(creative, "Dijital");
		IskurTeam impact = team(unit("Etki"), "Etkinlik");
		place(coordinator, ayse, media, IskurTeamRole.LEADER, null, 3, null).andExpect(status().isOk());
		place(coordinator, burak, digital, IskurTeamRole.MEMBER, null, 3, null).andExpect(status().isOk());
		place(coordinator, ceren, impact, IskurTeamRole.MEMBER, null, 3, null).andExpect(status().isOk());

		finalize("{\"actorId\":\"%s\",\"teamId\":\"%s\"}".formatted(coordinator.getId(), media.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.counts.finalized").value(1));
		assertThat(statusOf(ayse)).isEqualTo(IskurPlacementStatus.FINAL);
		assertThat(statusOf(burak)).isEqualTo(IskurPlacementStatus.PROVISIONAL);

		finalize("{\"actorId\":\"%s\",\"unitId\":\"%s\"}".formatted(coordinator.getId(), creative.getId()))
				.andExpect(jsonPath("$.counts.finalized").value(2));
		assertThat(statusOf(ceren)).isEqualTo(IskurPlacementStatus.PROVISIONAL);

		finalize("{\"actorId\":\"%s\"}".formatted(coCoordinator.getId()))
				.andExpect(jsonPath("$.counts.finalized").value(3))
				.andExpect(jsonPath("$.units[1].teams[0].people[0].finalizedAt").isNotEmpty());
		IskurPlacement finalized = placementRepository.findByProgramIdAndUserId(program.getId(), ceren.getId())
				.orElseThrow();
		assertThat(finalized.getFinalizedBy()).isEqualTo(coCoordinator.getId());
		assertThat(events.stream(IskurPlacementChangedEvent.class)
				.filter(event -> event.status() == IskurPlacementStatus.FINAL)).hasSize(3);

		// A move keeps a final placement final.
		place(coordinator, ceren, media, IskurTeamRole.MEMBER, ayse, 3, null).andExpect(status().isOk());
		assertThat(statusOf(ceren)).isEqualTo(IskurPlacementStatus.FINAL);

		finalize("{\"actorId\":\"%s\",\"teamId\":\"%s\",\"unitId\":\"%s\"}".formatted(coordinator.getId(),
				media.getId(), creative.getId())).andExpect(status().isBadRequest());
	}

	@Test
	void unitsAndTeamsAreNamedOnceAndGoOnlyWhenEmpty() throws Exception {
		send(post(BASE + "/units", program.getId()),
				"{\"actorId\":\"%s\",\"name\":\"  Etki ve Organizasyon \",\"coordinatorId\":\"%s\"}"
						.formatted(coordinator.getId(), coCoordinator.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.units[0].name").value("Etki ve Organizasyon"))
				.andExpect(jsonPath("$.units[0].coordinator.fullName").value("Kaan Yurt"));
		IskurUnit unit = unitRepository.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()).getFirst();

		send(post(BASE + "/units", program.getId()),
				"{\"actorId\":\"%s\",\"name\":\"ETKİ VE ORGANİZASYON\"}".formatted(coordinator.getId()))
				.andExpect(status().isConflict());
		send(post(BASE + "/units", program.getId()), "{\"actorId\":\"%s\",\"name\":\"Kampüs\",\"coordinatorId\":\"%s\"}"
				.formatted(coordinator.getId(), otherCoordinator.getId()))
				.andExpect(status().isBadRequest());
		send(post(BASE + "/units", program.getId()), "{\"actorId\":\"%s\",\"name\":\" \"}"
				.formatted(coordinator.getId()))
				.andExpect(status().isBadRequest());
		send(post(BASE + "/units", program.getId()), "{\"actorId\":\"%s\",\"name\":\"Kampüs\"}"
				.formatted(coordinator.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.units[1].sortOrder").value(1))
				.andExpect(jsonPath("$.units[1].coordinator").doesNotExist());

		send(put(BASE + "/units/{unitId}", program.getId(), unit.getId()),
				"{\"actorId\":\"%s\",\"name\":\"Etki\"}".formatted(coordinator.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.units[0].name").value("Etki"))
				.andExpect(jsonPath("$.units[0].coordinator").doesNotExist());
		send(put(BASE + "/units/{unitId}", program.getId(), unit.getId()),
				"{\"actorId\":\"%s\",\"name\":\"Kampüs\"}".formatted(coordinator.getId()))
				.andExpect(status().isConflict());
		send(put(BASE + "/units/{unitId}", program.getId(), UUID.randomUUID()),
				"{\"actorId\":\"%s\",\"name\":\"Yok\"}".formatted(coordinator.getId()))
				.andExpect(status().isNotFound());

		send(post(BASE + "/units/{unitId}/teams", program.getId(), unit.getId()),
				"{\"actorId\":\"%s\",\"name\":\"Etkinlik\"}".formatted(coordinator.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.units[0].teams[0].name").value("Etkinlik"));
		send(post(BASE + "/units/{unitId}/teams", program.getId(), unit.getId()),
				"{\"actorId\":\"%s\",\"name\":\"Sosyal Etki\"}".formatted(coordinator.getId()))
				.andExpect(jsonPath("$.units[0].teams[1].sortOrder").value(1));
		send(post(BASE + "/units/{unitId}/teams", program.getId(), unit.getId()),
				"{\"actorId\":\"%s\",\"name\":\"etkinlik\"}".formatted(coordinator.getId()))
				.andExpect(status().isConflict());
		List<IskurTeam> teams = teamRepository.findByUnitId(unit.getId());
		IskurTeam eventsTeam = teams.stream().filter(team -> team.getName().equals("Etkinlik")).findFirst()
				.orElseThrow();
		IskurTeam social = teams.stream().filter(team -> team.getName().equals("Sosyal Etki")).findFirst()
				.orElseThrow();
		send(put(BASE + "/teams/{teamId}", program.getId(), eventsTeam.getId()),
				"{\"actorId\":\"%s\",\"name\":\"Sosyal Etki\"}".formatted(coordinator.getId()))
				.andExpect(status().isConflict());
		send(put(BASE + "/teams/{teamId}", program.getId(), eventsTeam.getId()),
				"{\"actorId\":\"%s\",\"name\":\"Etkinlik Yönetimi\"}".formatted(coordinator.getId()))
				.andExpect(jsonPath("$.units[0].teams[0].name").value("Etkinlik Yönetimi"));

		place(coordinator, ayse, eventsTeam, IskurTeamRole.MEMBER, null, 3, null).andExpect(status().isOk());
		send(delete(BASE + "/teams/{teamId}", program.getId(), eventsTeam.getId())
				.param("actorId", coordinator.getId().toString()), null).andExpect(status().isConflict());
		send(delete(BASE + "/units/{unitId}", program.getId(), unit.getId())
				.param("actorId", coordinator.getId().toString()), null).andExpect(status().isConflict());
		send(delete(BASE + "/teams/{teamId}", program.getId(), social.getId())
				.param("actorId", coordinator.getId().toString()), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.units[0].teams.length()").value(1));

		send(delete(BASE + "/placements/{userId}", program.getId(), ayse.getId())
				.param("actorId", coordinator.getId().toString()), null).andExpect(status().isOk());
		send(delete(BASE + "/units/{unitId}", program.getId(), unit.getId())
				.param("actorId", coordinator.getId().toString()), null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.units[*].name").value(Matchers.contains("Kampüs")));
		assertThat(teamRepository.findByUnitId(unit.getId())).isEmpty();

		IskurUnit empty = unit("Boş Birim");
		send(delete(BASE + "/units/{unitId}", program.getId(), empty.getId())
				.param("actorId", coordinator.getId().toString()), null).andExpect(status().isOk());
	}

	@Test
	void aUnitCoordinatorWhoNoLongerCoordinatesIsNotShown() throws Exception {
		IskurUnit unit = unit("Etki");
		unit.setCoordinatorId(otherCoordinator.getId());
		unitRepository.saveAndFlush(unit);

		workspace(coordinator).andExpect(jsonPath("$.units[0].coordinator").doesNotExist());
	}

	@Test
	void theAdminManagesItTooButNoOtherProgramsCoordinator() throws Exception {
		workspace(admin).andExpect(status().isOk());
		workspace(otherCoordinator).andExpect(status().isBadRequest());
		send(post(BASE + "/structure/default", program.getId()), actor(otherCoordinator))
				.andExpect(status().isBadRequest());
		assertThat(unitRepository.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId())).isEmpty();
	}

	@Test
	void anInternOrLeaderCannotReachThePlacementScreen() throws Exception {
		IskurTeam team = team(unit("Etki"), "Etkinlik");
		place(coordinator, ayse, team, IskurTeamRole.LEADER, null, 3, null).andExpect(status().isOk());

		// Named as the actor by the proxy: not a coordinator.
		workspace(ayse).andExpect(status().isBadRequest());
		place(ayse, burak, team, IskurTeamRole.MEMBER, ayse, 3, null).andExpect(status().isBadRequest());
		// Signed in as themselves while naming the coordinator: the session wins, so they are still refused.
		mockMvc.perform(put(BASE + "/placements/{userId}", program.getId(), burak.getId())
				.with(authentication(signedIn(ayse)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"%s\",\"teamId\":\"%s\",\"role\":\"MEMBER\"}"
						.formatted(coordinator.getId(), team.getId())))
				.andExpect(status().isBadRequest());
		// Anonymous.
		mockMvc.perform(get(BASE + "/workspace", program.getId())
				.param("actorId", coordinator.getId().toString()))
				.andExpect(status().isUnauthorized());
		assertThat(placementRepository.findByProgramIdAndUserId(program.getId(), burak.getId())).isEmpty();
	}

	@Test
	void onlyPeerProgramsHaveAWorkspaceAndAnArchivedOneIsReadOnly() throws Exception {
		Program startups = program("Kuluçka", ProgramType.STARTUP_MENTORING, coordinator);
		mockMvc.perform(get(BASE + "/workspace", startups.getId())
				.param("actorId", coordinator.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isBadRequest());

		IskurTeam team = team(unit("Etki"), "Etkinlik");
		program.setStatus(ProgramStatus.COMPLETED);
		programRepository.saveAndFlush(program);
		workspace(coordinator).andExpect(status().isOk());
		place(coordinator, ayse, team, IskurTeamRole.MEMBER, null, 3, null).andExpect(status().isBadRequest());
	}

	private ResultActions workspace(AppUser actor) throws Exception {
		return mockMvc.perform(get(BASE + "/workspace", program.getId())
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions place(AppUser actor, AppUser who, IskurTeam team, IskurTeamRole role, AppUser leader,
			Integer days, Integer mentoring) throws Exception {
		return send(put(BASE + "/placements/{userId}", program.getId(), who.getId()),
				"{\"actorId\":\"%s\",\"teamId\":\"%s\",\"role\":\"%s\",\"leaderId\":%s,\"weeklyDays\":%s,\"mentoringMinutes\":%s}"
						.formatted(actor.getId(), team.getId(), role,
								leader == null ? "null" : "\"" + leader.getId() + "\"", days, mentoring));
	}

	private ResultActions finalize(String body) throws Exception {
		return send(post(BASE + "/placements/finalize", program.getId()), body);
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
		MockHttpServletRequestBuilder withAuth = request.with(httpBasic("test", "test"));
		if (body != null) {
			withAuth = withAuth.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(withAuth);
	}

	private static String actor(AppUser actor) {
		return "{\"actorId\":\"" + actor.getId() + "\"}";
	}

	private UUID leaderOf(AppUser member) {
		return placementRepository.findByProgramIdAndUserId(program.getId(), member.getId()).orElseThrow()
				.getLeaderId();
	}

	private IskurPlacementStatus statusOf(AppUser member) {
		return placementRepository.findByProgramIdAndUserId(program.getId(), member.getId()).orElseThrow()
				.getStatus();
	}

	private IskurUnit unit(String name) {
		return unitRepository.saveAndFlush(new IskurUnit(program.getId(), name, 0, coordinator.getId(),
				Instant.now()));
	}

	private IskurTeam team(IskurUnit unit, String name) {
		return teamRepository.saveAndFlush(new IskurTeam(unit, name, 0, Instant.now()));
	}

	private IskurTeam teamOfAnotherProgram() {
		Program other = program("Üçüncü Program", ProgramType.PEER_MENTORING, coordinator);
		IskurUnit unit = unitRepository.saveAndFlush(new IskurUnit(other.getId(), "Birim", 0, coordinator.getId(),
				Instant.now()));
		return teamRepository.saveAndFlush(new IskurTeam(unit, "Ekip", 0, Instant.now()));
	}

	private AppUser user(String name, UserRole role, Program joined) {
		AppUser user = new AppUser();
		user.setFullName(name);
		user.setEmail(UUID.randomUUID() + "@example.test");
		user.setRole(role);
		if (joined != null) {
			user.setProgramId(joined.getId());
		}
		AppUser saved = userRepository.saveAndFlush(user);
		if (joined != null && role == UserRole.MENTEE) {
			internOf(joined, saved);
		}
		return saved;
	}

	/** A student of the program whose address is not on the intern list. */
	private static AppUser account(String name, Program joined) {
		AppUser user = new AppUser();
		user.setFullName(name);
		user.setEmail(UUID.randomUUID() + "@example.test");
		user.setRole(UserRole.MENTEE);
		user.setProgramId(joined.getId());
		return user;
	}

	/** Puts the person's address on the program's İŞKUR intern list, as the coordinator would. */
	private void internOf(Program listing, AppUser person) {
		internEmailRepository.saveAndFlush(new IskurInternEmail(listing.getId(),
				person.getEmail().toLowerCase(Locale.ROOT), null, Instant.now()));
	}

	private void record(AppUser user, String department, boolean iskur) {
		InstitutionPerson older = new InstitutionPerson("odtu", UUID.randomUUID().toString());
		older.setUser(user);
		older.setFullName(user.getFullName());
		older.setDepartmentName("Eski Bölüm");
		older.setSource(InstitutionDataSource.DEV_STUB);
		older.setSyncedAt(Instant.now().minusSeconds(3600));
		institutionPersonRepository.saveAndFlush(older);
		InstitutionPerson latest = new InstitutionPerson("odtu", UUID.randomUUID().toString());
		latest.setUser(user);
		latest.setFullName(user.getFullName());
		latest.setDepartmentName(department);
		latest.setIskurParticipant(iskur);
		latest.setSource(InstitutionDataSource.DEV_STUB);
		latest.setSyncedAt(Instant.now());
		institutionPersonRepository.saveAndFlush(latest);
	}

	private Program program(String name, ProgramType type, AppUser creator) {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName(name);
		created.setCreatedBy(creator.getId());
		created.setCreatedAt(Instant.now());
		created.setStatus(ProgramStatus.ACTIVE);
		created.setProgramType(type);
		return programRepository.saveAndFlush(created);
	}

	private void coordinatorOf(Program coordinated, AppUser user) {
		ProgramCoordinator link = new ProgramCoordinator();
		link.setId(UUID.randomUUID());
		link.setProgramId(coordinated.getId());
		link.setUserId(user.getId());
		link.setAddedBy(coordinator.getId());
		link.setCreatedAt(Instant.now());
		programCoordinatorRepository.saveAndFlush(link);
	}

	private void mentorOf(Program pool, AppUser mentor, ProgramMentorStatus mentorStatus) {
		ProgramMentor membership = new ProgramMentor();
		membership.setId(UUID.randomUUID());
		membership.setProgramId(pool.getId());
		membership.setMentorId(mentor.getId());
		membership.setStatus(mentorStatus);
		membership.setInvitedBy(ProgramMentorInvitedBy.COORDINATOR);
		membership.setCreatedAt(Instant.now());
		programMentorRepository.saveAndFlush(membership);
		if (mentorStatus == ProgramMentorStatus.ACCEPTED) {
			internOf(pool, mentor);
		}
	}

	private static UsernamePasswordAuthenticationToken signedIn(AppUser user) {
		return new UsernamePasswordAuthenticationToken(new AppUserPrincipal(
				user.getId(), user.getEmail(), user.getRole(), user.getStatus(), "iskur-test-uid"), null, List.of());
	}
}
