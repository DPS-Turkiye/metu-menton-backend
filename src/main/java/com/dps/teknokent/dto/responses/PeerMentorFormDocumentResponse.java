package com.dps.teknokent.dto.responses;

import com.dps.teknokent.domain.IskurDocumentSlot;

/** A file the mentor already gave with their İŞKUR form, offered to their mentor profile. */
public record PeerMentorFormDocumentResponse(IskurDocumentSlot slot, String originalFilename, long sizeBytes) {
}
