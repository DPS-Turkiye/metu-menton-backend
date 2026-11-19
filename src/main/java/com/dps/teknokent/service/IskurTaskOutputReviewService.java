package com.dps.teknokent.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.dto.responses.IskurTaskOutputsResponse;
import com.dps.teknokent.dto.responses.IskurTaskResponse;
import com.dps.teknokent.entity.IskurTask;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurTaskRepository;
import com.dps.teknokent.repository.IskurTeamRepository;

import lombok.RequiredArgsConstructor;

/**
 * The coordinators' side of İŞKUR task outputs: every file handed in with the program's tasks, to list and download.
 * Only the program's coordinators and the admin reach it; another program's coordinator is refused like everywhere
 * else in the workspace.
 */
@Service
@RequiredArgsConstructor
public class IskurTaskOutputReviewService {

	private final IskurWorkspaceService workspaceService;
	private final IskurTaskOutputFiles outputFiles;
	private final IskurTaskRepository tasks;
	private final IskurTeamRepository teams;
	private final AppUserRepository users;

	@Transactional(readOnly = true)
	public IskurTaskOutputsResponse outputs(UUID actorId, UUID programId) {
		Program program = workspaceService.requireProgram(actorId, programId);
		// Newest file first, so a task takes the place of its newest file.
		Map<UUID, List<IskurTaskOutputFiles.Entry>> byTask = new LinkedHashMap<>();
		outputFiles.ofProgram(program.getId())
				.forEach(entry -> byTask.computeIfAbsent(entry.output().getTaskId(), id -> new ArrayList<>())
						.add(entry));
		Map<UUID, IskurTask> taskById = new HashMap<>();
		tasks.findAllById(byTask.keySet()).forEach(task -> taskById.put(task.getId(), task));
		Map<UUID, String> teamNames = new HashMap<>();
		teams.findAllById(taskById.values().stream().map(IskurTask::getTeamId).distinct().toList())
				.forEach(team -> teamNames.put(team.getId(), team.getName()));
		Map<UUID, String> people = new HashMap<>();
		users.findAllById(taskById.values().stream().map(IskurTask::getAssigneeId).distinct().toList())
				.forEach(user -> people.put(user.getId(), user.getFullName()));

		List<IskurTaskOutputsResponse.Task> rows = new ArrayList<>();
		byTask.forEach((taskId, entries) -> {
			IskurTask task = taskById.get(taskId);
			if (task != null) {
				rows.add(new IskurTaskOutputsResponse.Task(task.getId(), task.getTitle(),
						teamNames.getOrDefault(task.getTeamId(), ""),
						new IskurTaskResponse.Person(task.getAssigneeId(),
								people.getOrDefault(task.getAssigneeId(), "")),
						task.getStatus(), task.getDueDate(),
						entries.stream()
								.map(entry -> new IskurTaskOutputsResponse.File(entry.output().getId(),
										entry.file().getOriginalFilename(), entry.file().getContentType(),
										entry.file().getSizeBytes(), entry.output().getCreatedAt()))
								.toList()));
			}
		});
		return new IskurTaskOutputsResponse(program.getId(), outputFiles.enabled(), rows);
	}

	/** One handed-in file of this program; a file of another program's task reads as missing. */
	@Transactional(readOnly = true)
	public IskurApplicationService.DocumentContent output(UUID actorId, UUID programId, UUID taskId,
			UUID outputId) {
		Program program = workspaceService.requireProgram(actorId, programId);
		IskurTask task = tasks.findById(taskId)
				.filter(found -> found.getProgramId().equals(program.getId()))
				.orElseThrow(IskurTaskOutputFiles::notFound);
		return outputFiles.content(outputFiles.require(task.getId(), outputId));
	}
}
