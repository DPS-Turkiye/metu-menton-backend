package com.dps.teknokent.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.Edition;
import com.dps.teknokent.repository.AppUserRepository;

import lombok.RequiredArgsConstructor;

/** Who may listen to an İŞKUR channel's live topic. */
@Service
@RequiredArgsConstructor
public class IskurChannelRealtimeAccessService {

	private final IskurChannelService channels;
	private final AppUserRepository users;
	private final EditionService editions;

	/**
	 * Whether a socket for a sign-in that began at {@code signedInAt} may follow the channel: only on the ODTÜ
	 * edition, only while no admin ended the account's sign-ins since, and only for someone the REST reads would
	 * show the channel to right now ({@link IskurChannelService#canReach}).
	 */
	@Transactional(readOnly = true)
	public boolean mayFollow(UUID userId, UUID channelId, Instant signedInAt) {
		if (editions.current() != Edition.ODTU) {
			return false;
		}
		boolean stillSignedIn = users.findById(userId)
				.filter(user -> user.getSignInsRevokedAt() == null
						|| user.getSignInsRevokedAt().isBefore(signedInAt))
				.isPresent();
		return stillSignedIn && channels.canReach(userId, channelId);
	}
}
