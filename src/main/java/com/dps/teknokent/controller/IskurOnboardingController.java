package com.dps.teknokent.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dps.teknokent.domain.IskurDocumentSlot;
import com.dps.teknokent.dto.requests.IskurOnboardingRequest;
import com.dps.teknokent.dto.responses.IskurOnboardingResponse;
import com.dps.teknokent.dto.responses.IskurTeamRevealResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurOnboardingService;
import com.dps.teknokent.service.IskurTeamRevealService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The intern's side of the İŞKUR workspace: the onboarding form and their team. ODTÜ only. */
@RestController
@RequestMapping("/api/iskur")
@RequiredArgsConstructor
@Tag(name = "İŞKUR intern")
public class IskurOnboardingController {

	private final IskurOnboardingService onboardingService;
	private final IskurTeamRevealService teamRevealService;

	@GetMapping("/onboarding")
	public IskurOnboardingResponse onboarding(@RequestParam(required = false) UUID actorId) {
		return onboardingService.get(CurrentActor.resolve(actorId));
	}

	@PutMapping("/onboarding")
	public IskurOnboardingResponse saveDraft(@Valid @RequestBody IskurOnboardingRequest request) {
		return onboardingService.saveDraft(CurrentActor.resolve(request.actorId()), request);
	}

	@PostMapping("/onboarding/submit")
	public IskurOnboardingResponse submit(@Valid @RequestBody IskurOnboardingRequest request) {
		return onboardingService.submit(CurrentActor.resolve(request.actorId()), request);
	}

	@PostMapping(value = "/onboarding/documents/{slot}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public IskurOnboardingResponse upload(@PathVariable IskurDocumentSlot slot,
			@RequestParam(required = false) UUID actorId,
			@RequestPart(value = "file", required = false) MultipartFile file) {
		return onboardingService.upload(CurrentActor.resolve(actorId), slot, file);
	}

	@DeleteMapping("/onboarding/documents/{slot}")
	public IskurOnboardingResponse removeDocument(@PathVariable IskurDocumentSlot slot,
			@RequestParam(required = false) UUID actorId) {
		return onboardingService.removeDocument(CurrentActor.resolve(actorId), slot);
	}

	@GetMapping("/me")
	public IskurTeamRevealResponse me(@RequestParam(required = false) UUID actorId) {
		return teamRevealService.me(CurrentActor.resolve(actorId));
	}
}
