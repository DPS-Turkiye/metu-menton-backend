package com.dps.teknokent.service;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.office-hours.expiry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OfficeHoursRequestExpiryScheduler {

	private final OfficeHoursRequestExpiry expiry;

	@Scheduled(fixedDelayString = "${app.office-hours.expiry.interval-ms:300000}")
	public void expireDueRequests() {
		expiry.expireDue(Instant.now());
	}
}
