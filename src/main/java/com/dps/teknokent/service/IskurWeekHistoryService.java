package com.dps.teknokent.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.IskurWeekEventKind;
import com.dps.teknokent.dto.requests.IskurWeekDecisionRequest;
import com.dps.teknokent.dto.responses.IskurWeekResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurWeekEvent;
import com.dps.teknokent.entity.IskurWorkWeek;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurWeekEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * A week's history: every send, decision and objection, in order. The week row holds only the latest of each, and a
 * resend clears them; these rows stay. The same list goes to the intern, the reviewing leader and the coordinators:
 * it carries the reviewer's reason and the intern's objection, which each of them already sees on the week, and
 * nothing from peer mentoring.
 */
@Service
@RequiredArgsConstructor
public class IskurWeekHistoryService {

	private final IskurWeekEventRepository events;
	private final AppUserRepository users;

	/** Written in the caller's transaction, so a change that rolls back leaves no history row. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void submitted(IskurWorkWeek week, Instant now) {
		events.save(next(week, IskurWeekEventKind.SUBMITTED, week.getUserId(), null, now));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void decided(IskurWorkWeek week, UUID reviewerId, IskurWeekDecisionRequest request,
			@Nullable String reason, Instant now) {
		events.save(next(week, IskurWeekEventKind.of(request.decision()), reviewerId, reason, now)
				.withChecks(request.timeMatchesWork(), request.effortDocumented(), request.outputsSufficient()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void objected(IskurWorkWeek week, String objection, Instant now) {
		events.save(next(week, IskurWeekEventKind.OBJECTION, week.getUserId(), objection, now));
	}

	/**
	 * The week's next step. Every caller has just saved the week under its version check, so two changes to one
	 * week cannot both get here; the unique (week, step) key is the backstop.
	 */
	private IskurWeekEvent next(IskurWorkWeek week, IskurWeekEventKind kind, UUID actorId, @Nullable String note,
			Instant now) {
		return new IskurWeekEvent(week.getId(), events.countByWeekId(week.getId()), kind, actorId, note, now);
	}

	/** Oldest first; empty for a week never sent. */
	@Transactional(readOnly = true)
	public List<IskurWeekResponse.HistoryEvent> of(@Nullable IskurWorkWeek week) {
		if (week == null) {
			return List.of();
		}
		List<IskurWeekEvent> rows = events.findByWeekIdOrderByStepAsc(week.getId());
		Map<UUID, AppUser> people = users.findAllById(rows.stream()
				.map(IskurWeekEvent::getActorId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(AppUser::getId, Function.identity()));
		return rows.stream().map(row -> {
			UUID actorId = row.getActorId();
			AppUser actor = actorId == null ? null : people.get(actorId);
			return new IskurWeekResponse.HistoryEvent(row.getStep(), row.getKind(), row.getCreatedAt(),
					actor == null ? null : new IskurWeekResponse.Person(actor.getId(), actor.getFullName()),
					row.getNote(), row.getCheckTimeMatchesWork(), row.getCheckEffortDocumented(),
					row.getCheckOutputsSufficient());
		}).toList();
	}
}
