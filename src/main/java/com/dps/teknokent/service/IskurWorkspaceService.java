package com.dps.teknokent.service;

import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.AppLanguage;
import com.dps.teknokent.domain.IskurPlacementStatus;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.IskurWarning;
import com.dps.teknokent.domain.IskurWeeklySplit;
import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramMentorStatus;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.dto.requests.IskurFinalizeRequest;
import com.dps.teknokent.dto.requests.IskurPlacementRequest;
import com.dps.teknokent.dto.requests.IskurTeamRequest;
import com.dps.teknokent.dto.requests.IskurUnitRequest;
import com.dps.teknokent.dto.responses.IskurWorkspaceResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramMentor;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.IskurOnboardingRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramMentorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;

/**
 * The İŞKUR workspace's structure (Figma 284:2, org chart 274:647): a peer program's coordinators set up units and
 * thematic teams, and place the program's students and peer mentors in them as leaders or members. Placements are
 * provisional through the trial weeks and become final when a coordinator confirms them; every change is announced
 * with {@link IskurPlacementChangedEvent} so the team channels can follow it.
 */
@Service
@RequiredArgsConstructor
public class IskurWorkspaceService {

	/** The org chart's team: two leaders and seven members. */
	static final int TEAM_SIZE = 9;
	static final int MAX_LEADERS = 2;
	static final int SUBGROUP_MAX = 4;
	/** The leadership application asks for at least two work days (onboarding 02). */
	static final int LEADER_MIN_DAYS = 2;
	/** Page 274:647: four units of two thematic teams each. */
	static final int DEFAULT_UNITS = 4;
	static final int DEFAULT_TEAMS_PER_UNIT = 2;

	private static final Locale TR = Locale.forLanguageTag("tr");

	private final CoordinatorProgramService coordinatorProgramService;
	private final IskurUnitRepository units;
	private final IskurTeamRepository teams;
	private final IskurPlacementRepository placements;
	private final IskurOnboardingRepository onboardings;
	private final AppUserRepository users;
	private final ProgramMentorRepository programMentors;
	private final ProgramRepository programs;
	private final InstitutionPersonRepository institutionPeople;
	private final ApplicationEventPublisher events;
	private final IskurSkillService skillService;
	private final IskurInternService interns;
	private final IskurTaskOutputFiles taskOutputs;

	/** The program's coordinators and the admin may read it. */
	@Transactional(readOnly = true)
	public IskurWorkspaceResponse workspace(UUID actorId, UUID programId) {
		return build(requireProgram(actorId, programId));
	}

