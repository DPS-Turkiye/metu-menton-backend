package com.dps.teknokent.service;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.dps.teknokent.domain.BadgeStatus;
import com.dps.teknokent.domain.ProgramFeature;
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

/**
 * The participant numbers of peer and Office Hours programs, a few grouped queries for a whole list. The caller has
 * already checked who may see the programs; these are counts only, never names.
 */
@Component
public class ProgramParticipantCounts {

	private final AppUserRepository users;
	private final UserBadgeRepository awards;
	private final MentorshipRequestRepository mentorshipRequests;
	private final ProgramPeerCategoryRepository categories;
	private final InstitutionPersonRepository institutionPeople;
	private final OfficeHoursRequestRepository officeHoursRequests;
	private final Clock clock;

	@Autowired
	public ProgramParticipantCounts(AppUserRepository users, UserBadgeRepository awards,
			MentorshipRequestRepository mentorshipRequests, ProgramPeerCategoryRepository categories,
			InstitutionPersonRepository institutionPeople, OfficeHoursRequestRepository officeHoursRequests) {
		this(users, awards, mentorshipRequests, categories, institutionPeople, officeHoursRequests,
				Clock.systemUTC());
	}

	ProgramParticipantCounts(AppUserRepository users, UserBadgeRepository awards,
			MentorshipRequestRepository mentorshipRequests, ProgramPeerCategoryRepository categories,
			InstitutionPersonRepository institutionPeople, OfficeHoursRequestRepository officeHoursRequests,
			Clock clock) {
		this.users = users;
		this.awards = awards;
		this.mentorshipRequests = mentorshipRequests;
		this.categories = categories;
		this.institutionPeople = institutionPeople;
		this.officeHoursRequests = officeHoursRequests;
		this.clock = clock;
	}

	/**
	 * Each peer or Office Hours program's counts; a startup program is left out (see {@link ProgramCountsResponse}).
	 */
	public Map<UUID, ProgramCountsResponse> of(Collection<Program> programs) {
		Map<UUID, ProgramCountsResponse> counts = new HashMap<>();
		Map<UUID, ProgramCountsResponse.Peer> peerCounts = new HashMap<>();
		List<UUID> peer = idsOf(programs, ProgramFeature.PEER_MENTORING);
		if (!peer.isEmpty()) {
			Map<UUID, Long> students = byProgram(users.countByProgramIdInAndPermissionAndStatus(peer, UserRole.MENTEE,
					UserAccountStatus.APPROVED));
			Map<UUID, Long> mentors = byProgram(awards.countByProgramIdInAndBadgeCodeAndStatus(peer, BadgeService.GATE,
					BadgeStatus.ACTIVE));
			Map<UUID, Long> open = byProgram(mentorshipRequests.countByProgramIdInAndStatusIn(peer,
					PeerMentoringService.OPEN));
			Map<UUID, Long> active = categories.findByProgramIdIn(peer).stream()
					.collect(Collectors.groupingBy(ProgramPeerCategory::getProgramId, Collectors.counting()));
			for (UUID id : peer) {
				peerCounts.put(id, new ProgramCountsResponse.Peer(
						count(students, id), count(mentors, id), count(open, id), count(active, id)));
			}
		}
		List<UUID> officeHours = idsOf(programs, ProgramFeature.OFFICE_HOURS);
		ProgramCountsResponse.OfficeHours university = null;
		if (!officeHours.isEmpty()) {
			// One university-wide set of numbers, the same for every program with Office Hours.
			university = new ProgramCountsResponse.OfficeHours(
					Math.toIntExact(institutionPeople.countByAcademicTrueAndActiveTrue()),
					Math.toIntExact(institutionPeople.countActiveAcademicsWithWeeklyHours()),
					Math.toIntExact(officeHoursRequests.countByStatusInAndCreatedAtGreaterThanEqual(
							ProgramOfficeHoursService.AWAITING_DECISION,
							clock.instant().minus(ProgramOfficeHoursService.RECENT_WINDOW))));
		}
		for (Program program : programs) {
			ProgramCountsResponse.Peer peerPart = peerCounts.get(program.getId());
			ProgramCountsResponse.OfficeHours officeHoursPart = officeHours.contains(program.getId()) ? university
					: null;
			if (peerPart != null || officeHoursPart != null) {
				counts.put(program.getId(), new ProgramCountsResponse(peerPart, officeHoursPart));
			}
		}
		return counts;
	}

	private static List<UUID> idsOf(Collection<Program> programs, ProgramFeature feature) {
		return programs.stream().filter(program -> program.has(feature)).map(Program::getId).toList();
	}

	private static Map<UUID, Long> byProgram(List<ProgramTally> tallies) {
		return tallies.stream().collect(Collectors.toMap(ProgramTally::getProgramId, ProgramTally::getTotal));
	}

	private static int count(Map<UUID, Long> counts, UUID programId) {
		return Math.toIntExact(counts.getOrDefault(programId, 0L));
	}
}
