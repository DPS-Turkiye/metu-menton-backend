package com.dps.teknokent.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Mondays in Turkey, after the weekend's last meetings: the two-week check for interns without a peer meeting. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.iskur.meeting-gap", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IskurMeetingGapScheduler {

	private final IskurMeetingGapService gaps;

	@Scheduled(cron = "${app.iskur.meeting-gap.cron:0 0 9 * * MON}", zone = "Europe/Istanbul")
	public void checkMeetingGaps() {
		gaps.run();
	}
}
