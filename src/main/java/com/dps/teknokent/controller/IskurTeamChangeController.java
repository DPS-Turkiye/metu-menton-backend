package com.dps.teknokent.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.dto.requests.IskurTeamChangeAskRequest;
import com.dps.teknokent.dto.requests.IskurTeamChangeDeclineRequest;
import com.dps.teknokent.dto.responses.IskurTeamChangeMineResponse;
import com.dps.teknokent.dto.responses.IskurTeamChangesResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurTeamChangeService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Team change requests: the intern asks from the team reveal, the program's coordinators answer in the İŞKUR
 * workspace (moving the intern there closes the request).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "İŞKUR team change requests")
public class IskurTeamChangeController {

	private final IskurTeamChangeService iskurTeamChangeService;

	@GetMapping("/api/iskur/team-change")
	public IskurTeamChangeMineResponse mine(@RequestParam(required = false) UUID actorId) {
		return iskurTeamChangeService.mine(CurrentActor.resolve(actorId));
	}

	@PostMapping("/api/iskur/team-change")
	public IskurTeamChangeMineResponse ask(@Valid @RequestBody IskurTeamChangeAskRequest request) {
		return iskurTeamChangeService.ask(CurrentActor.resolve(request.actorId()), request);
	}

	@GetMapping("/api/coordinator/programs/{programId}/iskur/team-changes")
	public IskurTeamChangesResponse list(@PathVariable UUID programId,
			@RequestParam(required = false) UUID actorId) {
		return iskurTeamChangeService.list(CurrentActor.resolve(actorId), programId);
	}

	@PostMapping("/api/coordinator/programs/{programId}/iskur/team-changes/{requestId}/decline")
	public IskurTeamChangesResponse decline(@PathVariable UUID programId, @PathVariable UUID requestId,
			@Valid @RequestBody IskurTeamChangeDeclineRequest request) {
		return iskurTeamChangeService.decline(CurrentActor.resolve(request.actorId()), programId, requestId,
				request);
	}
}
