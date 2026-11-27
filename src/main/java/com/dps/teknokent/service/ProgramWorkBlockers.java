package com.dps.teknokent.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.MentorshipStatus;
import com.dps.teknokent.domain.PeerReportStatus;
import com.dps.teknokent.domain.ProgramBlocker;
import com.dps.teknokent.domain.ProgramBlockerKind;
import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.MentorshipRepository;
import com.dps.teknokent.repository.MentorshipRequestRepository;
import com.dps.teknokent.repository.OfficeHoursMeetingRepository;
import com.dps.teknokent.repository.PeerDailyReportRepository;
import com.dps.teknokent.repository.PeerMeetingReportRepository;
import com.dps.teknokent.repository.PeerWeeklyReportRepository;

/**
 * The open work in a semester that would silently end if one of its features were switched off: a refusal lists it
 * (counts only), and nothing is closed or deleted on the caller's behalf.
 */
@Service
public class ProgramWorkBlockers {

	/** Planned, running or paused: a mentorship somebody still works in. */
	static final Set<MentorshipStatus> LIVE_MENTORSHIPS = EnumSet.of(MentorshipStatus.PLANNED,
			MentorshipStatus.ACTIVE, MentorshipStatus.PAUSED);
	/** A report the mentor started or got back and has not sent (see {@link PeerReportStatus#editable}). */
	static final Set<PeerReportStatus> UNSENT_REPORTS = EnumSet.of(PeerReportStatus.DRAFT,
			PeerReportStatus.REVISION_REQUESTED);

	private final MentorshipRepository mentorships;
	private final MentorshipRequestRepository requests;
	private final IskurPlacementRepository placements;
	private final OfficeHoursMeetingRepository officeHoursMeetings;
	private final PeerDailyReportRepository dailyReports;
	private final PeerWeeklyReportRepository weeklyReports;
	private final PeerMeetingReportRepository meetingReports;
	private final Clock clock;

	@Autowired
	public ProgramWorkBlockers(MentorshipRepository mentorships, MentorshipRequestRepository requests,
			IskurPlacementRepository placements, OfficeHoursMeetingRepository officeHoursMeetings,
			PeerDailyReportRepository dailyReports, PeerWeeklyReportRepository weeklyReports,
			PeerMeetingReportRepository meetingReports) {
		this(mentorships, requests, placements, officeHoursMeetings, dailyReports, weeklyReports, meetingReports,
				Clock.systemUTC());
	}

	ProgramWorkBlockers(MentorshipRepository mentorships, MentorshipRequestRepository requests,
			IskurPlacementRepository placements, OfficeHoursMeetingRepository officeHoursMeetings,
			PeerDailyReportRepository dailyReports, PeerWeeklyReportRepository weeklyReports,
			PeerMeetingReportRepository meetingReports, Clock clock) {
		this.mentorships = mentorships;
		this.requests = requests;
		this.placements = placements;
		this.officeHoursMeetings = officeHoursMeetings;
		this.dailyReports = dailyReports;
		this.weeklyReports = weeklyReports;
		this.meetingReports = meetingReports;
		this.clock = clock;
	}

