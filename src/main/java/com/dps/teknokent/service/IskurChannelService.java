package com.dps.teknokent.service;

import java.text.Collator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.config.RateLimitingConfig;
import com.dps.teknokent.domain.IskurChannelEventType;
import com.dps.teknokent.domain.IskurChannelKind;
import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.dto.requests.IskurChannelPreferencesRequest;
import com.dps.teknokent.dto.requests.IskurMessageRequest;
import com.dps.teknokent.dto.responses.IskurChannelListResponse;
import com.dps.teknokent.dto.responses.IskurChannelPreferencesResponse;
import com.dps.teknokent.dto.responses.IskurChannelResponse;
import com.dps.teknokent.dto.responses.IskurMessagePageResponse;
import com.dps.teknokent.dto.responses.IskurMessageReceiptsResponse;
import com.dps.teknokent.dto.responses.IskurMessageResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurChannel;
import com.dps.teknokent.entity.IskurChannelMember;
import com.dps.teknokent.entity.IskurChannelMute;
import com.dps.teknokent.entity.IskurChannelRead;
import com.dps.teknokent.entity.IskurMessage;
import com.dps.teknokent.entity.IskurMessageReceipt;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurChannelMemberRepository;
import com.dps.teknokent.repository.IskurChannelMuteRepository;
import com.dps.teknokent.repository.IskurChannelReadRepository;
import com.dps.teknokent.repository.IskurChannelRepository;
import com.dps.teknokent.repository.IskurMessageReceiptRepository;
import com.dps.teknokent.repository.IskurMessageRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.ProgramCoordination;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;

/**
 * The İŞKUR channels (rules frame 322:3413 §4, 5.1, 6.1). Interns reach a channel only as one of its current members,
 * which {@link IskurChannelMembershipService} keeps in step with their placement. Coordinators reach channels
 * through their program role, checked on every call:
 * <ul>
 * <li>Genel Duyurular: every coordinator of the program and the admin read and post;</li>
 * <li>a unit's Liderler odası: its team leaders as members, and the unit's coordinator, who posts there like in the
 * unit channel (student handbook p.17, the weekly unit meeting); the admin reads it;</li>
 * <li>a unit's channel and its teams' channels: the unit's coordinator ("kendi birimindeki tüm kanalları görür"), or
 * every coordinator of the program while the unit has none. They post in the unit channel and only read the teams';
 * the admin reads both;</li>
 * <li>a member's 1:1 with their leader: nobody but the two of them.</li>
 * </ul>
 * A channel the caller cannot reach answers 404, so its existence does not leak. Messages are never work time.
 */
@Service
@RequiredArgsConstructor
public class IskurChannelService {

	static final int PAGE_SIZE = 50;
	/** Enough for a lively chat; a script posting in a loop stops here. */
	static final int POSTS_PER_MINUTE = 30;
	private static final long MINUTE_MS = 60_000;
	private static final Locale TR = Locale.forLanguageTag("tr");

	private final IskurChannelRepository channels;
	private final IskurChannelMemberRepository members;
	private final IskurMessageRepository messages;
	private final IskurMessageReceiptRepository receipts;
	private final IskurChannelReadRepository reads;
	private final IskurChannelMuteRepository mutes;
	private final IskurUnitRepository units;
	private final IskurTeamRepository teams;
	private final ProgramRepository programs;
	private final ProgramCoordinatorRepository programCoordinators;
	private final AppUserRepository users;
	private final ApplicationEventPublisher events;
	private final RateLimitingConfig rateLimiting;

	/** Every channel the caller can open, Genel Duyurular first; a coordinator can narrow it to one program. */
	@Transactional(readOnly = true)
	public IskurChannelListResponse list(UUID actorId, @Nullable UUID programId) {
		AppUser actor = requireActive(actorId);
		Map<UUID, Reach> reach = new LinkedHashMap<>();
		for (IskurChannelMember membership : members.findByUserIdAndLeftAtIsNull(actor.getId())) {
			reach.put(membership.getChannelId(), new Reach(membership.isCanWrite(), false));
		}
		Map<UUID, IskurChannel> byId = new HashMap<>();
		channels.findAllById(reach.keySet()).forEach(channel -> byId.put(channel.getId(), channel));
		if (programId != null) {
			byId.values().removeIf(channel -> !channel.getProgramId().equals(programId));
		}
		for (StaffScope scope : staffScopes(actor, programId)) {
			for (IskurChannel channel : channels.findByProgramId(scope.program().getId())) {
				Reach staff = scope.reach(actor, channel);
				if (staff != null) {
					byId.put(channel.getId(), channel);
					reach.merge(channel.getId(), staff, Reach::widest);
				}
			}
		}
		List<IskurChannelResponse> rows = describe(actor, byId.values(), reach);
		return new IskurChannelListResponse(rows,
				rows.stream().mapToLong(IskurChannelResponse::unreadCount).sum());
	}

