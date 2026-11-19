package com.dps.teknokent.service;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IskurPlacementStatus;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.entity.IskurPlacement;

/**
 * Published inside the transaction whenever an intern's İŞKUR placement changes, is finalized or is removed (then
 * {@code teamId}, {@code status} and {@code role} are null). Listeners that act on it, like the team channels, use
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so a rolled-back change opens nothing. When nothing
 * moved (a finalize, an edit in the same team, a member whose leader was released) {@code previousTeamId} equals
 * {@code teamId}; it is null only for someone placed for the first time.
 */
public record IskurPlacementChangedEvent(
		UUID programId,
		UUID userId,
		@Nullable UUID previousTeamId,
		@Nullable UUID teamId,
		@Nullable IskurPlacementStatus status,
		@Nullable IskurTeamRole role,
		@Nullable UUID leaderId) {

	static IskurPlacementChangedEvent of(IskurPlacement placement, @Nullable UUID previousTeamId) {
		return new IskurPlacementChangedEvent(placement.getProgramId(), placement.getUserId(), previousTeamId,
				placement.getTeamId(), placement.getStatus(), placement.getTeamRole(), placement.getLeaderId());
	}

	static IskurPlacementChangedEvent removed(IskurPlacement placement) {
		return new IskurPlacementChangedEvent(placement.getProgramId(), placement.getUserId(), placement.getTeamId(),
				null, null, null, null);
	}
}
