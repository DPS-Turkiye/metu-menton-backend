package com.dps.teknokent.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.dto.requests.SetStudentProgramRequest;
import com.dps.teknokent.dto.responses.StudentProgramChangeResponse;
import com.dps.teknokent.dto.responses.StudentProgramResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.AdminStudentProgramService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Kullanıcılar › Öğrenciler: a student's semester, admin only. */
@RestController
@RequestMapping("/api/admin/users/{userId}/program")
@RequiredArgsConstructor
@Tag(name = "Admin users")
public class AdminStudentProgramController {

	private final AdminStudentProgramService adminStudentProgramService;

	@GetMapping
	public StudentProgramResponse overview(@PathVariable UUID userId, @RequestParam(required = false) UUID actorId) {
		return adminStudentProgramService.overview(CurrentActor.resolve(actorId), userId);
	}

	/** A change revokes badges and permissions, so it takes the admin's own sign-in, as deleting a user does. */
	@PutMapping
	public StudentProgramChangeResponse move(@PathVariable UUID userId,
			@Valid @RequestBody SetStudentProgramRequest request) {
		return adminStudentProgramService.move(CurrentActor.requireSignedIn(), userId, request.programId());
	}

	@DeleteMapping
	public StudentProgramChangeResponse remove(@PathVariable UUID userId) {
		return adminStudentProgramService.remove(CurrentActor.requireSignedIn(), userId);
	}
}