	/** Creates the org chart's four units, eight teams and Figma 03's skills in a program with no structure yet. */
	@Transactional
	public IskurWorkspaceResponse createDefaultStructure(UUID actorId, UUID programId) {
		Program program = requireEditable(actorId, programId);
		if (!units.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()).isEmpty()) {
			throw new ConflictException(Texts.forRequest("error.iskurStructureExists"));
		}
		Instant now = Instant.now();
		for (int u = 1; u <= DEFAULT_UNITS; u++) {
			IskurUnit unit = units.save(new IskurUnit(program.getId(),
					Texts.get(AppLanguage.TR, "iskur.defaultUnit" + u), u - 1, actorId, now));
			skillService.seedDefaultSkills(program.getId(), unit.getId(), u);
			for (int t = 1; t <= DEFAULT_TEAMS_PER_UNIT; t++) {
				teams.save(new IskurTeam(unit, Texts.get(AppLanguage.TR, "iskur.defaultTeam" + u + "." + t), t - 1,
						now));
			}
		}
		return build(program);
	}

	@Transactional
	public IskurWorkspaceResponse createUnit(UUID actorId, UUID programId, IskurUnitRequest request) {
		Program program = requireEditable(actorId, programId);
		String name = request.name().strip();
		List<IskurUnit> existing = units.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId());
		requireFreeName(existing.stream().map(IskurUnit::getName).toList(), name, "error.iskurUnitNameTaken");
		int order = nextOrder(existing.stream().map(IskurUnit::getSortOrder).toList());
		IskurUnit unit = new IskurUnit(program.getId(), name, order, actorId, Instant.now());
		unit.setCoordinatorId(requireUnitCoordinator(program, request.coordinatorId()));
		units.save(unit);
		return build(program);
	}

	@Transactional
	public IskurWorkspaceResponse updateUnit(UUID actorId, UUID programId, UUID unitId, IskurUnitRequest request) {
		Program program = requireEditable(actorId, programId);
		IskurUnit unit = requireUnit(program, unitId);
		String name = request.name().strip();
		requireFreeName(units.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()).stream()
				.filter(other -> !other.getId().equals(unitId))
				.map(IskurUnit::getName)
				.toList(), name, "error.iskurUnitNameTaken");
		unit.setName(name);
		unit.setCoordinatorId(requireUnitCoordinator(program, request.coordinatorId()));
		units.save(unit);
		return build(program);
	}

	/** Only an empty unit goes: nobody is placed in any of its teams. */
	@Transactional
	public IskurWorkspaceResponse deleteUnit(UUID actorId, UUID programId, UUID unitId) {
		Program program = requireEditable(actorId, programId);
		IskurUnit unit = requireUnit(program, unitId);
		List<IskurTeam> unitTeams = teams.findByUnitId(unit.getId());
		if (!unitTeams.isEmpty() && placements.existsByTeamIdIn(unitTeams.stream().map(IskurTeam::getId).toList())) {
			throw new ConflictException(Texts.forRequest("error.iskurUnitNotEmpty"));
		}
		taskOutputs.removeAllOfTeams(unitTeams.stream().map(IskurTeam::getId).toList());
		teams.deleteAll(unitTeams);
		units.delete(unit);
		return build(program);
	}

	@Transactional
	public IskurWorkspaceResponse createTeam(UUID actorId, UUID programId, UUID unitId, IskurTeamRequest request) {
		Program program = requireEditable(actorId, programId);
		IskurUnit unit = requireUnit(program, unitId);
		String name = request.name().strip();
		requireFreeName(teamNames(program, null), name, "error.iskurTeamNameTaken");
		int order = nextOrder(teams.findByUnitId(unit.getId()).stream().map(IskurTeam::getSortOrder).toList());
		teams.save(new IskurTeam(unit, name, order, Instant.now()));
		return build(program);
	}

	@Transactional
	public IskurWorkspaceResponse updateTeam(UUID actorId, UUID programId, UUID teamId, IskurTeamRequest request) {
		Program program = requireEditable(actorId, programId);
		IskurTeam team = requireTeam(program, teamId);
		String name = request.name().strip();
		requireFreeName(teamNames(program, teamId), name, "error.iskurTeamNameTaken");
		team.setName(name);
		teams.save(team);
		return build(program);
	}

	/** Only an empty team goes; the coordinator moves its people first. */
	@Transactional
	public IskurWorkspaceResponse deleteTeam(UUID actorId, UUID programId, UUID teamId) {
		Program program = requireEditable(actorId, programId);
		IskurTeam team = requireTeam(program, teamId);
		if (!placements.findByTeamId(team.getId()).isEmpty()) {
			throw new ConflictException(Texts.forRequest("error.iskurTeamNotEmpty"));
		}
		taskOutputs.removeAllOfTeams(List.of(team.getId()));
		teams.delete(team);
		return build(program);
	}

	/**
	 * Places an intern, or changes where they are. A team has at most two leaders; a member's leader must lead the
	 * same team. A leader who stops leading this team leaves their sub-group without a leader, for the coordinator to
	 * re-assign. Moving a final placement keeps it final.
	 */
	@Transactional
	public IskurWorkspaceResponse place(UUID actorId, UUID programId, UUID userId, IskurPlacementRequest request) {
		Program program = requireEditable(actorId, programId);
		// Placing closes the intern's sent form: one being sent again now finishes first, or finds the placement.
		onboardings.findForUpdate(program.getId(), userId);
		Optional<IskurPlacement> existing = placements.findByProgramIdAndUserId(program.getId(), userId);
		if (existing.isEmpty() && !candidateIds(program).contains(userId)) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurNotInProgram"));
		}
		IskurTeam team = requireTeam(program, request.teamId());
		List<IskurPlacement> teammates = placements.findByTeamId(team.getId()).stream()
				.filter(other -> !other.getUserId().equals(userId))
				.toList();
		UUID leaderId = requireLeader(request, teammates);
		Integer days = request.weeklyDays();
		Integer mentoring = request.mentoringMinutes();
		if (mentoring != null && (days == null || !IskurWeeklySplit.fits(days, mentoring))) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurMentoringMinutes"));
		}

		Instant now = Instant.now();
		IskurPlacement placement = existing.orElseGet(() -> new IskurPlacement(program.getId(), userId, actorId, now));
		UUID previousTeamId = existing.map(IskurPlacement::getTeamId).orElse(null);
		boolean stopsLeading = existing.isPresent() && placement.getTeamRole() == IskurTeamRole.LEADER
				&& (request.role() != IskurTeamRole.LEADER || !team.getId().equals(previousTeamId));
		if (stopsLeading) {
			releaseSubgroup(placement.getTeamId(), userId, now);
		}
		placement.setTeamId(team.getId());
		placement.setTeamRole(request.role());
		placement.setLeaderId(leaderId);
		placement.setWeeklyDays(days == null ? null : days.shortValue());
		placement.setMentoringMinutes(mentoring);
		placement.setUpdatedAt(now);
		placements.save(placement);
		events.publishEvent(IskurPlacementChangedEvent.of(placement, previousTeamId));
		return build(program);
	}

	/** Takes an intern out of the structure, back to the unplaced list; their sub-group loses its leader. */
	@Transactional
	public IskurWorkspaceResponse unplace(UUID actorId, UUID programId, UUID userId) {
		Program program = requireEditable(actorId, programId);
		IskurPlacement placement = placements.findByProgramIdAndUserId(program.getId(), userId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurPlacementNotFound")));
		if (placement.getTeamRole() == IskurTeamRole.LEADER) {
			releaseSubgroup(placement.getTeamId(), userId, Instant.now());
		}
		placements.delete(placement);
		events.publishEvent(IskurPlacementChangedEvent.removed(placement));
		return build(program);
	}

	/** Makes the provisional placements of one team, one unit or the whole program final ("Atamayı kesinleştir"). */
	@Transactional
	public IskurWorkspaceResponse finalizePlacements(UUID actorId, UUID programId, IskurFinalizeRequest request) {
		Program program = requireEditable(actorId, programId);
		UUID teamId = request.teamId();
		UUID unitId = request.unitId();
		if (teamId != null && unitId != null) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurFinalizeOneScope"));
		}
		Set<UUID> scope = null;
		if (teamId != null) {
			scope = Set.of(requireTeam(program, teamId).getId());
		} else if (unitId != null) {
			scope = teams.findByUnitId(requireUnit(program, unitId).getId()).stream()
					.map(IskurTeam::getId)
					.collect(Collectors.toSet());
		}
		Set<UUID> inScope = scope;
		Instant now = Instant.now();
		List<IskurPlacement> finalized = placements.findByProgramId(program.getId()).stream()
				.filter(placement -> placement.getStatus() == IskurPlacementStatus.PROVISIONAL)
				.filter(placement -> inScope == null || inScope.contains(placement.getTeamId()))
				.toList();
		finalized.forEach(placement -> placement.finalizeBy(actorId, now));
		placements.saveAll(finalized);
		finalized.forEach(placement -> events
				.publishEvent(IskurPlacementChangedEvent.of(placement, placement.getTeamId())));
		return build(program);
	}

	/** A peer program the caller coordinates (or the admin); others are refused as in {@code requireManagedProgram}. */
	Program requireProgram(UUID actorId, UUID programId) {
		Program program = coordinatorProgramService.requireManagedProgram(actorId, programId);
		if (!program.has(ProgramFeature.ISKUR)) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurPeerProgramsOnly"));
		}
		return program;
	}

	/**
	 * A program the caller may change, locked for the rest of the transaction: the two-leader cap, the sub-groups and
	 * "delete only when empty" are read-then-write rules, so two coordinators' changes must not interleave.
	 */
	Program requireEditable(UUID actorId, UUID programId) {
		requireProgram(actorId, programId);
		Program program = programs.findByIdForUpdate(programId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurProgramNotFound")));
		if (program.getStatus() == ProgramStatus.COMPLETED) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurProgramArchived"));
		}
		return program;
	}

	private IskurUnit requireUnit(Program program, UUID unitId) {
		return units.findById(unitId)
				.filter(unit -> unit.getProgramId().equals(program.getId()))
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurUnitNotFound")));
	}

	private IskurTeam requireTeam(Program program, UUID teamId) {
		return teams.findById(teamId)
				.filter(team -> team.getProgramId().equals(program.getId()))
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurTeamNotFound")));
	}

	/** The unit coordinator is one of the program's coordinators, or nobody. */
	private @Nullable UUID requireUnitCoordinator(Program program, @Nullable UUID coordinatorId) {
		if (coordinatorId == null) {
			return null;
		}
		boolean coordinates = coordinatorProgramService.coordinatorsOf(program).stream()
				.anyMatch(ref -> ref.id().equals(coordinatorId));
		if (!coordinates) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurUnitCoordinatorNotInProgram"));
		}
		return coordinatorId;
	}

	/** A leader has no leader and the team keeps at most two; a member's leader leads the same team. */
	private static @Nullable UUID requireLeader(IskurPlacementRequest request, List<IskurPlacement> teammates) {
		UUID leaderId = request.leaderId();
		if (request.role() == IskurTeamRole.LEADER) {
			if (leaderId != null) {
				throw new InvalidRequestException(Texts.forRequest("error.iskurLeaderHasNoLeader"));
			}
			long leaders = teammates.stream().filter(other -> other.getTeamRole() == IskurTeamRole.LEADER).count();
			if (leaders >= MAX_LEADERS) {
				throw new ConflictException(Texts.forRequest("error.iskurTeamLeadersFull"));
			}
			return null;
		}
		if (leaderId != null && teammates.stream().noneMatch(other -> other.getUserId().equals(leaderId)
				&& other.getTeamRole() == IskurTeamRole.LEADER)) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurLeaderNotInTeam"));
		}
		return leaderId;
	}

	private void releaseSubgroup(UUID teamId, UUID leaderId, Instant now) {
		List<IskurPlacement> subgroup = placements.findByTeamId(teamId).stream()
				.filter(member -> leaderId.equals(member.getLeaderId()))
				.toList();
		subgroup.forEach(member -> {
			member.setLeaderId(null);
			member.setUpdatedAt(now);
		});
		placements.saveAll(subgroup);
		subgroup.forEach(member -> events.publishEvent(IskurPlacementChangedEvent.of(member, member.getTeamId())));
	}

	private List<String> teamNames(Program program, @Nullable UUID except) {
		return teams.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()).stream()
				.filter(team -> !team.getId().equals(except))
				.map(IskurTeam::getName)
				.toList();
	}

	private static void requireFreeName(List<String> taken, String name, String errorKey) {
		String wanted = name.toLowerCase(TR);
		if (taken.stream().anyMatch(other -> other.toLowerCase(TR).equals(wanted))) {
			throw new ConflictException(Texts.forRequest(errorKey));
		}
	}

	private static int nextOrder(List<Integer> orders) {
		return orders.stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
	}

	/** The program's interns (students and peer mentors on its İŞKUR list): the people the coordinator can place. */
	private Set<UUID> candidateIds(Program program) {
		return interns.internIds(program.getId());
	}

	private Set<UUID> peerMentorIds(Program program) {
		return programMentors.findByProgramIdOrderByCreatedAtAsc(program.getId()).stream()
				.filter(membership -> membership.getStatus() == ProgramMentorStatus.ACCEPTED)
				.map(ProgramMentor::getMentorId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private IskurWorkspaceResponse build(Program program) {
		List<IskurPlacement> placementRows = placements.findByProgramId(program.getId());
		List<IskurWorkspaceResponse.Coordinator> coordinators = coordinatorProgramService.coordinatorsOf(program)
				.stream()
				.map(ref -> new IskurWorkspaceResponse.Coordinator(ref.id(), ref.fullName()))
				.toList();

		Set<UUID> mentorIds = peerMentorIds(program);
		Set<UUID> candidates = candidateIds(program);
		Map<UUID, AppUser> accounts = new HashMap<>();
		Set<UUID> wanted = new LinkedHashSet<>(candidates);
		placementRows.forEach(placement -> wanted.add(placement.getUserId()));
		users.findAllById(wanted).forEach(user -> accounts.put(user.getId(), user));
		Map<UUID, InstitutionPerson> records = latestRecords(accounts.keySet());
		Map<UUID, IskurWorkspaceResponse.Person> people = new HashMap<>();
		accounts.values().forEach(user -> people.put(user.getId(), new IskurWorkspaceResponse.Person(user.getId(),
				user.getFullName(), user.getEmail(), departmentOf(records.get(user.getId())),
				iskurParticipant(records.get(user.getId())), mentorIds.contains(user.getId()))));

		Collator collator = Collator.getInstance(TR);
		Comparator<IskurWorkspaceResponse.Person> byName = Comparator
				.comparing(IskurWorkspaceResponse.Person::fullName, collator);
		Set<UUID> placedIds = placementRows.stream().map(IskurPlacement::getUserId).collect(Collectors.toSet());
		List<IskurWorkspaceResponse.Person> pool = candidates.stream()
				.filter(id -> !placedIds.contains(id))
				.map(people::get)
				.filter(person -> person != null)
				.sorted(byName)
				.toList();

		List<IskurPlacement> shown = placementRows.stream()
				.filter(placement -> people.containsKey(placement.getUserId()))
				.toList();
		Map<UUID, List<IskurPlacement>> byTeam = shown.stream()
				.collect(Collectors.groupingBy(IskurPlacement::getTeamId));
		Map<UUID, IskurWorkspaceResponse.Coordinator> coordinatorsById = new HashMap<>();
		coordinators.forEach(coordinator -> coordinatorsById.putIfAbsent(coordinator.userId(), coordinator));
		Map<UUID, List<IskurTeam>> teamsByUnit = teams.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId())
				.stream()
				.collect(Collectors.groupingBy(IskurTeam::getUnitId));

		List<IskurWorkspaceResponse.Unit> unitResponses = units
				.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()).stream()
				.map(unit -> new IskurWorkspaceResponse.Unit(unit.getId(), unit.getName(), unit.getSortOrder(),
						unit.getCoordinatorId() == null ? null : coordinatorsById.get(unit.getCoordinatorId()),
						teamsByUnit.getOrDefault(unit.getId(), List.of()).stream()
								.map(team -> team(team, byTeam.getOrDefault(team.getId(), List.of()), people,
										collator))
								.toList()))
				.toList();

		Set<UUID> everyone = new LinkedHashSet<>(candidates);
		everyone.addAll(placedIds);
		everyone.retainAll(people.keySet());
		IskurWorkspaceResponse.Counts counts = new IskurWorkspaceResponse.Counts(everyone.size(), shown.size(),
				(int) shown.stream().filter(placement -> placement.getStatus() == IskurPlacementStatus.FINAL).count(),
				(int) shown.stream().filter(placement -> placement.getTeamRole() == IskurTeamRole.LEADER).count());
		return new IskurWorkspaceResponse(program.getId(), unitResponses, pool, coordinators, counts);
	}

	/** Leaders first, each followed by their sub-group, then the members without a leader; names in Turkish order. */
	private static IskurWorkspaceResponse.Team team(IskurTeam team, List<IskurPlacement> rows,
			Map<UUID, IskurWorkspaceResponse.Person> people, Collator collator) {
		Comparator<IskurPlacement> byName = Comparator.comparing(placement -> nameOf(people, placement), collator);
		List<IskurPlacement> leaders = rows.stream()
				.filter(placement -> placement.getTeamRole() == IskurTeamRole.LEADER)
				.sorted(byName)
				.toList();
		Set<UUID> leaderIds = leaders.stream().map(IskurPlacement::getUserId).collect(Collectors.toSet());

		List<IskurPlacement> ordered = new ArrayList<>();
		boolean subgroupTooBig = false;
		for (IskurPlacement leader : leaders) {
			ordered.add(leader);
			List<IskurPlacement> subgroup = rows.stream()
					.filter(member -> member.getTeamRole() == IskurTeamRole.MEMBER
							&& leader.getUserId().equals(member.getLeaderId()))
					.sorted(byName)
					.toList();
			subgroupTooBig |= subgroup.size() > SUBGROUP_MAX;
			ordered.addAll(subgroup);
		}
		rows.stream()
				.filter(member -> member.getTeamRole() == IskurTeamRole.MEMBER
						&& !leaderIds.contains(member.getLeaderId()))
				.sorted(byName)
				.forEach(ordered::add);

		List<IskurWarning> warnings = new ArrayList<>();
		if (rows.size() != TEAM_SIZE) {
			warnings.add(IskurWarning.TEAM_SIZE);
		}
		if (leaders.isEmpty()) {
			warnings.add(IskurWarning.NO_LEADER);
		}
		if (subgroupTooBig) {
			warnings.add(IskurWarning.SUBGROUP_SIZE);
		}
		return new IskurWorkspaceResponse.Team(team.getId(), team.getName(), team.getSortOrder(), ordered.stream()
				.map(placement -> placement(placement, personOf(people, placement), leaderIds))
				.toList(), warnings);
	}

	private static IskurWorkspaceResponse.Person personOf(Map<UUID, IskurWorkspaceResponse.Person> people,
			IskurPlacement placement) {
		IskurWorkspaceResponse.Person person = people.get(placement.getUserId());
		if (person == null) {
			throw new IllegalStateException("Placement without a loaded account: " + placement.getId());
		}
		return person;
	}

	private static String nameOf(Map<UUID, IskurWorkspaceResponse.Person> people, IskurPlacement placement) {
		return personOf(people, placement).fullName();
	}

	private static IskurWorkspaceResponse.Placement placement(IskurPlacement placement,
			IskurWorkspaceResponse.Person person, Set<UUID> leaderIds) {
		Short storedDays = placement.getWeeklyDays();
		Integer days = storedDays == null ? null : storedDays.intValue();
		UUID leaderId = leaderIds.contains(placement.getLeaderId()) ? placement.getLeaderId() : null;
		List<IskurWarning> warnings = new ArrayList<>();
		if (days == null) {
			warnings.add(IskurWarning.DAYS_MISSING);
		} else if (placement.getTeamRole() == IskurTeamRole.LEADER && days < LEADER_MIN_DAYS) {
			warnings.add(IskurWarning.LEADER_FEW_DAYS);
		}
		if (placement.getTeamRole() == IskurTeamRole.MEMBER && leaderId == null) {
			warnings.add(IskurWarning.NO_LEADER_ASSIGNED);
		}
		return new IskurWorkspaceResponse.Placement(person.userId(), person.fullName(), person.email(),
				person.department(), person.iskurParticipant(), person.peerMentor(), placement.getTeamId(),
				placement.getTeamRole(), leaderId, placement.getStatus(), days,
				days == null ? null : IskurWeeklySplit.of(days, placement.getMentoringMinutes()), warnings,
				placement.getFinalizedAt());
	}

	private static @Nullable String departmentOf(@Nullable InstitutionPerson record) {
		return record == null ? null : record.getDepartmentName();
	}

	private static boolean iskurParticipant(@Nullable InstitutionPerson record) {
		return record != null && record.isIskurParticipant();
	}

	/** A person can carry more than one ODTÜ record over time; the newest one describes them. */
	private Map<UUID, InstitutionPerson> latestRecords(Set<UUID> userIds) {
		Map<UUID, InstitutionPerson> latest = new HashMap<>();
		if (userIds.isEmpty()) {
			return latest;
		}
		for (InstitutionPerson record : institutionPeople.findByUserIdIn(userIds)) {
			AppUser user = record.getUser();
			if (user == null) {
				continue;
			}
			latest.merge(user.getId(), record,
					(first, second) -> first.getSyncedAt().isAfter(second.getSyncedAt()) ? first : second);
		}
		return latest;
	}
}
