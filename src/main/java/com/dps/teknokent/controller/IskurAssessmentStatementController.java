package com.dps.teknokent.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.dto.requests.IskurAssessmentDecisionRequest;
import com.dps.teknokent.dto.requests.IskurAssessmentStatementRequest;
import com.dps.teknokent.dto.responses.IskurAssessmentMineResponse;
import com.dps.teknokent.dto.responses.IskurAssessmentStatementsResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurAssessmentStatementService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Assessment statements: an intern without the badge explains why they could not finish or pass the assessment; the
 * program's coordinators answer in the İŞKUR workspace.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "İŞKUR assessment statements")
public class IskurAssessmentStatementController {

	private final IskurAssessmentStatementService service;

	@GetMapping("/api/iskur/assessment-statement")
	public IskurAssessmentMineResponse mine(@RequestParam(required = false) UUID actorId) {
		return service.mine(CurrentActor.resolve(actorId));
	}

	@PostMapping("/api/iskur/assessment-statement")
	public IskurAssessmentMineResponse send(@Valid @RequestBody IskurAssessmentStatementRequest request) {
		return service.send(CurrentActor.resolve(request.actorId()), request);
	}

	@GetMapping("/api/coordinator/programs/{programId}/iskur/assessment-statements")
	public IskurAssessmentStatementsResponse list(@PathVariable UUID programId,
			@RequestParam(required = false) UUID actorId) {
		return service.list(CurrentActor.resolve(actorId), programId);
	}

	@PostMapping("/api/coordinator/programs/{programId}/iskur/assessment-statements/{statementId}/decide")
	public IskurAssessmentStatementsResponse decide(@PathVariable UUID programId, @PathVariable UUID statementId,
			@Valid @RequestBody IskurAssessmentDecisionRequest request) {
		return service.decide(CurrentActor.resolve(request.actorId()), programId, statementId, request);
	}
}
