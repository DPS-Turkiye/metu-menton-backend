package com.dps.teknokent.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.dto.responses.IskurTaskOutputsResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurApplicationService;
import com.dps.teknokent.service.IskurTaskOutputReviewService;
import com.dps.teknokent.util.Downloads;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** The coordinators' view of the files interns hand in with their İŞKUR tasks. */
@RestController
@RequestMapping("/api/coordinator/programs/{programId}/iskur/task-outputs")
@RequiredArgsConstructor
@Tag(name = "İŞKUR tasks")
public class IskurCoordinatorTaskOutputController {

	private final IskurTaskOutputReviewService reviewService;

	@GetMapping
	public IskurTaskOutputsResponse outputs(@PathVariable UUID programId,
			@RequestParam(required = false) UUID actorId) {
		return reviewService.outputs(CurrentActor.resolve(actorId), programId);
	}

	@GetMapping("/{taskId}/{outputId}")
	public ResponseEntity<byte[]> output(@PathVariable UUID programId, @PathVariable UUID taskId,
			@PathVariable UUID outputId, @RequestParam(required = false) UUID actorId) {
		IskurApplicationService.DocumentContent file = reviewService.output(CurrentActor.resolve(actorId), programId,
				taskId, outputId);
		return Downloads.attachment(file.bytes(), file.filename(), file.contentType());
	}
}
