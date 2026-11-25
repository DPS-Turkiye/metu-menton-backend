package com.dps.teknokent.dto.requests;

import java.util.UUID;

import com.dps.teknokent.domain.IskurDocumentSlot;
import com.dps.teknokent.domain.PeerDocumentVisibility;

import jakarta.validation.constraints.NotNull;

/** "Formundaki dosyayı kullan": copies the form's file to the mentor profile, seen by whom the mentor chose. */
public record UseFormDocumentRequest(UUID actorId, @NotNull IskurDocumentSlot slot,
		@NotNull PeerDocumentVisibility visibility) {
}
