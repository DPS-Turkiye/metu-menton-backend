package com.dps.teknokent.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.dto.requests.ActorRequest;
import com.dps.teknokent.dto.requests.IskurSupportActivityRequest;
import com.dps.teknokent.dto.responses.IskurSupportActivitiesResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurSupportActivityService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** A peer program's support-activity pool, kept by its coordinators. Every call answers with the whole pool. */
@RestController
@RequestMapping("/api/coordinator/programs/{programId}/iskur/support-activities")
@RequiredArgsConstructor
@Tag(name = "İŞKUR support activities")
public class IskurSupportActivityController {

	private final IskurSupportActivityService iskurSupportActivityService;

	@GetMapping
	public IskurSupportActivitiesResponse list(@PathVariable UUID programId,
			@RequestParam(required = false) UUID actorId) {
		return iskurSupportActivityService.list(CurrentActor.resolve(actorId), programId);
	}

	@PostMapping("/default")
	public IskurSupportActivitiesResponse createDefaults(@PathVariable UUID programId,
			@Valid @RequestBody ActorRequest request) {
		return iskurSupportActivityService.createDefaults(CurrentActor.resolve(request.actorId()), programId);
	}

	@PostMapping
	public IskurSupportActivitiesResponse create(@PathVariable UUID programId,
			@Valid @RequestBody IskurSupportActivityRequest request) {
		return iskurSupportActivityService.create(CurrentActor.resolve(request.actorId()), programId, request);
	}

	@PutMapping("/{activityId}")
	public IskurSupportActivitiesResponse update(@PathVariable UUID programId, @PathVariable UUID activityId,
			@Valid @RequestBody IskurSupportActivityRequest request) {
		return iskurSupportActivityService.update(CurrentActor.resolve(request.actorId()), programId, activityId,
				request);
	}

	@DeleteMapping("/{activityId}")
	public IskurSupportActivitiesResponse delete(@PathVariable UUID programId, @PathVariable UUID activityId,
			@RequestParam(required = false) UUID actorId) {
		return iskurSupportActivityService.delete(CurrentActor.resolve(actorId), programId, activityId);
	}
}
