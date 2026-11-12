package com.dps.teknokent.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.OfficeHoursRequestStatus;
import com.dps.teknokent.entity.OfficeHoursRequest;
import com.dps.teknokent.repository.OfficeHoursRequestRepository;
import com.dps.teknokent.util.MeetingSlotTimes;

import lombok.RequiredArgsConstructor;

/**
 * FR-OH-15: a request nobody acts on in time expires and lets its times go. One waiting on the academic expires 72
 * hours after it started waiting, or when its last preferred time starts, whichever comes first. One waiting on the
 * student expires when the last time still on the table starts: the preferred ones after a question, the offered
 * ones after other times were proposed.
 */
@Service
@RequiredArgsConstructor
public class OfficeHoursRequestExpiry {

	static final Duration ANSWER_WINDOW = Duration.ofHours(72);

	private static final Set<OfficeHoursRequestStatus> OPEN = Set.of(OfficeHoursRequestStatus.PENDING,
			OfficeHoursRequestStatus.MORE_INFO_REQUESTED, OfficeHoursRequestStatus.COUNTER_PROPOSED);

	private final OfficeHoursRequestRepository requestRepository;

	/** Expires every open request whose deadline has passed; returns how many. */
	@Transactional
	public int expireDue(Instant now) {
		int expired = 0;
		for (OfficeHoursRequest request : requestRepository.findByStatusIn(OPEN)) {
			if (deadline(request).filter(at -> !at.isAfter(now)).isPresent()) {
				expired += requestRepository.expireIfStill(request.getId(), request.getStatus(), now);
			}
		}
		return expired;
	}

	/** When the request expires; empty for a closed one. */
	static Optional<Instant> deadline(OfficeHoursRequest request) {
		return switch (request.getStatus()) {
		// updatedAt is when it last came back to the academic: sent, or answered after a question.
		case PENDING -> Optional.of(earliest(request.getUpdatedAt().plus(ANSWER_WINDOW),
				lastStart(request.getPreferredSlots())));
		case MORE_INFO_REQUESTED -> lastStart(request.getPreferredSlots());
		case COUNTER_PROPOSED -> lastStart(request.getProposedSlots());
		case ACCEPTED, DECLINED, WITHDRAWN, EXPIRED -> Optional.empty();
		};
	}

	private static Instant earliest(Instant window, Optional<Instant> lastStart) {
		return lastStart.filter(start -> start.isBefore(window)).orElse(window);
	}

	private static Optional<Instant> lastStart(String joined) {
		if (joined == null || joined.isBlank()) {
			return Optional.empty();
		}
		List<Instant> starts = Arrays.stream(joined.split(",")).map(String::trim)
				.map(MeetingSlotTimes::parseMachineSlot).filter(Objects::nonNull).toList();
		return starts.stream().max(Comparator.naturalOrder());
	}
}