	/** A page of the channel, oldest first; {@code before} names the oldest message already shown. */
	@Transactional(readOnly = true)
	public IskurMessagePageResponse messages(UUID actorId, UUID channelId, @Nullable UUID before) {
		AppUser actor = requireActive(actorId);
		Reached reached = requireReach(actor, channelId);
		PageRequest page = PageRequest.of(0, PAGE_SIZE + 1);
		List<IskurMessage> newestFirst;
		if (before == null) {
			newestFirst = messages.findByChannelIdOrderByCreatedAtDescIdDesc(channelId, page);
		} else {
			IskurMessage cursor = messages.findById(before)
					.filter(message -> message.getChannelId().equals(channelId))
					.orElseThrow(() -> new InvalidRequestException(Texts.forRequest("error.iskurMessageCursor")));
			newestFirst = messages.findPageBefore(channelId, cursor.getCreatedAt(), cursor.getId(), page);
		}
		boolean hasMore = newestFirst.size() > PAGE_SIZE;
		List<IskurMessage> shown = new ArrayList<>(newestFirst.subList(0, Math.min(PAGE_SIZE, newestFirst.size())));
		Collections.reverse(shown);
		IskurChannelResponse channel = describe(actor, List.of(reached.channel()),
				Map.of(channelId, reached.reach())).getFirst();
		return new IskurMessagePageResponse(channel, render(actor, reached, shown), hasMore);
	}

