package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dps.teknokent.domain.ProgramBlocker;
import com.dps.teknokent.domain.ProgramBlockerKind;
import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.MentorshipRepository;
import com.dps.teknokent.repository.MentorshipRequestRepository;
import com.dps.teknokent.repository.OfficeHoursMeetingRepository;
import com.dps.teknokent.repository.PeerDailyReportRepository;
import com.dps.teknokent.repository.PeerMeetingReportRepository;
import com.dps.teknokent.repository.PeerWeeklyReportRepository;

class ProgramWorkBlockersTest {

	private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");

	private final MentorshipRepository mentorships = mock(MentorshipRepository.class);
	private final MentorshipRequestRepository requests = mock(MentorshipRequestRepository.class);
	private final IskurPlacementRepository placements = mock(IskurPlacementRepository.class);
	private final OfficeHoursMeetingRepository meetings = mock(OfficeHoursMeetingRepository.class);
	private final PeerDailyReportRepository daily = mock(PeerDailyReportRepository.class);
	private final PeerWeeklyReportRepository weekly = mock(PeerWeeklyReportRepository.class);
	private final PeerMeetingReportRepository meetingReports = mock(PeerMeetingReportRepository.class);
	private final ProgramWorkBlockers blockers = new ProgramWorkBlockers(mentorships, requests, placements, meetings,
			daily, weekly, meetingReports, Clock.fixed(NOW, ZoneOffset.UTC));
	private final Program semester = semester();

	@Test
	void peerMentoringIsHeldByLiveMentorshipsOpenRequestsAndUnsentReports() {
		when(mentorships.countByProgramIdAndStatusIn(semester.getId(), ProgramWorkBlockers.LIVE_MENTORSHIPS))
				.thenReturn(2L);
		when(requests.countByProgramIdAndStatusIn(semester.getId(), PeerMentoringService.OPEN)).thenReturn(1L);
		when(daily.countByProgramIdAndStatusIn(semester.getId(), ProgramWorkBlockers.UNSENT_REPORTS)).thenReturn(1L);
		when(weekly.countByProgramIdAndStatusIn(semester.getId(), ProgramWorkBlockers.UNSENT_REPORTS)).thenReturn(2L);
		when(meetingReports.countByProgramIdAndStatusIn(semester.getId(), ProgramWorkBlockers.UNSENT_REPORTS))
				.thenReturn(0L);

		assertThat(blockers.forSwitchOff(semester, EnumSet.of(ProgramFeature.PEER_MENTORING))).containsExactly(
				new ProgramBlocker(ProgramBlockerKind.ACTIVE_MENTORSHIP, ProgramFeature.PEER_MENTORING, 2),
				new ProgramBlocker(ProgramBlockerKind.OPEN_REQUEST, ProgramFeature.PEER_MENTORING, 1),
				new ProgramBlocker(ProgramBlockerKind.UNSENT_REPORT, ProgramFeature.PEER_MENTORING, 3));
	}

	@Test
	void iskurIsHeldByPlacementsAndOfficeHoursByUpcomingMeetings() {
		when(placements.countByProgramId(semester.getId())).thenReturn(4L);
		when(meetings.countUpcomingForStudentsOf(semester.getId(), NOW)).thenReturn(1L);

		assertThat(blockers.forSwitchOff(semester, EnumSet.of(ProgramFeature.ISKUR, ProgramFeature.OFFICE_HOURS)))
				.containsExactly(new ProgramBlocker(ProgramBlockerKind.ISKUR_PLACEMENT, ProgramFeature.ISKUR, 4),
						new ProgramBlocker(ProgramBlockerKind.UPCOMING_OFFICE_HOURS_MEETING,
								ProgramFeature.OFFICE_HOURS, 1));
	}

	@Test
	void nothingOpenMeansNoBlockers() {
		when(mentorships.countByProgramIdAndStatusIn(any(), any())).thenReturn(0L);

		assertThat(blockers.forSwitchOff(semester, EnumSet.of(ProgramFeature.PEER_MENTORING))).isEmpty();
	}

	@Test
	void switchingNothingOffLooksAtNothing() {
		assertThat(blockers.forSwitchOff(semester, Set.of())).isEmpty();
		verifyNoInteractions(mentorships, requests, placements, meetings, daily, weekly, meetingReports);
	}

	private static Program semester() {
		Program program = new Program();
		program.setId(UUID.randomUUID());
		program.setProgramType(ProgramType.SEMESTER);
		program.setFeatures(EnumSet.allOf(ProgramFeature.class));
		return program;
	}
}
