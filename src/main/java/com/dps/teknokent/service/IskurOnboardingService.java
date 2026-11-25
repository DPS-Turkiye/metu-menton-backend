package com.dps.teknokent.service;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.DocumentKind;
import com.dps.teknokent.domain.IskurDocumentSlot;
import com.dps.teknokent.domain.IskurSkillLevel;
import com.dps.teknokent.domain.IskurTeamRolePreference;
import com.dps.teknokent.domain.IskurWeeklySplit;
import com.dps.teknokent.dto.requests.IskurOnboardingRequest;
import com.dps.teknokent.dto.responses.IskurOnboardingResponse;
import com.dps.teknokent.dto.responses.StoredDocumentResponse;
import com.dps.teknokent.entity.InstitutionPerson;
import com.dps.teknokent.entity.IskurOnboarding;
import com.dps.teknokent.entity.IskurScheduleRange;
import com.dps.teknokent.entity.IskurSkill;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.StoredDocument;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.InstitutionPersonRepository;
import com.dps.teknokent.repository.IskurOnboardingRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.StoredDocumentRepository;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The intern's onboarding form (Figma 284:2, frames 02–04): work days and hours, unit preferences, skills, team roles,
 * the CV and transcript, and an optional leadership application. It stays a draft until the intern submits it; then the
 * program's coordinators place them from it, and the intern may still change it, whole, until they are placed.
 *
 * <p>While file storage is not set up (ODTÜ has not decided where files live yet), the documents step is closed and
 * the form goes without a CV; the leadership check then drops the CV too.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IskurOnboardingService {

	/** A participant may use at most 140 actual days across all İŞKUR programs (onboarding 02). */
	public static final int MAX_ISKUR_DAYS = 140;
	public static final int ANSWER_MAX_LENGTH = 500;
	public static final int MAX_TEAM_ROLES = 2;
	/** A leader runs the weekly team meeting and reviews, so the application asks for at least two work days. */
	public static final int MIN_LEADER_DAYS = 2;
	/** A term's length when the program has no dates: 3 days × 24 weeks is Figma 02's "about 72 days". */
	static final int DEFAULT_TERM_WEEKS = 24;
	static final int MAX_RANGES_PER_DAY = 4;

	private final IskurInternService interns;
	private final IskurOnboardingRepository onboardings;
	private final IskurPlacementRepository placements;
	private final IskurUnitRepository units;
	private final IskurTeamRepository teams;
	private final IskurSkillService skillService;
	private final InstitutionPersonRepository institutionPeople;
	private final StoredDocumentRepository storedDocuments;
	private final DocumentService documentService;
	private final ObjectStorage objectStorage;

	/** The caller's form, or an empty one; not found when the caller is not an intern of an İŞKUR program. */
	@Transactional(readOnly = true)
	public IskurOnboardingResponse get(UUID actorId) {
		Program program = requireProgram(actorId);
		return response(program, actorId, onboardings.findByProgramIdAndUserId(program.getId(), actorId).orElse(null));
	}

	/** Stores the form as it is, complete or not ("Taslak olarak kaydet", and each step's "Devam et"). */
	@Transactional
	public IskurOnboardingResponse saveDraft(UUID actorId, IskurOnboardingRequest request) {
		Program program = requireProgram(actorId);
		IskurOnboarding form = editableForm(program, actorId);
		if (form.isSubmitted()) {
			// A half-changed form never replaces the one the coordinators read; changes are sent, not saved as a draft.
			throw new ConflictException(Texts.forRequest("error.iskurOnboardingSent"));
		}
		apply(form, program, request);
		onboardings.save(form);
		return response(program, actorId, form);
	}

	/**
	 * Stores the form and sends it to the coordinators, once everything it asks is answered. A sent form may be sent
	 * again with changes until its intern is placed.
	 */
	@Transactional
	public IskurOnboardingResponse submit(UUID actorId, IskurOnboardingRequest request) {
		Program program = requireProgram(actorId);
		IskurOnboarding form = editableForm(program, actorId);
		apply(form, program, request);
		Set<UUID> asked = requireComplete(form, program);
		// Ratings of units the intern did not pick go.
		form.getSkills().keySet().retainAll(asked);
		Instant now = Instant.now();
		form.setSubmittedAt(now);
		form.setUpdatedAt(now);
		onboardings.save(form);
		return response(program, actorId, form);
	}

	/** Puts a PDF in the CV or transcript slot, replacing what was there. */
	@Transactional
	public IskurOnboardingResponse upload(UUID actorId, IskurDocumentSlot slot, @Nullable MultipartFile file) {
		Program program = requireProgram(actorId);
		if (!objectStorage.isConfigured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					Texts.forRequest("error.iskurDocumentsOff"));
		}
		if (file == null || file.isEmpty()) {
			throw invalid("error.iskurDocumentMissing");
		}
		IskurOnboarding form = editableForm(program, actorId);
		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (IOException error) {
			throw invalid("error.iskurDocumentUnreadable");
		}
		StoredDocumentResponse stored = documentService.store(actorId, null, DocumentKind.ISKUR_DOCUMENT,
				file.getOriginalFilename(), file.getContentType(), bytes);
		StoredDocument row = storedDocuments.findById(stored.id())
				.orElseThrow(() -> new IllegalStateException("Stored document vanished: " + stored.id()));
		removeObjectIfRolledBack(row.getS3Key());
		UUID previous = slotOf(form, slot);
		setSlot(form, slot, row.getId());
		form.setUpdatedAt(Instant.now());
		onboardings.save(form);
		if (previous != null) {
			deleteFile(previous);
		}
		return response(program, actorId, form);
	}

	@Transactional
	public IskurOnboardingResponse removeDocument(UUID actorId, IskurDocumentSlot slot) {
		Program program = requireProgram(actorId);
		IskurOnboarding form = editableForm(program, actorId);
		if (form.isSubmitted() && slot == IskurDocumentSlot.CV) {
			// A sent form always has its CV; it can be replaced.
			throw invalid("error.iskurOnboardingCvMissing");
		}
		UUID previous = slotOf(form, slot);
		if (previous != null) {
			setSlot(form, slot, null);
			form.setUpdatedAt(Instant.now());
			onboardings.save(form);
			deleteFile(previous);
		}
		return response(program, actorId, form);
	}

	/**
	 * The days of the week the intern works, from their submitted form; empty until they submit it. Task assignment
	 * ("only on the member's work day") reads this.
	 */
	@Transactional(readOnly = true)
	public Set<DayOfWeek> workDays(UUID programId, UUID userId) {
		Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		onboardings.findByProgramIdAndUserId(programId, userId)
				.filter(IskurOnboarding::isSubmitted)
				.ifPresent(form -> form.getRanges().forEach(range -> days.add(DayOfWeek.of(range.getDayOfWeek()))));
		return days;
	}

	/** How many weeks a term of this program has: from its dates, or 24 when it has none. */
	static int termWeeks(Program program) {
		LocalDate start = program.getStartDate();
		LocalDate end = program.getEndDate();
		if (start == null || end == null || end.isBefore(start)) {
			return DEFAULT_TERM_WEEKS;
		}
		long days = ChronoUnit.DAYS.between(start, end) + 1;
		return (int) Math.max(1, (days + 6) / 7);
	}

	/** The form as its owner or a coordinator reads it. */
	IskurOnboardingResponse.Form formOf(@Nullable IskurOnboarding form) {
		if (form == null) {
			return new IskurOnboardingResponse.Form(null, List.of(), null, List.of(), List.of(), List.of(),
					new IskurOnboardingResponse.Documents(null, null),
					new IskurOnboardingResponse.Leadership(false, null, null, null), null);
		}
		return formOf(form, files(List.of(form)));
	}

	/** {@link #formOf(IskurOnboarding)} with the forms' files already loaded by {@link #files(Collection)}. */
	IskurOnboardingResponse.Form formOf(IskurOnboarding form, Map<UUID, StoredDocument> files) {
		Short days = form.getWeeklyDays();
		Short prior = form.getPriorIskurDays();
		return new IskurOnboardingResponse.Form(
				days == null ? null : days.intValue(),
				form.getRanges().stream().map(IskurOnboardingService::rangeOf).toList(),
				prior == null ? null : prior.intValue(),
				List.copyOf(form.getUnitIds()),
				form.getSkills().entrySet().stream()
						.map(entry -> new IskurOnboardingResponse.SkillAnswer(entry.getKey(), entry.getValue()))
						.toList(),
				form.getTeamRoles().stream().sorted().toList(),
				new IskurOnboardingResponse.Documents(fileRef(files, form.getCvDocumentId()),
						fileRef(files, form.getTranscriptDocumentId())),
				new IskurOnboardingResponse.Leadership(form.isWantsLeadership(), form.getLeadershipMotivation(),
						form.getLeadershipExperience(), form.getLeadershipConflict()),
				form.getSubmittedAt());
	}

	/** The stored files the forms point at, in one query. */
	Map<UUID, StoredDocument> files(Collection<IskurOnboarding> forms) {
		Set<UUID> ids = new HashSet<>();
		forms.forEach(form -> {
			if (form.getCvDocumentId() != null) {
				ids.add(form.getCvDocumentId());
			}
			if (form.getTranscriptDocumentId() != null) {
				ids.add(form.getTranscriptDocumentId());
			}
		});
		Map<UUID, StoredDocument> byId = new HashMap<>();
		if (!ids.isEmpty()) {
			storedDocuments.findAllById(ids).forEach(file -> byId.put(file.getId(), file));
		}
		return byId;
	}

	static @Nullable UUID slotOf(IskurOnboarding form, IskurDocumentSlot slot) {
		return slot == IskurDocumentSlot.CV ? form.getCvDocumentId() : form.getTranscriptDocumentId();
	}

	private Program requireProgram(UUID actorId) {
		return interns.currentProgram(actorId)
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurNotAnIntern")));
	}

	/**
	 * The caller's form, locked: a draft save, an upload and a submit of the same form run one at a time, and each sees
	 * whether the one before it submitted. A sent form closes once its intern is placed. A first form is inserted at
	 * once; two racing first saves meet the unique key
	 * and the loser answers 409.
	 */
	private IskurOnboarding editableForm(Program program, UUID userId) {
		IskurOnboarding form = onboardings.findForUpdate(program.getId(), userId)
				.orElseGet(() -> onboardings.saveAndFlush(new IskurOnboarding(program.getId(), userId, Instant.now())));
		if (!editable(program, userId, form)) {
			throw new ConflictException(Texts.forRequest("error.iskurOnboardingSubmitted"));
		}
		return form;
	}

	/** Until it is sent, and after that until the coordinators place the intern. */
	private boolean editable(Program program, UUID userId, @Nullable IskurOnboarding form) {
		return form == null || !form.isSubmitted()
				|| placements.findByProgramIdAndUserId(program.getId(), userId).isEmpty();
	}

	private void apply(IskurOnboarding form, Program program, IskurOnboardingRequest request) {
		Integer days = request.weeklyDays();
		Integer prior = request.priorIskurDays();
		form.setWeeklyDays(days == null ? null : days.shortValue());
		form.setPriorIskurDays(prior == null ? null : prior.shortValue());
		form.getRanges().clear();
		form.getRanges().addAll(ranges(request.schedule()));

		Set<UUID> programUnits = units.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()).stream()
				.map(IskurUnit::getId)
				.collect(Collectors.toSet());
		List<UUID> unitIds = request.unitIds() == null ? List.of() : request.unitIds();
		if (!programUnits.containsAll(unitIds)) {
			throw invalid("error.iskurOnboardingUnknownUnit");
		}
		form.getUnitIds().retainAll(unitIds);
		form.getUnitIds().addAll(unitIds);

		Set<UUID> catalogue = new HashSet<>(skillService.catalogueIds(program.getId()));
		Map<UUID, IskurSkillLevel> answers = new LinkedHashMap<>();
		if (request.skills() != null) {
			for (IskurOnboardingRequest.SkillAnswer answer : request.skills()) {
				if (!catalogue.contains(answer.skillId())) {
					throw invalid("error.iskurOnboardingUnknownSkill");
				}
				answers.put(answer.skillId(), answer.level());
			}
		}
		form.getSkills().keySet().retainAll(answers.keySet());
		form.getSkills().putAll(answers);

		Set<IskurTeamRolePreference> roles = new LinkedHashSet<>(
				request.teamRoles() == null ? List.of() : request.teamRoles());
		form.getTeamRoles().retainAll(roles);
		form.getTeamRoles().addAll(roles);

		// Answers to an application the intern is not making are not kept, not even in a draft.
		IskurOnboardingRequest.Leadership leadership = request.leadership();
		boolean wants = leadership != null && leadership.wants();
		form.setWantsLeadership(wants);
		form.setLeadershipMotivation(wants ? answer(leadership.motivation()) : null);
		form.setLeadershipExperience(wants ? answer(leadership.experience()) : null);
		form.setLeadershipConflict(wants ? answer(leadership.conflict()) : null);
		form.setUpdatedAt(Instant.now());
	}

	private static List<IskurScheduleRange> ranges(@Nullable List<IskurOnboardingRequest.Range> schedule) {
		if (schedule == null) {
			return List.of();
		}
		List<IskurScheduleRange> ranges = new ArrayList<>();
		Map<DayOfWeek, Integer> perDay = new HashMap<>();
		for (IskurOnboardingRequest.Range range : schedule) {
			int start = minutes(range.start());
			int end = minutes(range.end());
			if (start >= end) {
				throw invalid("error.iskurOnboardingRangeOrder");
			}
			if (perDay.merge(range.day(), 1, Integer::sum) > MAX_RANGES_PER_DAY) {
				throw invalid("error.iskurOnboardingTooManyRanges");
			}
			ranges.add(new IskurScheduleRange((short) range.day().getValue(), (short) start, (short) end));
		}
		ranges.sort(Comparator.comparingInt(IskurScheduleRange::getDayOfWeek)
				.thenComparingInt(IskurScheduleRange::getStartMinute));
		return ranges;
	}

	/**
	 * Everything the form asks is answered and adds up; answers the skill ids the intern's units ask about. A
	 * leadership application also needs two days, enough İŞKUR days left for the term, a CV while files can be
	 * uploaded, and its three answers.
	 */
	private Set<UUID> requireComplete(IskurOnboarding form, Program program) {
		Short storedDays = form.getWeeklyDays();
		if (storedDays == null) {
			throw invalid("error.iskurOnboardingDaysMissing");
		}
		int days = storedDays;
		int total = form.getRanges().stream().mapToInt(IskurScheduleRange::minutes).sum();
		if (total != days * IskurWeeklySplit.MINUTES_PER_DAY) {
			throw invalid("error.iskurOnboardingScheduleTotal");
		}
		IskurScheduleRange previous = null;
		for (IskurScheduleRange range : form.getRanges()) {
			if (previous != null && previous.getDayOfWeek() == range.getDayOfWeek()
					&& range.getStartMinute() < previous.getEndMinute()) {
				throw invalid("error.iskurOnboardingScheduleOverlap");
			}
			previous = range;
		}
		Short prior = form.getPriorIskurDays();
		if (prior == null) {
			throw invalid("error.iskurOnboardingPriorDaysMissing");
		}
		if (form.getUnitIds().isEmpty()) {
			throw invalid("error.iskurOnboardingUnitsMissing");
		}
		Set<UUID> asked = skillService.skillsByUnit(program.getId(), form.getUnitIds()).values().stream()
				.flatMap(List::stream)
				.map(IskurSkill::getId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (!form.getSkills().keySet().containsAll(asked)) {
			throw invalid("error.iskurOnboardingSkillsMissing");
		}
		if (form.getTeamRoles().isEmpty()) {
			throw invalid("error.iskurOnboardingRolesMissing");
		}
		boolean documentsEnabled = objectStorage.isConfigured();
		boolean hasCv = form.getCvDocumentId() != null;
		if (documentsEnabled && !hasCv) {
			throw invalid("error.iskurOnboardingCvMissing");
		}
		if (form.isWantsLeadership()) {
			boolean enoughDaysLeft = MAX_ISKUR_DAYS - prior >= days * termWeeks(program);
			if (days < MIN_LEADER_DAYS || !enoughDaysLeft || (documentsEnabled && !hasCv)) {
				throw invalid("error.iskurLeadershipNotEligible");
			}
			if (form.getLeadershipMotivation() == null || form.getLeadershipExperience() == null
					|| form.getLeadershipConflict() == null) {
				throw invalid("error.iskurLeadershipAnswersMissing");
			}
		}
		return asked;
	}

	private IskurOnboardingResponse response(Program program, UUID userId, @Nullable IskurOnboarding form) {
		List<IskurUnit> programUnits = units.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId());
		Map<UUID, List<String>> teamNames = teams.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId())
				.stream()
				.collect(Collectors.groupingBy(IskurTeam::getUnitId, LinkedHashMap::new,
						Collectors.mapping(IskurTeam::getName, Collectors.toList())));
		Map<UUID, List<IskurSkill>> skills = skillService.skillsByUnit(program.getId(),
				programUnits.stream().map(IskurUnit::getId).toList());
		List<IskurOnboardingResponse.Unit> unitResponses = programUnits.stream()
				.map(unit -> new IskurOnboardingResponse.Unit(unit.getId(), unit.getName(),
						teamNames.getOrDefault(unit.getId(), List.of()),
						skills.getOrDefault(unit.getId(), List.of()).stream()
								.map(skill -> new IskurOnboardingResponse.Skill(skill.getId(), skill.getName()))
								.toList()))
				.toList();
		Optional<InstitutionPerson> record = institutionPeople.findByUserIdIn(Set.of(userId)).stream()
				.max(Comparator.comparing(InstitutionPerson::getSyncedAt,
						Comparator.nullsFirst(Comparator.<Instant>naturalOrder())));
		IskurOnboardingResponse.Context context = new IskurOnboardingResponse.Context(program.getId(),
				program.getName(), record.map(InstitutionPerson::getDepartmentName).orElse(null),
				record.map(InstitutionPerson::getClassYear).orElse(null), unitResponses,
				objectStorage.isConfigured(), MAX_ISKUR_DAYS, termWeeks(program), MIN_LEADER_DAYS, MAX_TEAM_ROLES,
				ANSWER_MAX_LENGTH, editable(program, userId, form));
		return new IskurOnboardingResponse(formOf(form), context);
	}

	private static IskurOnboardingResponse.Range rangeOf(IskurScheduleRange range) {
		return new IskurOnboardingResponse.Range(DayOfWeek.of(range.getDayOfWeek()), hhmm(range.getStartMinute()),
				hhmm(range.getEndMinute()));
	}

	private static IskurOnboardingResponse.@Nullable FileRef fileRef(Map<UUID, StoredDocument> files,
			@Nullable UUID id) {
		StoredDocument file = id == null ? null : files.get(id);
		if (file == null) {
			return null;
		}
		return new IskurOnboardingResponse.FileRef(file.getOriginalFilename(), file.getSizeBytes(),
				file.getCreatedAt());
	}

	private static void setSlot(IskurOnboarding form, IskurDocumentSlot slot, @Nullable UUID documentId) {
		if (slot == IskurDocumentSlot.CV) {
			form.setCvDocumentId(documentId);
		} else {
			form.setTranscriptDocumentId(documentId);
		}
	}

	/** The row goes now; its object only once the change has committed, so a rollback never loses the file. */
	private void deleteFile(UUID documentId) {
		storedDocuments.findById(documentId).ifPresent(file -> {
			storedDocuments.delete(file);
			String key = file.getS3Key();
			afterCommit(() -> deleteObject(key));
		});
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
			log.warn("İŞKUR onboarding document object {} remains in storage: {}", s3Key, error.getMessage());
		}
	}

	private static @Nullable String answer(@Nullable String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static int minutes(String time) {
		return Integer.parseInt(time.substring(0, 2)) * 60 + Integer.parseInt(time.substring(3, 5));
	}

	private static String hhmm(int minutes) {
		return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
	}

	private static InvalidRequestException invalid(String key) {
		return new InvalidRequestException(Texts.forRequest(key));
	}
}
