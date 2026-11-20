package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class IskurChannelRealtimeAccessServiceTest {

	@Mock
	private IskurChannelService channels;
	@Mock
	private AppUserRepository users;

	private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000201");
	private final UUID channelId = UUID.fromString("00000000-0000-0000-0000-000000000c01");
	private final Instant signedInAt = Instant.parse("2026-11-20T09:00:00Z");

	@Test
	void followsWhatTheRestReadsWouldShow() {
		when(users.findById(userId)).thenReturn(Optional.of(new AppUser()));
		when(channels.canReach(userId, channelId)).thenReturn(true, false);

		assertThat(odtu().mayFollow(userId, channelId, signedInAt)).isTrue();
		assertThat(odtu().mayFollow(userId, channelId, signedInAt)).isFalse();
	}

	@Test
	void theTeknokentEditionHasNoIskurTopics() {
		IskurChannelRealtimeAccessService teknokent = new IskurChannelRealtimeAccessService(channels, users,
				new EditionService("TEKNOKENT"));

		assertThat(teknokent.mayFollow(userId, channelId, signedInAt)).isFalse();
		verifyNoInteractions(channels, users);
	}

	@Test
	void anAccountThatNoLongerExistsFollowsNothing() {
		when(users.findById(userId)).thenReturn(Optional.empty());

		assertThat(odtu().mayFollow(userId, channelId, signedInAt)).isFalse();
		verifyNoInteractions(channels);
	}

	// An admin's "end all sign-ins" closes the sockets of every sign-in that began before it.
	@Test
	void aSignInEndedByAnAdminFollowsNothing() {
		AppUser revoked = new AppUser();
		revoked.setSignInsRevokedAt(signedInAt.plusSeconds(30));
		when(users.findById(userId)).thenReturn(Optional.of(revoked));

		assertThat(odtu().mayFollow(userId, channelId, signedInAt)).isFalse();
		verifyNoInteractions(channels);
	}

	@Test
	void aSignInThatBeganAfterTheRevokeFollowsAgain() {
		AppUser revokedEarlier = new AppUser();
		revokedEarlier.setSignInsRevokedAt(signedInAt.minusSeconds(30));
		when(users.findById(userId)).thenReturn(Optional.of(revokedEarlier));
		when(channels.canReach(userId, channelId)).thenReturn(true);

		assertThat(odtu().mayFollow(userId, channelId, signedInAt)).isTrue();
	}

	private IskurChannelRealtimeAccessService odtu() {
		return new IskurChannelRealtimeAccessService(channels, users, new EditionService("ODTU"));
	}
}
