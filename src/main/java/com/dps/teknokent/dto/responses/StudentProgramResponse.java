package com.dps.teknokent.dto.responses;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.ProgramBlocker;
import com.dps.teknokent.domain.ProgramStatus;

/**
 * What the admin's "Programa ekle / Programdan çıkar" dialog shows before confirming: the student's semester (none
 * when {@code current} is null), the semesters they can go into, and the open work that stops them leaving.
 */
public record StudentProgramResponse(@Nullable ProgramRef current, List<ProgramRef> targets,
		List<ProgramBlocker> blockers) {

	public record ProgramRef(UUID id, String name, ProgramStatus status) {
	}
}
