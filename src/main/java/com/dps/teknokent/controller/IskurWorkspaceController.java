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
import com.dps.teknokent.dto.requests.IskurFinalizeRequest;
import com.dps.teknokent.dto.requests.IskurPlacementRequest;
import com.dps.teknokent.dto.requests.IskurTeamRequest;
import com.dps.teknokent.dto.requests.IskurUnitRequest;
import com.dps.teknokent.dto.responses.IskurWorkspaceResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurWorkspaceService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The coordinator's İŞKUR placement screen: units, thematic teams and who works where. Every call answers with the
 * whole workspace.
 */
@RestController
@RequestMapping("/api/coordinator/programs/{programId}/iskur")
@RequiredArgsConstructor
@Tag(name = "İŞKUR workspace")
public class IskurWorkspaceController {

	private final IskurWorkspaceService iskurWorkspaceService;

	@GetMapping("/workspace")
	public IskurWorkspaceResponse workspace(@PathVariable UUID programId,
			@RequestParam(required = false) UUID actorId) {
		return iskurWorkspaceService.workspace(CurrentActor.resolve(actorId), programId);
	}

	@PostMapping("/structure/default")
	public IskurWorkspaceResponse createDefaultStructure(@PathVariable UUID programId,
			@Valid @RequestBody ActorRequest request) {
		return iskurWorkspaceService.createDefaultStructure(CurrentActor.resolve(request.actorId()), programId);
	}

	@PostMapping("/units")
	public IskurWorkspaceResponse createUnit(@PathVariable UUID programId,
			@Valid @RequestBody IskurUnitRequest request) {
		return iskurWorkspaceService.createUnit(CurrentActor.resolve(request.actorId()), programId, request);
	}

	@PutMapping("/units/{unitId}")
	public IskurWorkspaceResponse updateUnit(@PathVariable UUID programId, @PathVariable UUID unitId,
			@Valid @RequestBody IskurUnitRequest request) {
		return iskurWorkspaceService.updateUnit(CurrentActor.resolve(request.actorId()), programId, unitId, request);
	}

	@DeleteMapping("/units/{unitId}")
	public IskurWorkspaceResponse deleteUnit(@PathVariable UUID programId, @PathVariable UUID unitId,
			@RequestParam(required = false) UUID actorId) {
		return iskurWorkspaceService.deleteUnit(CurrentActor.resolve(actorId), programId, unitId);
	}

	@PostMapping("/units/{unitId}/teams")
	public IskurWorkspaceResponse createTeam(@PathVariable UUID programId, @PathVariable UUID unitId,
			@Valid @RequestBody IskurTeamRequest request) {
		return iskurWorkspaceService.createTeam(CurrentActor.resolve(request.actorId()), programId, unitId, request);
	}

	@PutMapping("/teams/{teamId}")
	public IskurWorkspaceResponse updateTeam(@PathVariable UUID programId, @PathVariable UUID teamId,
			@Valid @RequestBody IskurTeamRequest request) {
		return iskurWorkspaceService.updateTeam(CurrentActor.resolve(request.actorId()), programId, teamId, request);
	}

	@DeleteMapping("/teams/{teamId}")
	public IskurWorkspaceResponse deleteTeam(@PathVariable UUID programId, @PathVariable UUID teamId,
			@RequestParam(required = false) UUID actorId) {
		return iskurWorkspaceService.deleteTeam(CurrentActor.resolve(actorId), programId, teamId);
	}

	@PutMapping("/placements/{userId}")
	public IskurWorkspaceResponse place(@PathVariable UUID programId, @PathVariable UUID userId,
			@Valid @RequestBody IskurPlacementRequest request) {
		return iskurWorkspaceService.place(CurrentActor.resolve(request.actorId()), programId, userId, request);
	}

	@DeleteMapping("/placements/{userId}")
	public IskurWorkspaceResponse unplace(@PathVariable UUID programId, @PathVariable UUID userId,
			@RequestParam(required = false) UUID actorId) {
		return iskurWorkspaceService.unplace(CurrentActor.resolve(actorId), programId, userId);
	}

	@PostMapping("/placements/finalize")
	public IskurWorkspaceResponse finalizePlacements(@PathVariable UUID programId,
			@Valid @RequestBody IskurFinalizeRequest request) {
		return iskurWorkspaceService.finalizePlacements(CurrentActor.resolve(request.actorId()), programId, request);
	}
}
