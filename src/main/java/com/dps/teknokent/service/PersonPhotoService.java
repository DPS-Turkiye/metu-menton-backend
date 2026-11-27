package com.dps.teknokent.service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.DocumentKind;
import com.dps.teknokent.domain.ProgramMentorStatus;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramMentor;
import com.dps.teknokent.entity.StoredDocument;
import com.dps.teknokent.exception.ResourceNotFoundException;
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

	private final DomainLookupService domainLookupService;
	private final StoredDocumentRepository storedDocumentRepository;
	private final ObjectStorage objectStorage;
	private final PersonAccessService personAccessService;
	private final MenteeProgramService menteeProgramService;
	private final ProgramMentorRepository programMentorRepository;
	private final ProgramRepository programRepository;
	private final ProgramCoordinatorRepository programCoordinatorRepository;

	@Transactional(readOnly = true)
	public AvatarImages.Image photo(UUID actorId, UUID personId) {
		AppUser viewer = domainLookupService.requireUser(actorId);
		AppUser person = domainLookupService.requireUser(personId);
		// Most people have no photo, so that is asked first; the answer is the same "not found" either way.
		StoredDocument photo = storedDocumentRepository.findByUserIdAndKind(personId, DocumentKind.AVATAR).stream()
				.max(Comparator
						.comparing(StoredDocument::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
						.thenComparing(StoredDocument::getId))
				.orElseThrow(() -> notFound(personId));
		if (!canSeePhoto(viewer, person)) {
			throw notFound(personId);
		}
		return AvatarImages.thumbnail(objectStorage.require(photo.getS3Key()), photo.getContentType())
				.orElseThrow(() -> notFound(personId));
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
			Set<UUID> personPrograms = programIdsOf(person);
			if (coordinatesAny(viewer, personPrograms) || sharesRunningProgram(viewer, personPrograms)) {
				return true;
			}
		}
		return personAccessService.sees(viewer, person);
	}

	private boolean coordinatesAny(AppUser viewer, Set<UUID> programIds) {
		return viewer.has(UserRole.COORDINATOR) && programs(programIds).stream()
				.anyMatch(program -> ProgramCoordination.coordinates(program, viewer.getId(),
						programCoordinatorRepository));
	}

	private boolean sharesRunningProgram(AppUser viewer, Set<UUID> personPrograms) {
		if (personPrograms.isEmpty()) {
			return false;
		}
		Set<UUID> shared = programIdsOf(viewer);
		shared.retainAll(personPrograms);
		return programs(shared).stream().anyMatch(program -> program.getStatus() == ProgramStatus.ACTIVE);
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

	private static ResourceNotFoundException notFound(UUID personId) {
		return new ResourceNotFoundException("Photo not found: " + personId);
	}
}
