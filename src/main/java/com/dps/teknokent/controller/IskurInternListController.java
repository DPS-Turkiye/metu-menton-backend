package com.dps.teknokent.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.dto.requests.IskurInternEmailsRequest;
import com.dps.teknokent.dto.responses.IskurInternListResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurInternListService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The coordinator's İŞKUR intern list of e-mail addresses; since 27 Nov it no longer decides who is an intern.
 * Every call answers with the whole list. Addresses are removed by their entry id, so none travels in a URL.
 */
@RestController
@RequestMapping("/api/coordinator/programs/{programId}/iskur/interns")
@RequiredArgsConstructor
@Tag(name = "İŞKUR workspace")
public class IskurInternListController {

	private final IskurInternListService iskurInternListService;

	@GetMapping
	public IskurInternListResponse list(@PathVariable UUID programId, @RequestParam(required = false) UUID actorId) {
		return iskurInternListService.list(CurrentActor.resolve(actorId), programId);
	}

	@PostMapping
	public IskurInternListResponse add(@PathVariable UUID programId,
			@Valid @RequestBody IskurInternEmailsRequest request) {
		return iskurInternListService.add(CurrentActor.resolve(request.actorId()), programId, request);
	}

	@DeleteMapping("/{entryId}")
	public IskurInternListResponse remove(@PathVariable UUID programId, @PathVariable UUID entryId,
			@RequestParam(required = false) UUID actorId) {
		return iskurInternListService.remove(CurrentActor.resolve(actorId), programId, entryId);
	}
}