	/** Posts a message. A title and the important flag are for coordinators in Genel Duyurular. */
	@Transactional
	public IskurMessageResponse post(UUID actorId, UUID channelId, IskurMessageRequest request) {
		AppUser actor = requireActive(actorId);
		Reached reached = requireReach(actor, channelId);
		if (!reached.reach().canWrite()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, Texts.forRequest("error.iskurChannelReadOnly"));
		}
		String title = TextUtils.normalizeOptional(request.title());
		boolean important = Boolean.TRUE.equals(request.important());
		boolean announcer = reached.reach().staff() && reached.channel().getKind() == IskurChannelKind.ANNOUNCEMENTS;
		if ((title != null || important) && !announcer) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurAnnouncementOnly"));
		}
		String body = request.body().strip();
		if (body.isEmpty()) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurMessageEmpty"));
		}
		if (!rateLimiting.resolveBucket("iskur_post:" + actor.getId(), POSTS_PER_MINUTE, MINUTE_MS).tryConsume(1)) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					Texts.forRequest("error.iskurTooManyMessages"));
		}
		// The lock orders the channel's posts: each gets a later time than the one before, so lastMessageAt never
		// goes back and a read mark taken from it never covers a message still being written.
		IskurChannel channel = channels.findByIdForUpdate(channelId).orElseThrow(IskurChannelService::channelNotFound);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		Instant previous = channel.getLastMessageAt();
		if (previous != null && !now.isAfter(previous)) {
			now = previous.plus(1, ChronoUnit.MICROS);
		}
		IskurMessage message = messages.save(new IskurMessage(channelId, actor.getId(), title, body, important, now));
		channel.setLastMessageAt(now);
		channels.save(channel);
		events.publishEvent(new IskurMessagePostedEvent(message.getId()));
		events.publishEvent(new IskurChannelChangedEvent(IskurChannelEventType.MESSAGE_POSTED, channelId,
				message.getId()));
		return render(actor, new Reached(channel, reached.reach()), List.of(message)).getFirst();
	}

	/**
	 * Marks the channel read up to its newest message (its opening, while it has none). Not "now": a message still
	 * being written has a time before now and would be marked read unseen.
	 */
	@Transactional
	public void markRead(UUID actorId, UUID channelId) {
		AppUser actor = requireActive(actorId);
		IskurChannel channel = requireReach(actor, channelId).channel();
		Instant mark = channel.getLastMessageAt() != null ? channel.getLastMessageAt() : channel.getCreatedAt();
		IskurChannelRead read = reads.findByChannelIdAndUserId(channelId, actor.getId())
				.orElseGet(() -> new IskurChannelRead(channelId, actor.getId()));
		if (read.getLastReadAt() == null || read.getLastReadAt().isBefore(mark)) {
			read.setLastReadAt(mark);
		}
		reads.save(read);
	}

	/** "Okudum": a member confirms an important announcement. Doing it again keeps the first time. */
	@Transactional
	public IskurMessageResponse acknowledge(UUID actorId, UUID messageId) {
		AppUser actor = requireActive(actorId);
		IskurMessage message = messages.findById(messageId).orElseThrow(IskurChannelService::channelNotFound);
		Reached reached = requireReach(actor, message.getChannelId());
		if (!message.isImportant()) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurNotImportant"));
		}
		if (members.findByChannelIdAndUserIdAndLeftAtIsNull(message.getChannelId(), actor.getId()).isEmpty()) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurReceiptMembersOnly"));
		}
		if (receipts.findByMessageIdAndUserId(messageId, actor.getId()).isEmpty()) {
			receipts.save(new IskurMessageReceipt(messageId, actor.getId(), Instant.now()));
			events.publishEvent(new IskurChannelChangedEvent(IskurChannelEventType.MESSAGE_ACKNOWLEDGED,
					message.getChannelId(), messageId));
		}
		return render(actor, reached, List.of(message)).getFirst();
	}

	/** Who in the channel confirmed an important announcement; for the coordinators who post there. */
	@Transactional(readOnly = true)
	public IskurMessageReceiptsResponse receipts(UUID actorId, UUID messageId) {
		AppUser actor = requireActive(actorId);
		IskurMessage message = messages.findById(messageId).orElseThrow(IskurChannelService::channelNotFound);
		Reached reached = requireReach(actor, message.getChannelId());
		if (!reached.reach().staff() || !reached.reach().canWrite()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					Texts.forRequest("error.iskurReceiptsCoordinatorsOnly"));
		}
		if (!message.isImportant()) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurNotImportant"));
		}
		Map<UUID, Instant> readAt = new HashMap<>();
		receipts.findByMessageId(messageId).forEach(receipt -> readAt.put(receipt.getUserId(), receipt.getReadAt()));
		List<UUID> audience = members.findByChannelIdAndLeftAtIsNull(message.getChannelId()).stream()
				.map(IskurChannelMember::getUserId)
				.toList();
		Collator collator = Collator.getInstance(TR);
		List<IskurMessageReceiptsResponse.Reader> people = users.findAllById(audience).stream()
				.map(user -> new IskurMessageReceiptsResponse.Reader(user.getId(), user.getFullName(),
						readAt.get(user.getId())))
				.sorted(Comparator.comparing(IskurMessageReceiptsResponse.Reader::fullName, collator))
				.toList();
		long read = people.stream().filter(reader -> reader.readAt() != null).count();
		return new IskurMessageReceiptsResponse(messageId, read, people.size(), people);
	}

	@Transactional(readOnly = true)
	public IskurChannelPreferencesResponse preferences(UUID actorId, UUID programId) {
		AppUser actor = requireActive(actorId);
		requireProgramReach(actor, programId);
		return preferencesOf(actor.getId(), programId);
	}

	/** 6.1 Bildirimler: Genel Duyurular cannot be muted ("Önemli duyurular (kapatılamaz)"). */
	@Transactional
	public IskurChannelPreferencesResponse updatePreferences(UUID actorId, IskurChannelPreferencesRequest request) {
		AppUser actor = requireActive(actorId);
		UUID programId = request.programId();
		requireProgramReach(actor, programId);
		Set<IskurChannelKind> wanted = Set.copyOf(request.mutedKinds());
		if (wanted.stream().anyMatch(kind -> !kind.mutable())) {
			throw new InvalidRequestException(Texts.forRequest("error.iskurAnnouncementsAlwaysNotify"));
		}
		List<IskurChannelMute> current = mutes.findByProgramIdAndUserId(programId, actor.getId());
		mutes.deleteAll(current.stream().filter(mute -> !wanted.contains(mute.getKind())).toList());
		Set<IskurChannelKind> kept = current.stream().map(IskurChannelMute::getKind).collect(Collectors.toSet());
		mutes.saveAll(wanted.stream()
				.filter(kind -> !kept.contains(kind))
				.map(kind -> new IskurChannelMute(programId, actor.getId(), kind))
				.toList());
		return preferencesOf(actor.getId(), programId);
	}

	private IskurChannelPreferencesResponse preferencesOf(UUID userId, UUID programId) {
		return new IskurChannelPreferencesResponse(programId, mutes.findByProgramIdAndUserId(programId, userId)
				.stream()
				.map(IskurChannelMute::getKind)
				.sorted()
				.toList());
	}

	/** A member of one of the program's channels, or a coordinator of it. */
	private void requireProgramReach(AppUser actor, UUID programId) {
		List<UUID> joined = members.findByUserIdAndLeftAtIsNull(actor.getId()).stream()
				.map(IskurChannelMember::getChannelId)
				.toList();
		boolean member = channels.findAllById(joined).stream()
				.anyMatch(channel -> channel.getProgramId().equals(programId));
		if (!member && staffScopes(actor, programId).isEmpty()) {
			throw channelNotFound();
		}
	}

	private AppUser requireActive(UUID actorId) {
		AppUser actor = users.findById(actorId).orElseThrow(IskurChannelService::channelNotFound);
		if (actor.getStatus() != UserAccountStatus.APPROVED) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, Texts.forRequest("error.accountNotApproved"));
		}
		return actor;
	}

	/**
	 * Whether the REST reads would show this channel to the user right now: an approved account that is one of its
	 * current members, or a coordinator or admin whose program role reaches it. The live topic asks this, so a
	 * subscription never opens what a read would refuse.
	 */
	@Transactional(readOnly = true)
	public boolean canReach(UUID userId, UUID channelId) {
		return users.findById(userId)
				.filter(user -> user.getStatus() == UserAccountStatus.APPROVED)
				.map(user -> findReach(user, channelId) != null)
				.orElse(false);
	}

	/** The channel and how the caller reaches it, or 404 when they cannot. */
	private Reached requireReach(AppUser actor, UUID channelId) {
		Reached reached = findReach(actor, channelId);
		if (reached == null) {
			throw channelNotFound();
		}
		return reached;
	}

	private @Nullable Reached findReach(AppUser actor, UUID channelId) {
		IskurChannel channel = channels.findById(channelId).orElse(null);
		if (channel == null) {
			return null;
		}
		Reach reach = members.findByChannelIdAndUserIdAndLeftAtIsNull(channelId, actor.getId())
				.map(membership -> new Reach(membership.isCanWrite(), false))
				.orElse(null);
		for (StaffScope scope : staffScopes(actor, channel.getProgramId())) {
			Reach staff = scope.reach(actor, channel);
			if (staff != null) {
				reach = reach == null ? staff : reach.widest(staff);
			}
		}
		return reach == null ? null : new Reached(channel, reach);
	}

	/** A team's channel and a Liderler odası carry their unit's name beside their own. */
	private static boolean showsUnit(IskurChannelKind kind) {
		return kind == IskurChannelKind.TEAM || kind == IskurChannelKind.LEADERS;
	}

	private static ResourceNotFoundException channelNotFound() {
		return new ResourceNotFoundException(Texts.forRequest("error.iskurChannelNotFound"));
	}

	/** The peer programs the caller runs (all of them for an admin), with what is needed to judge their channels. */
	private List<StaffScope> staffScopes(AppUser actor, @Nullable UUID programId) {
		boolean admin = actor.has(UserRole.ADMIN);
		if (!admin && !actor.has(UserRole.COORDINATOR)) {
			return List.of();
		}
		List<Program> managed;
		if (programId != null) {
			Program program = programs.findById(programId).orElse(null);
			managed = program == null || !(admin || ProgramCoordination.coordinates(program, actor.getId(),
					programCoordinators)) ? List.of() : List.of(program);
		} else {
			managed = admin ? programs.findWithFeature(ProgramFeature.ISKUR)
					: ProgramCoordination.programsOf(actor.getId(), programs, programCoordinators);
		}
		return managed.stream()
				.filter(program -> program.has(ProgramFeature.ISKUR))
				.map(program -> new StaffScope(program, admin,
						units.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()).stream()
								.collect(Collectors.toMap(IskurUnit::getId, Function.identity())),
						teams.findByProgramIdOrderBySortOrderAscCreatedAtAsc(program.getId()).stream()
								.collect(Collectors.toMap(IskurTeam::getId, IskurTeam::getUnitId))))
				.toList();
	}

	private List<IskurChannelResponse> describe(AppUser actor, Collection<IskurChannel> shown,
			Map<UUID, Reach> reach) {
		if (shown.isEmpty()) {
			return List.of();
		}
		List<UUID> ids = shown.stream().map(IskurChannel::getId).toList();
		Map<UUID, Program> programById = programs
				.findAllById(shown.stream().map(IskurChannel::getProgramId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(Program::getId, Function.identity()));
		Map<UUID, IskurTeam> teamById = teams
				.findAllById(shown.stream().map(IskurChannel::getTeamId).filter(id -> id != null).toList()).stream()
				.collect(Collectors.toMap(IskurTeam::getId, Function.identity()));
		Set<UUID> unitIds = new HashSet<>();
		shown.stream().map(IskurChannel::getUnitId).filter(id -> id != null).forEach(unitIds::add);
		teamById.values().forEach(team -> unitIds.add(team.getUnitId()));
		Map<UUID, IskurUnit> unitById = units.findAllById(unitIds).stream()
				.collect(Collectors.toMap(IskurUnit::getId, Function.identity()));
		Set<UUID> counterpartIds = new HashSet<>();
		shown.stream().filter(channel -> channel.getKind() == IskurChannelKind.LEADER_DIRECT).forEach(channel -> {
			counterpartIds.add(channel.getLeaderId());
			counterpartIds.add(channel.getMemberId());
		});
		Map<UUID, AppUser> people = users.findAllById(counterpartIds).stream()
				.collect(Collectors.toMap(AppUser::getId, Function.identity()));
		Map<UUID, Long> internCount = members.findByChannelIdIn(ids).stream()
				.filter(IskurChannelMember::isActive)
				.collect(Collectors.groupingBy(IskurChannelMember::getChannelId, Collectors.counting()));
		Set<UUID> opened = reads.findByUserIdAndChannelIdIn(actor.getId(), ids).stream()
				.filter(read -> read.getLastReadAt() != null)
				.map(IskurChannelRead::getChannelId)
				.collect(Collectors.toSet());
		Map<UUID, Long> unread = new HashMap<>();
		messages.countUnread(ids, actor.getId(), Instant.EPOCH)
				.forEach(row -> unread.put(row.getChannelId(), row.getCount()));
		Map<UUID, Integer> coordinatorCount = new HashMap<>();

		Collator collator = Collator.getInstance(TR);
		List<IskurChannelResponse> rows = new ArrayList<>();
		for (IskurChannel channel : shown) {
			Program program = programById.get(channel.getProgramId());
			Reach access = reach.get(channel.getId());
			if (program == null || access == null) {
				continue;
			}
			IskurTeam team = channel.getTeamId() == null ? null : teamById.get(channel.getTeamId());
			IskurUnit unit = unitById.get(team != null ? team.getUnitId() : channel.getUnitId());
			IskurChannelResponse.Counterpart counterpart = null;
			if (channel.getKind() == IskurChannelKind.LEADER_DIRECT) {
				boolean iLead = actor.getId().equals(channel.getLeaderId());
				AppUser other = people.get(iLead ? channel.getMemberId() : channel.getLeaderId());
				if (other != null) {
					counterpart = new IskurChannelResponse.Counterpart(other.getId(), other.getFullName(), !iLead);
				}
			}
			String name = switch (channel.getKind()) {
			case ANNOUNCEMENTS -> Texts.get(actor, "iskur.channel.announcements");
			case UNIT -> unit == null ? "" : unit.getName();
			// The unit is in the name: a coordinator can see several units' rooms side by side.
			case LEADERS -> unit == null ? Texts.get(actor, "iskur.channel.leaders")
					: Texts.get(actor, "iskur.channel.leadersOf", unit.getName());
			case TEAM -> team == null ? "" : team.getName();
			case LEADER_DIRECT -> counterpart == null ? "" : counterpart.fullName();
			};
			int staffCount = switch (channel.getKind()) {
			case ANNOUNCEMENTS -> coordinatorCount.computeIfAbsent(program.getId(),
					id -> ProgramCoordination.coordinatorIds(program, programCoordinators).size());
			case UNIT, LEADERS -> unit != null && unit.getCoordinatorId() != null ? 1 : 0;
			case TEAM, LEADER_DIRECT -> 0;
			};
			rows.add(new IskurChannelResponse(channel.getId(), program.getId(), program.getName(), channel.getKind(),
					name, showsUnit(channel.getKind()) && unit != null ? unit.getName() : null,
					counterpart, internCount.getOrDefault(channel.getId(), 0L).intValue() + staffCount,
					access.canWrite(), access.staff(), unread.getOrDefault(channel.getId(), 0L),
					!opened.contains(channel.getId()), channel.getLastMessageAt()));
		}
		rows.sort(Comparator.comparing(IskurChannelResponse::kind)
				.thenComparing(IskurChannelResponse::programName, collator)
				.thenComparing(IskurChannelResponse::name, collator));
		return rows;
	}

	private List<IskurMessageResponse> render(AppUser actor, Reached reached, List<IskurMessage> shown) {
		if (shown.isEmpty()) {
			return List.of();
		}
		IskurChannel channel = reached.channel();
		Map<UUID, AppUser> senders = users.findAllById(shown.stream()
				.map(IskurMessage::getSenderId)
				.filter(id -> id != null)
				.collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(AppUser::getId, Function.identity()));
		Program program = programs.findById(channel.getProgramId()).orElse(null);
		Set<UUID> coordinators = program == null ? Set.of()
				: Set.copyOf(ProgramCoordination.coordinatorIds(program, programCoordinators));
		List<UUID> importantIds = shown.stream().filter(IskurMessage::isImportant).map(IskurMessage::getId).toList();
		Map<UUID, Instant> mine = importantIds.isEmpty() ? Map.of()
				: receipts.findByUserIdAndMessageIdIn(actor.getId(), importantIds).stream()
						.collect(Collectors.toMap(IskurMessageReceipt::getMessageId, IskurMessageReceipt::getReadAt));
		boolean seesReceipts = reached.reach().staff() && reached.reach().canWrite() && !importantIds.isEmpty();
		Map<UUID, Long> counts = new HashMap<>();
		long audience = 0;
		if (seesReceipts) {
			receipts.countByMessage(importantIds).forEach(row -> counts.put(row.getMessageId(), row.getCount()));
			audience = members.findByChannelIdAndLeftAtIsNull(channel.getId()).size();
		}
		List<IskurMessageResponse> rendered = new ArrayList<>();
		for (IskurMessage message : shown) {
			AppUser sender = message.getSenderId() == null ? null : senders.get(message.getSenderId());
			IskurMessageResponse.Sender from = sender == null ? null
					: new IskurMessageResponse.Sender(sender.getId(), sender.getFullName(),
							coordinators.contains(sender.getId()) || sender.has(UserRole.ADMIN));
			IskurMessageResponse.Receipts tally = seesReceipts && message.isImportant()
					? new IskurMessageResponse.Receipts(counts.getOrDefault(message.getId(), 0L), audience)
					: null;
			rendered.add(new IskurMessageResponse(message.getId(), message.getChannelId(), from, message.getTitle(),
					message.getBody(), message.isImportant(), message.getCreatedAt(),
					actor.getId().equals(message.getSenderId()), mine.get(message.getId()), tally));
		}
		return rendered;
	}

	/**
	 * How someone reaches a channel: whether they may post, and whether through their program role.
	 *
	 * @param staff true when it is a coordinator's or the admin's reach
	 */
	private record Reach(boolean canWrite, boolean staff) {
		Reach widest(Reach other) {
			return new Reach(canWrite || other.canWrite, staff || other.staff);
		}
	}

	private record Reached(IskurChannel channel, Reach reach) {
	}

	/**
	 * One peer program a coordinator or the admin runs.
	 *
	 * @param teamUnits each team's unit
	 */
	private record StaffScope(Program program, boolean admin, Map<UUID, IskurUnit> units, Map<UUID, UUID> teamUnits) {

		@Nullable
		Reach reach(AppUser actor, IskurChannel channel) {
			if (!channel.getProgramId().equals(program.getId())) {
				return null;
			}
			return switch (channel.getKind()) {
			case ANNOUNCEMENTS -> new Reach(true, true);
			case UNIT, LEADERS -> {
				IskurUnit unit = units.get(channel.getUnitId());
				yield unit == null ? null : unitReach(actor, unit, true);
			}
			case TEAM -> {
				UUID unitId = teamUnits.get(channel.getTeamId());
				IskurUnit unit = unitId == null ? null : units.get(unitId);
				yield unit == null ? null : unitReach(actor, unit, false);
			}
			case LEADER_DIRECT -> null;
			};
		}

		private @Nullable Reach unitReach(AppUser actor, IskurUnit unit, boolean mayWrite) {
			boolean ownUnit = actor.getId().equals(unit.getCoordinatorId());
			boolean unassigned = unit.getCoordinatorId() == null && !actor.has(UserRole.ADMIN);
			if (ownUnit || unassigned) {
				return new Reach(mayWrite, true);
			}
			return admin ? new Reach(false, true) : null;
		}
	}
}
