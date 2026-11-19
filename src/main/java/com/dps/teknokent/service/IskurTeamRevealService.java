package com.dps.teknokent.service;

import java.text.Collator;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IskurPlacementStatus;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.dto.responses.IskurTeamRevealResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurOnboarding;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurOnboardingRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.util.MeetingSlotTimes;
import com.dps.teknokent.util.Texts;

/**
 * "Ekibin belli oldu" (Figma 05): the intern's own team as the coordinator placed it, provisional through the four
 * trial weeks or final. It names the team's people and roles, the intern's leader and the unit coordinator; it never
 * shows another team or anyone's contact details.
 */
@Service
public class IskurTeamRevealService {

	/** The placement is provisional for the first four weeks of the program ("Deneme n/4. hafta"). */
	static final int TRIAL_WEEKS = 4;

	private static final Locale TR = Locale.forLanguageTag("tr");

	private final IskurInternService interns;
	private final IskurPlacementRepository placements;
	private final IskurTeamRepository teams;
	private final IskurUnitRepository units;
	private final IskurOnboardingRepository onboardings;
	private final AppUserRepository users;
	private final Clock clock;

	@Autowired
	public IskurTeamRevealService(IskurInternService interns, IskurPlacementRepository placements,
			IskurTeamRepository teams, IskurUnitRepository units, IskurOnboardingRepository onboardings,
			AppUserRepository users) {
		this(interns, placements, teams, units, onboardings, users, Clock.systemUTC());
	}

	IskurTeamRevealService(IskurInternService interns, IskurPlacementRepository placements, IskurTeamRepository teams,
			IskurUnitRepository units, IskurOnboardingRepository onboardings, AppUserRepository users, Clock clock) {
		this.interns = interns;
		this.placements = placements;
		this.teams = teams;
		this.units = units;
		this.onboardings = onboardings;
		this.users = users;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public IskurTeamRevealResponse me(UUID actorId) {
		Program program = interns.currentProgram(actorId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurNotAnIntern")));
		boolean submitted = onboardings.findByProgramIdAndUserId(program.getId(), actorId)
				.filter(IskurOnboarding::isSubmitted)
				.isPresent();
		IskurPlacement mine = placements.findByProgramIdAndUserId(program.getId(), actorId).orElse(null);
		IskurTeam team = mine == null ? null : teams.findById(mine.getTeamId()).orElse(null);
		IskurUnit unit = team == null ? null : units.findById(team.getUnitId()).orElse(null);
		if (mine == null || team == null || unit == null) {
			return new IskurTeamRevealResponse(program.getId(), program.getName(), submitted, null);
		}

		List<IskurPlacement> teammates = placements.findByTeamId(team.getId());
		Set<UUID> ids = teammates.stream().map(IskurPlacement::getUserId).collect(Collectors.toSet());
		UUID coordinatorId = unit.getCoordinatorId();
		if (coordinatorId != null) {
			ids.add(coordinatorId);
		}
		Map<UUID, AppUser> people = new HashMap<>();
		users.findAllById(ids).forEach(user -> people.put(user.getId(), user));
		List<IskurTeamRevealResponse.Member> members = members(teammates, people, actorId);
		IskurTeamRevealResponse.Person leader = members.stream()
				.filter(member -> mine.getTeamRole() == IskurTeamRole.MEMBER
						&& member.role() == IskurTeamRole.LEADER
						&& member.userId().equals(mine.getLeaderId()))
				.map(member -> new IskurTeamRevealResponse.Person(member.userId(), member.fullName()))
				.findFirst()
				.orElse(null);
		AppUser coordinator = coordinatorId == null ? null : people.get(coordinatorId);
		return new IskurTeamRevealResponse(program.getId(), program.getName(), submitted,
				new IskurTeamRevealResponse.Placement(mine.getStatus(), trialWeek(program, mine), mine.getTeamRole(),
						leader,
						new IskurTeamRevealResponse.Unit(unit.getName(), coordinator == null ? null
								: new IskurTeamRevealResponse.Person(coordinator.getId(), coordinator.getFullName())),
						new IskurTeamRevealResponse.Team(team.getName(), members),
						commonDays(program, teammates)));
	}

	/** The week of the trial, 1 to 4, while the placement is provisional and the program has a start date. */
	@Nullable
	Integer trialWeek(Program program, IskurPlacement placement) {
		LocalDate start = program.getStartDate();
		if (placement.getStatus() != IskurPlacementStatus.PROVISIONAL || start == null) {
			return null;
		}
		LocalDate today = LocalDate.now(clock.withZone(MeetingSlotTimes.APP_ZONE));
		long week = today.isBefore(start) ? 1 : ChronoUnit.DAYS.between(start, today) / 7 + 1;
		return week > TRIAL_WEEKS ? null : (int) week;
	}

	/** Leaders first, each followed by their sub-group, then the members without a leader; names in Turkish order. */
	private static List<IskurTeamRevealResponse.Member> members(List<IskurPlacement> teammates,
			Map<UUID, AppUser> people, UUID me) {
		Collator collator = Collator.getInstance(TR);
		Comparator<IskurPlacement> byName = Comparator.comparing(placement -> nameOf(people, placement), collator);
		List<IskurPlacement> shown = teammates.stream().filter(placement -> people.containsKey(placement.getUserId()))
				.toList();
		List<IskurPlacement> leaders = shown.stream()
				.filter(placement -> placement.getTeamRole() == IskurTeamRole.LEADER)
				.sorted(byName)
				.toList();
		Set<UUID> leaderIds = leaders.stream().map(IskurPlacement::getUserId).collect(Collectors.toSet());
		List<IskurPlacement> ordered = new ArrayList<>();
		for (IskurPlacement leader : leaders) {
			ordered.add(leader);
			shown.stream()
					.filter(member -> member.getTeamRole() == IskurTeamRole.MEMBER
							&& leader.getUserId().equals(member.getLeaderId()))
					.sorted(byName)
					.forEach(ordered::add);
		}
		shown.stream()
				.filter(member -> member.getTeamRole() == IskurTeamRole.MEMBER
						&& !leaderIds.contains(member.getLeaderId()))
				.sorted(byName)
				.forEach(ordered::add);
		return ordered.stream()
				.map(placement -> new IskurTeamRevealResponse.Member(placement.getUserId(), nameOf(people, placement),
						placement.getTeamRole(),
						leaderIds.contains(placement.getLeaderId()) ? placement.getLeaderId() : null,
						placement.getUserId().equals(me)))
				.toList();
	}

	/**
	 * The days everyone in the team works ("Ekip günleri: Salı ve Perşembe ortak"), from the submitted forms; empty
	 * until at least two of the team have sent theirs.
	 */
	private List<DayOfWeek> commonDays(Program program, List<IskurPlacement> teammates) {
		List<IskurOnboarding> forms = onboardings.findByProgramIdAndUserIdIn(program.getId(),
				teammates.stream().map(IskurPlacement::getUserId).toList()).stream()
				.filter(IskurOnboarding::isSubmitted)
				.toList();
		if (forms.size() < 2) {
			return List.of();
		}
		Set<DayOfWeek> shared = EnumSet.allOf(DayOfWeek.class);
		for (IskurOnboarding form : forms) {
			Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
			form.getRanges().forEach(range -> days.add(DayOfWeek.of(range.getDayOfWeek())));
			shared.retainAll(days);
		}
		return List.copyOf(shared);
	}

	private static String nameOf(Map<UUID, AppUser> people, IskurPlacement placement) {
		AppUser user = people.get(placement.getUserId());
		return user == null ? "" : user.getFullName();
	}
}
