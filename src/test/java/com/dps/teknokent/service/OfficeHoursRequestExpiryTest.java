package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dps.teknokent.domain.OfficeHoursRequestStatus;
import com.dps.teknokent.entity.OfficeHoursRequest;
import com.dps.teknokent.repository.OfficeHoursRequestRepository;

@ExtendWith(MockitoExtension.class)
class OfficeHoursRequestExpiryTest {

	/** Monday 9 November 2026, 09:00 in Turkey. */
	private static final Instant SENT = Instant.parse("2026-11-09T06:00:00Z");

	@Mock
	private OfficeHoursRequestRepository requestRepository;

	@InjectMocks
	private OfficeHoursRequestExpiry expiry;

	private static OfficeHoursRequest request(OfficeHoursRequestStatus status, String preferred, String proposed) {
		OfficeHoursRequest request = new OfficeHoursRequest();
		request.setId(UUID.randomUUID());
		request.setStatus(status);
		request.setPreferredSlots(preferred);
		request.setProposedSlots(proposed);
		request.setCreatedAt(SENT);
		request.setUpdatedAt(SENT);
		return request;
	}

	@Test
	void aPendingRequestExpiresAfter72HoursWhenItsTimesAreLater() {
		OfficeHoursRequest request = request(OfficeHoursRequestStatus.PENDING, "2026-11-20T10:00,2026-11-24T10:00",
				null);

		assertThat(OfficeHoursRequestExpiry.deadline(request)).contains(Instant.parse("2026-11-12T06:00:00Z"));
	}

	@Test
	void aPendingRequestExpiresWhenItsLastTimeStartsIfThatComesFirst() {
		// 14:00 Tuesday passed, but 10:00 Wednesday can still be accepted until it starts.
		OfficeHoursRequest request = request(OfficeHoursRequestStatus.PENDING, "2026-11-10T14:00,2026-11-11T10:00",
				null);

		assertThat(OfficeHoursRequestExpiry.deadline(request)).contains(Instant.parse("2026-11-11T07:00:00Z"));
	}

	@Test
	void theAnswerWindowStartsAgainWhenTheStudentAnswersAQuestion() {
		OfficeHoursRequest request = request(OfficeHoursRequestStatus.PENDING, "2026-11-30T10:00", null);
		request.setUpdatedAt(Instant.parse("2026-11-11T06:00:00Z"));

		assertThat(OfficeHoursRequestExpiry.deadline(request)).contains(Instant.parse("2026-11-14T06:00:00Z"));
	}

	@Test
	void oneWaitingOnTheStudentExpiresWhenTheLastTimeOnTheTableStarts() {
		assertThat(OfficeHoursRequestExpiry.deadline(request(OfficeHoursRequestStatus.MORE_INFO_REQUESTED,
				"2026-11-30T10:00", null))).contains(Instant.parse("2026-11-30T07:00:00Z"));
		assertThat(OfficeHoursRequestExpiry.deadline(request(OfficeHoursRequestStatus.COUNTER_PROPOSED,
				"2026-11-10T14:00", "2026-11-12T10:00,2026-11-13T15:00")))
				.contains(Instant.parse("2026-11-13T12:00:00Z"));
		assertThat(OfficeHoursRequestExpiry.deadline(request(OfficeHoursRequestStatus.ACCEPTED,
				"2026-11-10T14:00", null))).isEmpty();
	}

	@Test
	void expiresOnlyTheDueOnesAndOnlyIfNobodyMovedThemOn() {
		OfficeHoursRequest due = request(OfficeHoursRequestStatus.PENDING, "2026-11-30T10:00", null);
		OfficeHoursRequest notYet = request(OfficeHoursRequestStatus.COUNTER_PROPOSED, null, "2026-11-30T10:00");
		when(requestRepository.findByStatusIn(any())).thenReturn(List.of(due, notYet));
		Instant now = Instant.parse("2026-11-12T06:00:00Z");
		when(requestRepository.expireIfStill(due.getId(), OfficeHoursRequestStatus.PENDING, now)).thenReturn(1);

		assertThat(expiry.expireDue(now)).isEqualTo(1);
		verify(requestRepository, never()).expireIfStill(eq(notYet.getId()), any(), any());
	}
}
