package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One activity of the support pool; {@code active} null keeps the current value (a new one starts active). */
public record IskurSupportActivityRequest(
		@NotNull UUID actorId,
		@NotBlank @Size(max = 120) String title,
		@Size(max = 500) String description,
		Boolean active) {
}
