package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The intern's answer to a revision or a rejection ("Öğrenci gerekçeyi görür ve itiraz edebilir"). */
public record IskurWeekObjectionRequest(@NotNull UUID actorId, @NotBlank @Size(max = 500) String objection) {
}
