package com.dps.teknokent.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.dto.requests.ActorRequest;
import com.dps.teknokent.dto.requests.IskurSupportEntryRequest;
import com.dps.teknokent.dto.requests.IskurWeekDecisionRequest;
import com.dps.teknokent.dto.requests.IskurWeekObjectionRequest;
import com.dps.teknokent.dto.requests.IskurWorkEntriesRequest;
import com.dps.teknokent.dto.responses.IskurLeaderQueueResponse;
import com.dps.teknokent.dto.responses.IskurLeaderWeekResponse;
import com.dps.teknokent.dto.responses.IskurWeekResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurLeaderReviewService;
import com.dps.teknokent.service.IskurWorkWeekService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** An intern's weekly log (Bu Haftam, Çalışma kaydı) and the leader's weekly review. */
@RestController
@RequestMapping("/api/iskur")
@RequiredArgsConstructor
@Tag(name = "İŞKUR weekly work")
public class IskurWeekController {

	private final IskurWorkWeekService iskurWorkWeekService;
	private final IskurLeaderReviewService iskurLeaderReviewService;

	@GetMapping("/week")
	public IskurWeekResponse week(@RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
		return iskurWorkWeekService.week(CurrentActor.resolve(actorId), weekStart);
	}

	@PutMapping("/week/{weekStart}/entries")
	public IskurWeekResponse saveEntries(
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@Valid @RequestBody IskurWorkEntriesRequest request) {
		return iskurWorkWeekService.saveEntries(CurrentActor.resolve(request.actorId()), weekStart, request);
	}

	@PostMapping("/week/{weekStart}/support-activity")
	public IskurWeekResponse addSupportActivity(
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@Valid @RequestBody IskurSupportEntryRequest request) {
		return iskurWorkWeekService.addSupportActivity(CurrentActor.resolve(request.actorId()), weekStart, request);
	}

	@PostMapping("/week/{weekStart}/submit")
	public IskurWeekResponse submit(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@Valid @RequestBody ActorRequest request) {
		return iskurWorkWeekService.submit(CurrentActor.resolve(request.actorId()), weekStart);
	}

	@PostMapping("/week/{weekStart}/objection")
	public IskurWeekResponse object(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
			@Valid @RequestBody IskurWeekObjectionRequest request) {
		return iskurWorkWeekService.object(CurrentActor.resolve(request.actorId()), weekStart, request);
	}

	@GetMapping("/leader/weeks")
	public IskurLeaderQueueResponse queue(@RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
		return iskurLeaderReviewService.queue(CurrentActor.resolve(actorId), weekStart);
	}

	@GetMapping("/leader/weeks/{weekId}")
	public IskurLeaderWeekResponse leaderWeek(@PathVariable UUID weekId,
			@RequestParam(required = false) UUID actorId) {
		return iskurLeaderReviewService.week(CurrentActor.resolve(actorId), weekId);
	}

	@PostMapping("/leader/weeks/{weekId}/decision")
	public IskurLeaderWeekResponse decide(@PathVariable UUID weekId,
			@Valid @RequestBody IskurWeekDecisionRequest request) {
		return iskurLeaderReviewService.decide(CurrentActor.resolve(request.actorId()), weekId, request);
	}
}
