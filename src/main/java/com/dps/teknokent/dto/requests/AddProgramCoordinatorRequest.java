package com.dps.teknokent.dto.requests;

import java.util.Set;
import java.util.UUID;

import com.dps.teknokent.domain.ProgramFeature;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An existing coordinator by {@code userId}, or an email to invite as in "Yeni Program Oluştur"; exactly one.
 * {@code features}: what the coordinator runs in a semester; every feature it has when left out.
 */
public record AddProgramCoordinatorRequest(UUID actorId, UUID userId, @Valid NewCoordinatorInput invite,
		@Size(max = 3) Set<@NotNull ProgramFeature> features) {

	public AddProgramCoordinatorRequest(UUID actorId, UUID userId, NewCoordinatorInput invite) {
		this(actorId, userId, invite, null);
	}

	@JsonIgnore
	@AssertTrue(message = "Send either userId or invite.")
	public boolean isOneCoordinator() {
		return (userId == null) != (invite == null);
	}
}
