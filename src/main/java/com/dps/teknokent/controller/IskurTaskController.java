package com.dps.teknokent.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dps.teknokent.dto.requests.IskurTaskRequest;
import com.dps.teknokent.dto.requests.IskurTaskUpdateRequest;
import com.dps.teknokent.dto.responses.IskurTaskResponse;
import com.dps.teknokent.dto.responses.IskurTeamTasksResponse;
import com.dps.teknokent.security.CurrentActor;
import com.dps.teknokent.service.IskurTaskService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** An intern's team tasks: Ekibim's task list and "Görev ata" for leaders, and one task's detail for its assignee. */
@RestController
@RequestMapping("/api/iskur")
@RequiredArgsConstructor
@Tag(name = "İŞKUR tasks")
public class IskurTaskController {

	private final IskurTaskService iskurTaskService;

	@GetMapping("/team/tasks")
	public IskurTeamTasksResponse teamTasks(@RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
		return iskurTaskService.teamTasks(CurrentActor.resolve(actorId), weekStart);
	}

	@PostMapping("/team/tasks")
	public IskurTeamTasksResponse assign(@Valid @RequestBody IskurTaskRequest request) {
		return iskurTaskService.create(CurrentActor.resolve(request.actorId()), request);
	}

	@GetMapping("/tasks/{taskId}")
	public IskurTaskResponse task(@PathVariable UUID taskId, @RequestParam(required = false) UUID actorId) {
		return iskurTaskService.task(CurrentActor.resolve(actorId), taskId);
	}

	@PatchMapping("/tasks/{taskId}")
	public IskurTaskResponse update(@PathVariable UUID taskId, @Valid @RequestBody IskurTaskUpdateRequest request) {
		return iskurTaskService.update(CurrentActor.resolve(request.actorId()), taskId, request);
	}

	@DeleteMapping("/tasks/{taskId}")
	public IskurTeamTasksResponse delete(@PathVariable UUID taskId, @RequestParam(required = false) UUID actorId) {
		return iskurTaskService.delete(CurrentActor.resolve(actorId), taskId);
	}
}
