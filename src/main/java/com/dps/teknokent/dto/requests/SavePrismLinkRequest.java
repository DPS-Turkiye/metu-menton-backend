package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The program's Prism training link (https only); {@code actorId} comes from the proxy's session. */
public record SavePrismLinkRequest(UUID actorId, @NotBlank @Size(max = 500) String url) {
}
