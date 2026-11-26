package com.dps.teknokent.dto.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** "Programa ekle": the semester a student goes into; a student already in another one moves. */
public record SetStudentProgramRequest(UUID actorId, @NotNull UUID programId) {
}
