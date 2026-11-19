package com.dps.teknokent.service;

import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IskurDocumentSlot;
import com.dps.teknokent.dto.requests.IskurUnitSkillsRequest;
import com.dps.teknokent.dto.responses.IskurApplicationsResponse;
import com.dps.teknokent.dto.responses.IskurOnboardingResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurOnboarding;
import com.dps.teknokent.entity.IskurSkill;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.StoredDocument;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurOnboardingRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.StoredDocumentRepository;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;

/**
 * The coordinators' side of İŞKUR onboarding: the submitted forms they place interns from (the placement screen's
 * "Tercihler" panel), the CV and transcript behind them, and the skills each unit asks about.
 */
@Service
@RequiredArgsConstructor
public class IskurApplicationService {

	private static final Locale TR = Locale.forLanguageTag("tr");

	private final IskurWorkspaceService workspaceService;
	private final IskurInternService interns;
	private final IskurOnboardingService onboardingService;
	private final IskurSkillService skillService;
	private final IskurOnboardingRepository onboardings;
	private final IskurUnitRepository units;
	private final AppUserRepository users;
	private final StoredDocumentRepository storedDocuments;
	private final ObjectStorage objectStorage;

	/** A file as the download endpoint streams it. */
	@SuppressWarnings("ArrayRecordComponent") // Carries a file's bytes to the controller; never compared or hashed.
	public record DocumentContent(String filename, String contentType, byte[] bytes) {
	}

	/**
	 * Every submitted form of the program's current interns, by name. Drafts are counted, not shown: they are the
	 * intern's until they send them. A form of someone who has left the program is left out.
	 */
	@Transactional(readOnly = true)
	public IskurApplicationsResponse applications(UUID actorId, UUID programId) {
		return build(workspaceService.requireProgram(actorId, programId));
	}

	/** Sets the skills a unit asks about, in order; answers to a skill the unit keeps stay. */
	@Transactional
	public IskurApplicationsResponse replaceUnitSkills(UUID actorId, UUID programId, UUID unitId,
			IskurUnitSkillsRequest request) {
		Program program = workspaceService.requireEditable(actorId, programId);
		IskurUnit unit = units.findById(unitId)
				.filter(found -> found.getProgramId().equals(program.getId()))
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurUnitNotFound")));
		skillService.replaceUnitSkills(program.getId(), unit.getId(), request.names());
		return build(program);
	}

	/** An intern's CV or transcript, for the program's coordinators only, and only from a submitted form. */
	@Transactional(readOnly = true)
	public DocumentContent document(UUID actorId, UUID programId, UUID userId, IskurDocumentSlot slot) {
		Program program = workspaceService.requireProgram(actorId, programId);
		UUID documentId = onboardings.findByProgramIdAndUserId(program.getId(), userId)
				.filter(IskurOnboarding::isSubmitted)
				.filter(form -> interns.internIds(program.getId()).contains(form.getUserId()))
				.map(form -> IskurOnboardingService.slotOf(form, slot))
				.orElse(null);
		StoredDocument file = documentId == null ? null : storedDocuments.findById(documentId).orElse(null);
		if (file == null) {
			throw new ResourceNotFoundException(Texts.forRequest("error.iskurDocumentNotFound"));
		}
		return new DocumentContent(file.getOriginalFilename(), file.getContentType(),
				objectStorage.require(file.getS3Key()));
	}

	private IskurApplicationsResponse build(Program program) {
		List<IskurUnit> programUnits = units.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId());
		Map<UUID, List<IskurSkill>> skills = skillService.skillsByUnit(program.getId(),
				programUnits.stream().map(IskurUnit::getId).toList());
		List<IskurApplicationsResponse.Unit> unitResponses = programUnits.stream()
				.map(unit -> new IskurApplicationsResponse.Unit(unit.getId(), unit.getName(),
						skills.getOrDefault(unit.getId(), List.of()).stream()
								.map(skill -> new IskurOnboardingResponse.Skill(skill.getId(), skill.getName()))
								.toList()))
				.toList();

		Set<UUID> internIds = interns.internIds(program.getId());
		List<IskurOnboarding> forms = onboardings.findByProgramId(program.getId()).stream()
				.filter(form -> internIds.contains(form.getUserId()))
				.toList();
		List<IskurOnboarding> submitted = forms.stream().filter(IskurOnboarding::isSubmitted).toList();
		Map<UUID, AppUser> people = new HashMap<>();
		users.findAllById(submitted.stream().map(IskurOnboarding::getUserId).toList())
				.forEach(user -> people.put(user.getId(), user));
		Map<UUID, StoredDocument> files = onboardingService.files(submitted);
		Collator collator = Collator.getInstance(TR);
		List<IskurApplicationsResponse.Application> applications = submitted.stream()
				.filter(form -> people.containsKey(form.getUserId()))
				.map(form -> application(form, nameOf(people, form), files))
				.sorted(Comparator.comparing(IskurApplicationsResponse.Application::fullName, collator))
				.toList();
		return new IskurApplicationsResponse(program.getId(), unitResponses, applications,
				forms.size() - submitted.size());
	}

	private IskurApplicationsResponse.Application application(IskurOnboarding form, String fullName,
			Map<UUID, StoredDocument> files) {
		IskurOnboardingResponse.Form answers = onboardingService.formOf(form, files);
		Integer prior = answers.priorIskurDays();
		return new IskurApplicationsResponse.Application(form.getUserId(), fullName, form.getSubmittedAt(),
				answers.weeklyDays(), answers.schedule(), prior,
				prior == null ? null : IskurOnboardingService.MAX_ISKUR_DAYS - prior, answers.unitIds(),
				answers.skills(), answers.teamRoles(), answers.documents(), answers.leadership());
	}

	private static String nameOf(Map<UUID, AppUser> people, IskurOnboarding form) {
		AppUser user = people.get(form.getUserId());
		return user == null ? "" : user.getFullName();
	}
}
