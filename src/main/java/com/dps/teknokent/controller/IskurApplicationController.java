package com.dps.teknokent.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.domain.IskurDocumentSlot;
import com.dps.teknokent.dto.requests.IskurUnitSkillsRequest;
import com.dps.teknokent.dto.responses.IskurApplicationsResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurApplicationService;
import com.dps.teknokent.util.Downloads;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The coordinators' view of İŞKUR onboarding: the interns' forms, their files, and each unit's skills. */
@RestController
@RequestMapping("/api/coordinator/programs/{programId}/iskur")
@RequiredArgsConstructor
@Tag(name = "İŞKUR workspace")
public class IskurApplicationController {

	private final IskurApplicationService applicationService;

	@GetMapping("/applications")
	public IskurApplicationsResponse applications(@PathVariable UUID programId,
			@RequestParam(required = false) UUID actorId) {
		return applicationService.applications(CurrentActor.resolve(actorId), programId);
	}

	@GetMapping("/applications/{userId}/documents/{slot}")
	public ResponseEntity<byte[]> document(@PathVariable UUID programId, @PathVariable UUID userId,
			@PathVariable IskurDocumentSlot slot, @RequestParam(required = false) UUID actorId) {
		IskurApplicationService.DocumentContent file = applicationService.document(CurrentActor.resolve(actorId),
				programId, userId, slot);
		return Downloads.attachment(file.bytes(), file.filename(), file.contentType());
	}

	@PutMapping("/units/{unitId}/skills")
	public IskurApplicationsResponse replaceUnitSkills(@PathVariable UUID programId, @PathVariable UUID unitId,
			@Valid @RequestBody IskurUnitSkillsRequest request) {
		return applicationService.replaceUnitSkills(CurrentActor.resolve(request.actorId()), programId, unitId,
				request);
	}
}
