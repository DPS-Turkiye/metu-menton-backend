package com.dps.teknokent.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.domain.OfficeHoursRequestStatus;
import com.dps.teknokent.dto.requests.ProgramOfficeHoursAppointmentsQuery;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentResponse;
import com.dps.teknokent.dto.responses.ProgramOfficeHoursAppointmentsResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.ProgramOfficeHoursService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/coordinator/programs/{programId}/office-hours/appointments")
@Tag(name = "Office Hours appointments for coordinators")
public class ProgramOfficeHoursController {

	private final ProgramOfficeHoursService programOfficeHoursService;

	@GetMapping
	public ProgramOfficeHoursAppointmentsResponse list(@PathVariable UUID programId,
			@RequestParam(required = false) @Nullable UUID actorId,
			@RequestParam(required = false) @Nullable OfficeHoursRequestStatus status,
			@RequestParam(required = false) @Nullable String faculty,
			@RequestParam(required = false) @Nullable String search,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) @Nullable LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) @Nullable LocalDate to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return programOfficeHoursService.list(CurrentActor.resolve(actorId), programId,
				new ProgramOfficeHoursAppointmentsQuery(status, faculty, search, from, to, page, size));
	}

	@GetMapping("/{requestId}")
	public ProgramOfficeHoursAppointmentResponse detail(@PathVariable UUID programId, @PathVariable UUID requestId,
			@RequestParam(required = false) @Nullable UUID actorId) {
		return programOfficeHoursService.detail(CurrentActor.resolve(actorId), programId, requestId);
	}
}
