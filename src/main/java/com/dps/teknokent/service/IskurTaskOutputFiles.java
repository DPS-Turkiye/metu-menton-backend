package com.dps.teknokent.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.DocumentKind;
import com.dps.teknokent.dto.responses.StoredDocumentResponse;
import com.dps.teknokent.entity.IskurTaskOutput;
import com.dps.teknokent.entity.StoredDocument;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.IskurTaskOutputRepository;
import com.dps.teknokent.repository.StoredDocumentRepository;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The files handed in with İŞKUR tasks: storing, listing, reading and removing them. It keeps the rows and the stored
 * objects in step, and knows nothing of who may do what; every caller checks that first.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IskurTaskOutputFiles {

	public static final int MAX_FILES_PER_TASK = 5;

	private final IskurTaskOutputRepository outputs;
	private final StoredDocumentRepository storedDocuments;
	private final DocumentService documentService;
	private final ObjectStorage objectStorage;

	/** An output with the stored file behind it. */
	public record Entry(IskurTaskOutput output, StoredDocument file) {
	}

	/** False while no bucket is set: nothing can be uploaded or downloaded, and the screens hide the upload. */
	public boolean enabled() {
		return objectStorage.isConfigured();
	}

	/** Each task's files, oldest first; a task without any has no key. */
	public Map<UUID, List<Entry>> byTask(Collection<UUID> taskIds) {
		if (taskIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, List<Entry>> byTask = new HashMap<>();
		entries(outputs.findByTaskIdInOrderByCreatedAtAscIdAsc(taskIds))
				.forEach(entry -> byTask.computeIfAbsent(entry.output().getTaskId(), id -> new ArrayList<>())
						.add(entry));
		return byTask;
	}

	public List<Entry> ofTask(UUID taskId) {
		return byTask(List.of(taskId)).getOrDefault(taskId, List.of());
	}

	/** Every file of a program's tasks, newest first. */
	public List<Entry> ofProgram(UUID programId) {
		return entries(outputs.findByProgramId(programId));
	}

	public long count(UUID taskId) {
		return outputs.countByTaskId(taskId);
	}

	/**
	 * Stores the file and links it to the task. The format is read from the file's bytes (see
	 * {@link DocumentKind#workFiles()}). When the caller's transaction rolls back, the stored object goes too.
	 */
	public void add(UUID taskId, UUID uploaderId, @Nullable MultipartFile file, Instant now) {
		requireEnabled();
		if (file == null || file.isEmpty()) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurTaskOutputMissing"));
		}
		if (file.getSize() > DocumentKind.ISKUR_TASK_OUTPUT.maxBytes()) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurTaskOutputTooLarge"));
		}
		if (count(taskId) >= MAX_FILES_PER_TASK) {
			throw new ConflictException(Texts.forRequest("error.iskurTaskOutputLimit", MAX_FILES_PER_TASK));
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (IOException error) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurDocumentUnreadable"));
		}
		StoredDocumentResponse stored = documentService.store(uploaderId, null, DocumentKind.ISKUR_TASK_OUTPUT,
				file.getOriginalFilename(), file.getContentType(), bytes);
		StoredDocument row = storedDocuments.findById(stored.id())
				.orElseThrow(() -> new IllegalStateException("Stored document vanished: " + stored.id()));
		removeObjectIfRolledBack(row.getS3Key());
		outputs.save(new IskurTaskOutput(taskId, row.getId(), uploaderId, now));
	}

	/** The task's output with this id, or not found: an id of another task's file reveals nothing. */
	public Entry require(UUID taskId, UUID outputId) {
		return outputs.findByIdAndTaskId(outputId, taskId)
				.flatMap(output -> storedDocuments.findById(output.getDocumentId())
						.map(file -> new Entry(output, file)))
				.orElseThrow(IskurTaskOutputFiles::notFound);
	}

	public IskurApplicationService.DocumentContent content(Entry entry) {
		requireEnabled();
		StoredDocument file = entry.file();
		return new IskurApplicationService.DocumentContent(file.getOriginalFilename(), file.getContentType(),
				objectStorage.require(file.getS3Key()));
	}

	/** Removes the link and the file's row now, and the stored object once the caller's transaction commits. */
	public void remove(Entry entry) {
		outputs.delete(entry.output());
		storedDocuments.delete(entry.file());
		String key = entry.file().getS3Key();
		afterCommit(() -> deleteObject(key));
	}

	/** Everything handed in with a task that is being deleted. */
	public void removeAll(UUID taskId) {
		ofTask(taskId).forEach(this::remove);
	}

	/**
	 * Everything handed in with the tasks of teams that are being deleted: the database takes the tasks with their
	 * team, but the stored files would stay behind.
	 */
	public void removeAllOfTeams(Collection<UUID> teamIds) {
		if (!teamIds.isEmpty()) {
			entries(outputs.findByTeamIds(teamIds)).forEach(this::remove);
		}
	}

	static ResourceNotFoundException notFound() {
		return new ResourceNotFoundException(Texts.forRequest("error.iskurTaskOutputNotFound"));
	}

	/** 503 while no bucket is set. */
	public void requireEnabled() {
		if (!enabled()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					Texts.forRequest("error.iskurTaskOutputsOff"));
		}
	}

	private List<Entry> entries(List<IskurTaskOutput> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<UUID, StoredDocument> files = new HashMap<>();
		storedDocuments.findAllById(rows.stream().map(IskurTaskOutput::getDocumentId).toList())
				.forEach(file -> files.put(file.getId(), file));
		List<Entry> result = new ArrayList<>();
		for (IskurTaskOutput row : rows) {
			StoredDocument file = files.get(row.getDocumentId());
			if (file != null) {
				result.add(new Entry(row, file));
			}
		}
		return result;
	}

	private void removeObjectIfRolledBack(String s3Key) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status == STATUS_ROLLED_BACK) {
					deleteObject(s3Key);
				}
			}
		});
	}

	private static void afterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}

	private void deleteObject(String s3Key) {
		try {
			objectStorage.delete(s3Key);
		} catch (RuntimeException error) {
			log.warn("İŞKUR task output object {} remains in storage: {}", s3Key, error.getMessage());
		}
	}
}
