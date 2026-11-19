package com.dps.teknokent.dto.requests;

import java.util.List;
import java.util.UUID;

import com.dps.teknokent.domain.IskurChannelKind;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Replaces the muted kinds of channel for one program. */
public record IskurChannelPreferencesRequest(
		@NotNull UUID actorId,
		@NotNull UUID programId,
		@NotNull @Size(max = 4) List<@NotNull IskurChannelKind> mutedKinds) {
}
