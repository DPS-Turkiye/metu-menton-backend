package com.dps.teknokent.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dps.teknokent.domain.DocumentKind;
import com.dps.teknokent.domain.ProgramMentorStatus;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramMentor;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramMentorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.repository.StoredDocumentRepository;
import com.dps.teknokent.util.AvatarImages;
import com.dps.teknokent.util.ProgramCoordination;

import lombok.RequiredArgsConstructor;

/**
 * A person's profile photo, small enough for an avatar, for the people who may see it. Every "no" is the same "not
 * found" (no such person, no photo, or not for this caller), so the endpoint does not tell who exists or who has a
 * photo.
 */
@Service
@RequiredArgsConstructor
public class PersonPhotoService {

	private final AppUserRepository appUserRepository;
	private final StoredDocumentRepository storedDocumentRepository;
	private final ObjectStorage objectStorage;
	private final PersonAccessService personAccessService;
	private final MenteeProgramService menteeProgramService;
	private final ProgramMentorRepository programMentorRepository;
	private final ProgramRepository programRepository;
	private final ProgramCoordinatorRepository programCoordinatorRepository;
	private final PlatformTransactionManager transactionManager;

	private record StoredPhoto(String key, @Nullable String contentType) {
	}

	/**
	 * Only the lookup and the rules hold a database connection; the storage read and the scaling run after it, so a
	 * page of avatars does not tie up the pool.
	 */
	public AvatarImages.Image photo(UUID actorId, UUID personId) {
		TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
		readOnly.setReadOnly(true);
		StoredPhoto photo = readOnly.execute(status -> visiblePhoto(actorId, personId))
				.orElseThrow(() -> notFound(personId));
		byte[] original = objectStorage.get(photo.key());
		if (original == null) {
			throw notFound(personId);
		}
		return AvatarImages.thumbnail(original, photo.contentType()).orElseThrow(() -> notFound(personId));
	}

	// Most people have no photo, so that is asked first; every "no" below is the same answer.
	private Optional<StoredPhoto> visiblePhoto(UUID actorId, UUID personId) {
		AppUser viewer = appUserRepository.findById(actorId).orElse(null);
		AppUser person = appUserRepository.findById(personId).orElse(null);
		if (viewer == null || person == null) {
			return Optional.empty();
		}
		return DocumentService
				.newestAvatar(storedDocumentRepository.findByUserIdAndKind(personId, DocumentKind.AVATAR))
				.filter(photo -> canSeePhoto(viewer, person))
				.map(photo -> new StoredPhoto(photo.getS3Key(), photo.getContentType()));
	}

	/**
	 * Who sees a person's photo, all in one place:
	 * <ol>
	 * <li>the person themself, and approved admins;</li>
	 * <li>anyone who may open the person's profile ({@link PersonAccessService}: teammates, their mentors and
	 * mentees, the coordinators of their startup programs);</li>
	 * <li>approved coordinators of a program the person is in, of any kind (semester, Akran, İŞKUR);</li>
	 * <li>approved people who share a running program with the person.</li>
	 * </ol>
	 * Rules 3 and 4 need both accounts approved; a pending or rejected account gets only rules 1 and 2, and a deleted
	 * one shows no photo to anyone.
	 */
	boolean canSeePhoto(AppUser viewer, AppUser person) {
		if (person.getStatus() == UserAccountStatus.DELETED) {
			return false;
		}
		if (viewer.getId().equals(person.getId())) {
			return true;
		}
		if (viewer.getStatus() != UserAccountStatus.APPROVED) {
			return false;
		}
		if (viewer.has(UserRole.ADMIN)) {
			return true;
		}
		if (person.getStatus() == UserAccountStatus.APPROVED) {
			List<Program> personPrograms = programs(programIdsOf(person));
			if (coordinatesAny(viewer, personPrograms) || sharesRunningProgram(viewer, personPrograms)) {
				return true;
			}
		}
		return personAccessService.sees(viewer, person);
	}

	private boolean coordinatesAny(AppUser viewer, List<Program> programs) {
		return viewer.has(UserRole.COORDINATOR) && programs.stream()
				.anyMatch(program -> ProgramCoordination.coordinates(program, viewer.getId(),
						programCoordinatorRepository));
	}

	private boolean sharesRunningProgram(AppUser viewer, List<Program> personPrograms) {
		List<UUID> running = personPrograms.stream()
				.filter(program -> program.getStatus() == ProgramStatus.ACTIVE)
				.map(Program::getId)
				.toList();
		if (running.isEmpty()) {
			return false;
		}
		Set<UUID> viewerPrograms = programIdsOf(viewer);
		return running.stream().anyMatch(viewerPrograms::contains);
	}

	/** The programs a person is in now: as a student or a startup's member, and as an accepted mentor. */
	private Set<UUID> programIdsOf(AppUser user) {
		Set<UUID> ids = new LinkedHashSet<>();
		if (user.getProgramId() != null) {
			ids.add(user.getProgramId());
		}
		menteeProgramService.programOf(user).ifPresent(ids::add);
		programMentorRepository.findByMentorIdAndStatusOrderByCreatedAtDesc(user.getId(), ProgramMentorStatus.ACCEPTED)
				.stream()
				.map(ProgramMentor::getProgramId)
				.forEach(ids::add);
		return ids;
	}

	private List<Program> programs(Set<UUID> ids) {
		return ids.isEmpty() ? List.of() : programRepository.findAllById(ids);
	}

	// One fixed text for every case, so the body does not tell "no such person" from "no photo for you".
	private static ResourceNotFoundException notFound(UUID personId) {
		return new ResourceNotFoundException("Photo not found: " + personId);
	}
}
