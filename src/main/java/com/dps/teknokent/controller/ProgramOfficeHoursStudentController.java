package com.dps.teknokent.controller;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.dto.requests.ProgramOfficeHoursStudentsQuery;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursStudentsResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.ProgramOfficeHoursService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Office Hours students for coordinators")
public class ProgramOfficeHoursStudentController {

	private final ProgramOfficeHoursService programOfficeHoursService;

	@GetMapping("/api/coordinator/programs/{programId}/office-hours/students")
	public ProgramOfficeHoursStudentsResponse students(@PathVariable UUID programId,
			@RequestParam(required = false) @Nullable UUID actorId,
			@RequestParam(required = false) @Nullable String search,
			@RequestParam(required = false) @Nullable String faculty,
			@RequestParam(defaultValue = "false") boolean awaitingOnly,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return programOfficeHoursService.students(CurrentActor.resolve(actorId), programId,
				new ProgramOfficeHoursStudentsQuery(search, faculty, awaitingOnly, page, size));
	}
}
