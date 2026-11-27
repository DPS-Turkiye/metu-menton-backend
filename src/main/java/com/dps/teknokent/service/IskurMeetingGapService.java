package com.dps.teknokent.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.dto.responses.CoordinatorProgramResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurMeetingGapAlert;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurMeetingGapAlertRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "İki hafta üst üste görüşme yoksa akran mentörlüğü koordinatörüne bildirim gider" (rules frame 322:3413). On Monday,
 * for each running İŞKUR program, every placed intern who gives peer mentoring and held no peer meeting in either of
 * the last two weeks is reported to the program's coordinators, once per stretch: while the stretch goes on, each
 * Monday only carries its mark forward, and a meeting ends it. Interns whose badge is still pending are not watched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IskurMeetingGapService {

	private final ProgramRepository programs;
	private final IskurUnitRepository units;
	private final IskurPlacementRepository placements;
	private final IskurMeetingGapAlertRepository alerts;
	private final AppUserRepository users;
	private final CoordinatorProgramService coordinatorProgramService;
	private final NotificationService notifications;
	private final IskurMentoringTally tally;
	private final PeerReportCalendar calendar;
	private final IskurInternService interns;

	/** Checks the two weeks before this one; answers how many interns were reported. */
	@Transactional
	public int run() {
		LocalDate thisMonday = calendar.weekStartOf(calendar.today());
		LocalDate lastMonday = thisMonday.minusDays(7);
		LocalDate firstMonday = lastMonday.minusDays(7);
		int reported = 0;
		for (Program program : programs.findWithFeature(ProgramFeature.ISKUR)) {
			if (program.getStatus() != ProgramStatus.ACTIVE || !units.existsByProgramId(program.getId())) {
				continue;
			}
			reported += check(program, firstMonday, lastMonday);
		}
		if (reported > 0) {
			log.info("İŞKUR meeting gap: {} interns reported for the weeks from {}", reported, firstMonday);
		}
		return reported;
	}

	private int check(Program program, LocalDate firstMonday, LocalDate lastMonday) {
		// Locked for the rest of the transaction: a second instance running the job at the same minute waits here,
		// then sees the alerts this one wrote, so a coordinator is never told twice.
		programs.findByIdForUpdate(program.getId());
		Set<UUID> coordinatorIds = coordinatorProgramService.coordinatorsOf(program).stream()
				.map(CoordinatorProgramResponse.CoordinatorRef::id)
				.collect(Collectors.toSet());
		List<AppUser> coordinators = users.findAllById(coordinatorIds);
		if (coordinators.isEmpty()) {
			// No one to tell yet: no alert is recorded, so the first coordinator added still hears of it.
			return 0;
		}
		List<IskurPlacement> watched = placements.findByProgramId(program.getId()).stream()
				.filter(IskurMeetingGapService::givesMentoring)
				// Someone placed during the two weeks has not had two whole weeks yet.
				.filter(placement -> !calendar.dateOf(placement.getPlacedAt()).isAfter(firstMonday))
				.filter(placement -> !alerts.existsByProgramIdAndUserIdAndWeekStart(program.getId(),
						placement.getUserId(), lastMonday))
				.toList();
		if (watched.isEmpty()) {
			return 0;
		}
		Map<UUID, AppUser> people = users.findAllById(watched.stream().map(IskurPlacement::getUserId).toList())
				.stream()
				.collect(Collectors.toMap(AppUser::getId, Function.identity()));
		// An intern whose badge is still pending cannot hold peer meetings yet, so their silence is not a gap.
		Set<UUID> mentors = interns.peerMentorIds(program.getId(), people.values());
		LocalDate thisMonday = lastMonday.plusDays(7);
		int reported = 0;
		for (IskurPlacement placement : watched) {
			AppUser person = people.get(placement.getUserId());
			if (person == null || !mentors.contains(person.getId())
					|| tally.meetingsHeld(person, firstMonday, thisMonday) > 0) {
				continue;
			}
			// Still the same silent stretch as last week's alert: carry the mark on, without telling anyone again.
			boolean continuing = alerts.existsByProgramIdAndUserIdAndWeekStart(program.getId(), person.getId(),
					firstMonday);
			alerts.save(new IskurMeetingGapAlert(program.getId(), person.getId(), lastMonday, calendar.now()));
			if (continuing) {
				continue;
			}
			coordinators.forEach(coordinator -> notifications.create(coordinator, NotificationCategory.GENERAL,
					Texts.get(coordinator, "notification.iskurMeetingGap.title"),
					Texts.get(coordinator, "notification.iskurMeetingGap.body", person.getFullName(),
							program.getName()),
					"/coordinator/programs/" + program.getId() + "/iskur"));
			reported++;
		}
		return reported;
	}

	/** An intern whose split is set to no mentoring is not expected to meet anyone. */
	private static boolean givesMentoring(IskurPlacement placement) {
		Integer minutes = placement.getMentoringMinutes();
		return minutes == null || minutes > 0;
	}
}
