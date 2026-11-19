package com.dps.teknokent.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.Meeting;
import com.dps.teknokent.entity.PeerActivity;
import com.dps.teknokent.entity.PeerMeetingReport;

import lombok.RequiredArgsConstructor;

/**
 * An intern's peer mentoring in one week, as numbers only: meetings held, their minutes, how many reports were sent,
 * and the minutes of the other mentoring work (rules frame 322:3413, "Ekip lideri neyi görür?"). Nothing here keeps a
 * mentee, a note or a report body, so what is built from it cannot show one to a leader.
 */
@Component
@RequiredArgsConstructor
class IskurMentoringTally {

	private final PeerReportAccess access;
	private final PeerMeetingReportService meetingReports;
	private final PeerDailyReportService dailyReports;
	private final PeerReportCalendar calendar;

	/** One day: the held peer meetings, how many have a sent report, and the minutes of each kind of work. */
	record Day(LocalDate date, int meetingCount, int meetingMinutes, int reportsSubmitted, int activityMinutes) {

		int minutes() {
			return meetingMinutes + activityMinutes;
		}
	}

	/** Monday to Sunday, and whether a peer meeting of the week is still to come. */
	record Week(List<Day> days, boolean meetingsAhead) {

		int meetingCount() {
			return days.stream().mapToInt(Day::meetingCount).sum();
		}

		int meetingMinutes() {
			return days.stream().mapToInt(Day::meetingMinutes).sum();
		}

		int activityMinutes() {
			return days.stream().mapToInt(Day::activityMinutes).sum();
		}

		int reportsSubmitted() {
			return days.stream().mapToInt(Day::reportsSubmitted).sum();
		}

		int minutes() {
			return meetingMinutes() + activityMinutes();
		}
	}

	Week of(AppUser intern, LocalDate monday) {
		List<Meeting> held = access.heldMeetings(intern, calendar.startOf(monday),
				calendar.startOf(monday.plusDays(7)));
		Map<UUID, PeerMeetingReport> reports = meetingReports.reportsFor(intern, held);
		List<PeerActivity> activities = dailyReports.activities(intern, monday, monday.plusDays(6),
				PeerReportScope.MENTOR);
		List<Day> days = new ArrayList<>();
		for (int offset = 0; offset < 7; offset++) {
			LocalDate date = monday.plusDays(offset);
			List<Meeting> onDay = held.stream()
					.filter(meeting -> calendar.dateOf(meeting.getScheduledAt()).equals(date))
					.toList();
			int meetingMinutes = onDay.stream()
					.mapToInt(meeting -> PeerMeetingReportService.durationOf(meeting, reports.get(meeting.getId())))
					.sum();
			int sent = (int) onDay.stream()
					.filter(meeting -> PeerMeetingReportService.sent(reports.get(meeting.getId())))
					.count();
			int activityMinutes = activities.stream()
					.filter(activity -> activity.getActivityDate().equals(date))
					.mapToInt(PeerActivity::getDurationMinutes)
					.sum();
			days.add(new Day(date, onDay.size(), meetingMinutes, sent, activityMinutes));
		}
		return new Week(List.copyOf(days), access.meetingsAhead(intern, calendar.startOf(monday.plusDays(7))));
	}
}
