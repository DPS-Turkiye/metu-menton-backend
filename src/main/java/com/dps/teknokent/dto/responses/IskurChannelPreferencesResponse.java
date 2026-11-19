package com.dps.teknokent.dto.responses;

import java.util.List;
import java.util.UUID;

import com.dps.teknokent.domain.IskurChannelKind;

/** The kinds of channel the reader turned notifications off for in a program (6.1 Bildirimler). */
public record IskurChannelPreferencesResponse(UUID programId, List<IskurChannelKind> mutedKinds) {
}