	/** What stops {@code switchedOff} from going off in {@code program}; empty when it may. */
	@Transactional(readOnly = true)
	public List<ProgramBlocker> forSwitchOff(Program program, Set<ProgramFeature> switchedOff) {
		List<ProgramBlocker> blockers = new ArrayList<>();
		if (switchedOff.contains(ProgramFeature.PEER_MENTORING)) {
			add(blockers, ProgramBlockerKind.ACTIVE_MENTORSHIP, ProgramFeature.PEER_MENTORING,
					mentorships.countByProgramIdAndStatusIn(program.getId(), LIVE_MENTORSHIPS));
			add(blockers, ProgramBlockerKind.OPEN_REQUEST, ProgramFeature.PEER_MENTORING,
					requests.countByProgramIdAndStatusIn(program.getId(), PeerMentoringService.OPEN));
			add(blockers, ProgramBlockerKind.UNSENT_REPORT, ProgramFeature.PEER_MENTORING,
					dailyReports.countByProgramIdAndStatusIn(program.getId(), UNSENT_REPORTS)
							+ weeklyReports.countByProgramIdAndStatusIn(program.getId(), UNSENT_REPORTS)
							+ meetingReports.countByProgramIdAndStatusIn(program.getId(), UNSENT_REPORTS));
		}
		if (switchedOff.contains(ProgramFeature.ISKUR)) {
			add(blockers, ProgramBlockerKind.ISKUR_PLACEMENT, ProgramFeature.ISKUR,
					placements.countByProgramId(program.getId()));
		}
		if (switchedOff.contains(ProgramFeature.OFFICE_HOURS)) {
			add(blockers, ProgramBlockerKind.UPCOMING_OFFICE_HOURS_MEETING, ProgramFeature.OFFICE_HOURS,
					officeHoursMeetings.countUpcomingForStudentsOf(program.getId(), clock.instant()));
		}
		return blockers;
	}

	/**
	 * What stops {@code person} from leaving {@code program} (an admin moving or removing them): their live
	 * mentorships and open requests there, as mentor or as mentee, their unsent peer reports, their İŞKUR placement,
	 * and, in a semester with Office Hours, their upcoming Office Hours meetings. Empty when they may go.
	 */
	@Transactional(readOnly = true)
	public List<ProgramBlocker> forPerson(Program program, AppUser person) {
		UUID programId = program.getId();
		UUID personId = person.getId();
		List<ProgramBlocker> blockers = new ArrayList<>();
		long live = mentorships.countByProgramIdAndMentorIdAndStatusIn(programId, personId, LIVE_MENTORSHIPS)
				// A student's own team of one; a shared team's mentorships are not this person's to finish.
				+ (person.getOrganization() == null || !person.getOrganization().isPersonal() ? 0
						: mentorships.countByProgramIdAndOrganizationIdAndStatusIn(programId,
								person.getOrganization().getId(), LIVE_MENTORSHIPS));
		add(blockers, ProgramBlockerKind.ACTIVE_MENTORSHIP, ProgramFeature.PEER_MENTORING, live);
		add(blockers, ProgramBlockerKind.OPEN_REQUEST, ProgramFeature.PEER_MENTORING,
				requests.countByProgramIdAndMentorIdAndStatusIn(programId, personId, PeerMentoringService.OPEN)
						+ requests.countByProgramIdAndRequesterIdAndStatusIn(programId, personId,
								PeerMentoringService.OPEN));
		add(blockers, ProgramBlockerKind.UNSENT_REPORT, ProgramFeature.PEER_MENTORING,
				dailyReports.countByProgramIdAndMentorIdAndStatusIn(programId, personId, UNSENT_REPORTS)
						+ weeklyReports.countByProgramIdAndMentorIdAndStatusIn(programId, personId, UNSENT_REPORTS)
						+ meetingReports.countByProgramIdAndMentorIdAndStatusIn(programId, personId, UNSENT_REPORTS));
		add(blockers, ProgramBlockerKind.ISKUR_PLACEMENT, ProgramFeature.ISKUR,
				placements.findByProgramIdAndUserId(programId, personId).isPresent() ? 1 : 0);
		if (program.has(ProgramFeature.OFFICE_HOURS)) {
			add(blockers, ProgramBlockerKind.UPCOMING_OFFICE_HOURS_MEETING, ProgramFeature.OFFICE_HOURS,
					officeHoursMeetings.countUpcomingForStudent(personId, clock.instant()));
		}
		return blockers;
	}

	private static void add(List<ProgramBlocker> blockers, ProgramBlockerKind kind, ProgramFeature feature,
			long count) {
		if (count > 0) {
			blockers.add(new ProgramBlocker(kind, feature, count));
		}
	}
}
