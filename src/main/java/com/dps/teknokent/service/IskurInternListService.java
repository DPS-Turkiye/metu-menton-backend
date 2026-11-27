package com.dps.teknokent.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IskurInternAccountState;
import com.dps.teknokent.dto.requests.IskurInternEmailsRequest;
import com.dps.teknokent.dto.responses.IskurInternListResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurInternEmail;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.ConflictException;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurInternEmailRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.util.EmailDomains;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The coordinator's İŞKUR intern list (weekend launch, part B). Since 27 Nov it decides nothing: every approved
 * student of an İŞKUR program is an intern ({@link IskurInternService}), and the web no longer shows the list.
 * It stays readable so the addresses already entered are kept, until a cleanup removes it. Removing one is still
 * refused while that person is placed in a team.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IskurInternListService {

	/** A program is one cohort of about 72 interns; the cap only stops a runaway paste. */
	static final int MAX_LISTED = 2000;
	/** How many unreadable entries an add call echoes back. */
	static final int MAX_REJECTED_SHOWN = 50;

	private final IskurWorkspaceService workspaceService;
	private final IskurInternService interns;
	private final IskurInternEmailRepository internEmails;
	private final AppUserRepository users;
	private final IskurPlacementRepository placements;

	/** The program's coordinators and the admin may read it. */
	@Transactional(readOnly = true)
	public IskurInternListResponse list(UUID actorId, UUID programId) {
		return build(workspaceService.requireProgram(actorId, programId), List.of());
	}

	@Transactional
	public IskurInternListResponse add(UUID actorId, UUID programId, IskurInternEmailsRequest request) {
		Program program = workspaceService.requireEditable(actorId, programId);
		Set<String> listed = new HashSet<>(internEmails.findEmailsByProgramId(program.getId()));
		Set<String> fresh = new LinkedHashSet<>();
		List<String> rejected = new ArrayList<>();
		for (String raw : request.emails()) {
			String email = EmailDomains.normalizeEmail(raw);
			if (email.isEmpty()) {
				continue;
			}
			// ASCII only: Java's and Postgres's lowercasing agree on it, and university addresses are ASCII anyway.
			if (!EmailDomains.isEmail(email) || !isAscii(email)) {
				if (rejected.size() < MAX_REJECTED_SHOWN) {
					rejected.add(raw.strip());
				}
				continue;
			}
			if (!listed.contains(email)) {
				fresh.add(email);
			}
		}
		if (listed.size() + fresh.size() > MAX_LISTED) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurInternListFull", MAX_LISTED));
		}
		Instant now = Instant.now();
		internEmails.saveAll(fresh.stream()
				.map(email -> new IskurInternEmail(program.getId(), email, actorId, now))
				.toList());
		log.info("İŞKUR intern list: actor {} added {} addresses to program {} ({} unreadable)", actorId, fresh.size(),
				program.getId(), rejected.size());
		return build(program, rejected);
	}

	/** Takes an address off the list; refused while its owner is placed ("önce yerleşimden çıkar"). */
	@Transactional
	public IskurInternListResponse remove(UUID actorId, UUID programId, UUID entryId) {
		Program program = workspaceService.requireEditable(actorId, programId);
		IskurInternEmail entry = internEmails.findById(entryId)
				.filter(found -> found.getProgramId().equals(program.getId()))
				.orElseThrow(() -> new ResourceNotFoundException(Texts.forRequest("error.iskurInternNotFound")));
		boolean placed = users.findByLowercaseEmailIn(List.of(entry.getEmail())).stream()
				.anyMatch(user -> placements.findByProgramIdAndUserId(program.getId(), user.getId()).isPresent());
		if (placed) {
			throw new ConflictException(Texts.forRequest("error.iskurInternPlaced"));
		}
		internEmails.delete(entry);
		log.info("İŞKUR intern list: actor {} removed entry {} from program {}", actorId, entry.getId(),
				program.getId());
		return build(program, List.of());
	}

	private IskurInternListResponse build(Program program, List<String> rejected) {
		List<IskurInternEmail> rows = internEmails.findByProgramIdOrderByEmailAsc(program.getId());
		Set<UUID> internIds = interns.internIds(program.getId());
		Set<UUID> placedIds = placements.findByProgramId(program.getId()).stream()
				.map(IskurPlacement::getUserId)
				.collect(Collectors.toSet());
		Map<String, List<AppUser>> accounts = new HashMap<>();
		if (!rows.isEmpty()) {
			users.findByLowercaseEmailIn(rows.stream().map(IskurInternEmail::getEmail).toList())
					.forEach(user -> accounts
							.computeIfAbsent(EmailDomains.normalizeEmail(user.getEmail()), key -> new ArrayList<>())
							.add(user));
		}

		List<IskurInternListResponse.Entry> entries = rows.stream()
				.map(row -> entry(row, accounts.getOrDefault(row.getEmail(), List.of()), internIds, placedIds))
				.toList();
		IskurInternListResponse.Counts counts = new IskurInternListResponse.Counts(entries.size(),
				count(entries, IskurInternAccountState.INTERN), count(entries, IskurInternAccountState.NOT_SIGNED_UP),
				count(entries, IskurInternAccountState.OUTSIDE_PROGRAM));
		return new IskurInternListResponse(program.getId(), entries, counts, List.copyOf(rejected));
	}

	private static IskurInternListResponse.Entry entry(IskurInternEmail row, List<AppUser> owners,
			Set<UUID> internIds, Set<UUID> placedIds) {
		boolean placed = owners.stream().anyMatch(user -> placedIds.contains(user.getId()));
		AppUser intern = owners.stream()
				.filter(user -> internIds.contains(user.getId()))
				.min(Comparator.comparing(AppUser::getId))
				.orElse(null);
		if (intern != null) {
			return new IskurInternListResponse.Entry(row.getId(), row.getEmail(), row.getAddedAt(),
					IskurInternAccountState.INTERN, intern.getId(), intern.getFullName(), placed);
		}
		IskurInternAccountState state = owners.isEmpty() ? IskurInternAccountState.NOT_SIGNED_UP
				: IskurInternAccountState.OUTSIDE_PROGRAM;
		return new IskurInternListResponse.Entry(row.getId(), row.getEmail(), row.getAddedAt(), state, null, null,
				placed);
	}

	private static boolean isAscii(String email) {
		return email.chars().allMatch(c -> c < 0x80);
	}

	private static int count(List<IskurInternListResponse.Entry> entries, IskurInternAccountState state) {
		return (int) entries.stream().filter(entry -> entry.account() == state).count();
	}
}
