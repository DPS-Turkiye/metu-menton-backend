package com.dps.teknokent.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.dps.teknokent.domain.IskurChannelKind;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.entity.IskurChannel;
import com.dps.teknokent.entity.IskurChannelMember;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.repository.IskurChannelMemberRepository;
import com.dps.teknokent.repository.IskurChannelRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.ProgramRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the İŞKUR channels in step with the placements. The student handbook (p.04, p.13) wins over Figma 5.1 here:
 * "kanalların atamayla otomatik açılır; ekip değişirse erişimin de otomatik güncellenir", so the channels open with
 * the coordinator's provisional placement, not only once it is final. Every change rebuilds the whole program's
 * membership from the placements, so it does not matter which field an event carries or in what order events arrive: a
 * finalize, a move,
 * a new leader and a removal all come out right, and running it twice changes nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IskurChannelMembershipService {

	private final ProgramRepository programs;
	private final IskurPlacementRepository placements;
	private final IskurTeamRepository teams;
	private final IskurChannelRepository channels;
	private final IskurChannelMemberRepository members;
	private final PlatformTransactionManager transactionManager;

	/**
	 * After the placement is committed, in a transaction of its own: a failure here is logged and never turns the
	 * coordinator's saved placement into an error. The next change in the program, or the next start, repairs it.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onPlacementChanged(IskurPlacementChangedEvent event) {
		reconcileOnItsOwn(event.programId());
	}

	/**
	 * A sync that failed, or never ran because the server stopped between the commit and the listener, is repaired on
	 * the next start: every peer program is rebuilt once, each in its own transaction.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void reconcileAllOnStartup() {
		for (Program program : programs.findByProgramType(ProgramType.PEER_MENTORING)) {
			reconcileOnItsOwn(program.getId());
		}
	}

	private void reconcileOnItsOwn(UUID programId) {
		TransactionTemplate own = new TransactionTemplate(transactionManager);
		own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		try {
			own.executeWithoutResult(status -> reconcile(programId));
		} catch (RuntimeException failure) {
			log.warn("İŞKUR channel sync failed for program {}", programId, failure);
		}
	}

	/**
	 * Opens the channels the placements need and moves interns in and out of them. The program row is locked
	 * like every placement write, so two syncs never open the same channel twice.
	 */
	@Transactional
	public void reconcile(UUID programId) {
		if (programs.findByIdForUpdate(programId).isEmpty()) {
			return;
		}
		Instant now = Instant.now();
		Map<ChannelKey, Map<UUID, Boolean>> wanted = wantedMembership(programId);

		Map<ChannelKey, IskurChannel> byKey = new HashMap<>();
		channels.findByProgramId(programId).forEach(channel -> byKey.put(ChannelKey.of(channel), channel));
		List<IskurChannel> opened = new ArrayList<>();
		for (ChannelKey key : wanted.keySet()) {
			if (!byKey.containsKey(key)) {
				IskurChannel channel = key.open(programId, now);
				byKey.put(key, channel);
				opened.add(channel);
			}
		}
		channels.saveAll(opened);

		Map<UUID, Map<UUID, IskurChannelMember>> rows = new HashMap<>();
		members.findByChannelIdIn(byKey.values().stream().map(IskurChannel::getId).toList())
				.forEach(row -> rows.computeIfAbsent(row.getChannelId(), id -> new HashMap<>())
						.put(row.getUserId(), row));

		List<IskurChannelMember> changed = new ArrayList<>();
		byKey.forEach((key, channel) -> {
			Map<UUID, Boolean> want = wanted.getOrDefault(key, Map.of());
			Map<UUID, IskurChannelMember> have = rows.getOrDefault(channel.getId(), Map.of());
			have.forEach((userId, row) -> {
				Boolean canWrite = want.get(userId);
				if (canWrite == null) {
					if (row.isActive()) {
						row.setLeftAt(now);
						changed.add(row);
					}
				} else if (!row.isActive() || row.isCanWrite() != canWrite) {
					if (!row.isActive()) {
						row.setLeftAt(null);
						row.setJoinedAt(now);
					}
					row.setCanWrite(canWrite);
					changed.add(row);
				}
			});
			want.forEach((userId, canWrite) -> {
				if (!have.containsKey(userId)) {
					changed.add(new IskurChannelMember(channel.getId(), userId, canWrite, now));
				}
			});
		});
		members.saveAll(changed);
	}

	/**
	 * Rules frame §4: who belongs in which channel, and whether they may write there, from the placements, provisional
	 * or final.
	 */
	private Map<ChannelKey, Map<UUID, Boolean>> wantedMembership(UUID programId) {
		List<IskurPlacement> placed = placements.findByProgramId(programId);
		Map<UUID, IskurTeam> teamById = teams.findByProgramIdOrderBySortOrderAscCreatedAtAsc(programId).stream()
				.collect(Collectors.toMap(IskurTeam::getId, Function.identity()));
		Map<UUID, IskurPlacement> byUser = placed.stream()
				.collect(Collectors.toMap(IskurPlacement::getUserId, Function.identity()));

		Map<ChannelKey, Map<UUID, Boolean>> wanted = new LinkedHashMap<>();
		for (IskurPlacement placement : placed) {
			IskurTeam team = teamById.get(placement.getTeamId());
			if (team == null) {
				continue;
			}
			UUID userId = placement.getUserId();
			join(wanted, ChannelKey.announcements(), userId, false);
			join(wanted, new ChannelKey(IskurChannelKind.UNIT, team.getUnitId(), null), userId, true);
			join(wanted, new ChannelKey(IskurChannelKind.TEAM, team.getId(), null), userId, true);
			IskurPlacement leader = placement.getLeaderId() == null ? null : byUser.get(placement.getLeaderId());
			if (placement.getTeamRole() == IskurTeamRole.MEMBER && leader != null
					&& leader.getTeamRole() == IskurTeamRole.LEADER
					&& leader.getTeamId().equals(placement.getTeamId())) {
				ChannelKey direct = new ChannelKey(IskurChannelKind.LEADER_DIRECT, leader.getUserId(), userId);
				join(wanted, direct, userId, true);
				join(wanted, direct, leader.getUserId(), true);
			}
		}
		return wanted;
	}

	private static void join(Map<ChannelKey, Map<UUID, Boolean>> wanted, ChannelKey key, UUID userId,
			boolean canWrite) {
		wanted.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(userId, canWrite);
	}

	/**
	 * What makes a channel the same channel: its kind plus the unit, the team or the leader and member pair.
	 * {@code ref} is the unit, the team or the leader; {@code other} is the member of a 1:1.
	 */
	private record ChannelKey(IskurChannelKind kind, @Nullable UUID ref, @Nullable UUID other) {

		static ChannelKey announcements() {
			return new ChannelKey(IskurChannelKind.ANNOUNCEMENTS, null, null);
		}

		static ChannelKey of(IskurChannel channel) {
			return switch (channel.getKind()) {
			case ANNOUNCEMENTS -> announcements();
			case UNIT -> new ChannelKey(IskurChannelKind.UNIT, channel.getUnitId(), null);
			case TEAM -> new ChannelKey(IskurChannelKind.TEAM, channel.getTeamId(), null);
			case LEADER_DIRECT -> new ChannelKey(IskurChannelKind.LEADER_DIRECT, channel.getLeaderId(),
					channel.getMemberId());
			};
		}

		IskurChannel open(UUID programId, Instant now) {
			IskurChannel channel = new IskurChannel(programId, kind, now);
			switch (kind) {
			case UNIT -> channel.setUnitId(ref);
			case TEAM -> channel.setTeamId(ref);
			case LEADER_DIRECT -> {
				channel.setLeaderId(ref);
				channel.setMemberId(other);
			}
			case ANNOUNCEMENTS -> {
				// The program is the whole scope.
			}
			}
			return channel;
		}
	}
}
