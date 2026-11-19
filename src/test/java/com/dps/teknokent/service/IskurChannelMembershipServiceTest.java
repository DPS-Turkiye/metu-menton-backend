package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.dps.teknokent.domain.IskurPlacementStatus;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.repository.IskurChannelMemberRepository;
import com.dps.teknokent.repository.IskurChannelRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.ProgramRepository;

class IskurChannelMembershipServiceTest {

	private final ProgramRepository programs = mock(ProgramRepository.class);
	private final IskurPlacementRepository placements = mock(IskurPlacementRepository.class);
	private final IskurChannelRepository channels = mock(IskurChannelRepository.class);
	private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
	private final IskurChannelMembershipService service = new IskurChannelMembershipService(programs, placements,
			mock(IskurTeamRepository.class), channels, mock(IskurChannelMemberRepository.class), transactions);

	private final UUID programId = UUID.randomUUID();
	private final IskurPlacementChangedEvent event = new IskurPlacementChangedEvent(programId, UUID.randomUUID(),
			null, UUID.randomUUID(), IskurPlacementStatus.FINAL, IskurTeamRole.MEMBER, null);

	@Test
	void aFailedSyncIsRolledBackAndNeverReachesTheCoordinatorsSavedPlacement() {
		TransactionStatus status = mock(TransactionStatus.class);
		when(transactions.getTransaction(any())).thenReturn(status);
		when(programs.findByIdForUpdate(programId)).thenThrow(new IllegalStateException("database is gone"));

		assertThatCode(() -> service.onPlacementChanged(event)).doesNotThrowAnyException();
		verify(transactions).rollback(status);
	}

	@Test
	void aProgramThatIsGoneHasNothingToSync() {
		when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		when(programs.findByIdForUpdate(programId)).thenReturn(Optional.empty());

		service.onPlacementChanged(event);

		verifyNoInteractions(placements, channels);
	}
}
