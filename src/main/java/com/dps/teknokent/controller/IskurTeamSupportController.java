package com.dps.teknokent.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.dto.requests.IskurTeamSupportAnswerRequest;
import com.dps.teknokent.dto.requests.IskurTeamSupportOpenRequest;
import com.dps.teknokent.dto.responses.IskurTeamSupportResponse;
import com.dps.teknokent.dto.responses.IskurTeamSupportsResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurTeamSupportService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Support requests between İŞKUR teams: a team leader asks another team, that team's leader answers. */
@RestController
@RequestMapping("/api/iskur")
@RequiredArgsConstructor
@Tag(name = "İŞKUR team support requests")
public class IskurTeamSupportController {

	private final IskurTeamSupportService iskurTeamSupportService;

	@GetMapping("/team-support")
	public IskurTeamSupportsResponse mine(@RequestParam(required = false) UUID actorId) {
		return iskurTeamSupportService.mine(CurrentActor.resolve(actorId));
	}

	@PostMapping("/team-support")
	public IskurTeamSupportsResponse open(@Valid @RequestBody IskurTeamSupportOpenRequest request) {
		return iskurTeamSupportService.open(CurrentActor.resolve(request.actorId()), request);
	}

	@PostMapping("/team-support/{requestId}/accept")
	public IskurTeamSupportsResponse accept(@PathVariable UUID requestId,
			@Valid @RequestBody IskurTeamSupportAnswerRequest request) {
		return iskurTeamSupportService.answer(CurrentActor.resolve(request.actorId()), requestId, true, request);
	}

	@PostMapping("/team-support/{requestId}/decline")
	public IskurTeamSupportsResponse decline(@PathVariable UUID requestId,
			@Valid @RequestBody IskurTeamSupportAnswerRequest request) {
		return iskurTeamSupportService.answer(CurrentActor.resolve(request.actorId()), requestId, false, request);
	}

	@GetMapping("/tasks/{taskId}/team-support")
	public List<IskurTeamSupportResponse> forTask(@PathVariable UUID taskId,
			@RequestParam(required = false) UUID actorId) {
		return iskurTeamSupportService.forTask(CurrentActor.resolve(actorId), taskId);
	}
}
