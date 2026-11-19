package com.dps.teknokent.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.dto.requests.IskurWeekDecisionRequest;
import com.dps.teknokent.dto.responses.IskurLeaderQueueResponse;
import com.dps.teknokent.dto.responses.IskurLeaderWeekResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurLeaderReviewService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The coordinators' review of the weeks no team leader can review: a one-leader team's leader and its leaderless. */
@RestController
@RequestMapping("/api/coordinator/programs/{programId}/iskur/weeks")
@RequiredArgsConstructor
@Tag(name = "İŞKUR weekly work")
public class IskurCoordinatorWeekController {

	private final IskurLeaderReviewService iskurLeaderReviewService;

	@GetMapping
	public IskurLeaderQueueResponse queue(@PathVariable UUID programId, @RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
		return iskurLeaderReviewService.coordinatorQueue(CurrentActor.resolve(actorId), programId, weekStart);
	}

	@GetMapping("/{weekId}")
	public IskurLeaderWeekResponse week(@PathVariable UUID programId, @PathVariable UUID weekId,
			@RequestParam(required = false) UUID actorId) {
		return iskurLeaderReviewService.coordinatorWeek(CurrentActor.resolve(actorId), programId, weekId);
	}

	@PostMapping("/{weekId}/decision")
	public IskurLeaderWeekResponse decide(@PathVariable UUID programId, @PathVariable UUID weekId,
			@Valid @RequestBody IskurWeekDecisionRequest request) {
		return iskurLeaderReviewService.coordinatorDecide(CurrentActor.resolve(request.actorId()), programId, weekId,
				request);
	}
}
