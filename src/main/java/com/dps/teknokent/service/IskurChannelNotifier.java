package com.dps.teknokent.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.dps.teknokent.domain.IskurChannelKind;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurChannel;
import com.dps.teknokent.entity.IskurChannelMember;
import com.dps.teknokent.entity.IskurChannelMute;
import com.dps.teknokent.entity.IskurChannelRead;
import com.dps.teknokent.entity.IskurMessage;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurChannelMemberRepository;
import com.dps.teknokent.repository.IskurChannelMuteRepository;
import com.dps.teknokent.repository.IskurChannelReadRepository;
import com.dps.teknokent.repository.IskurChannelRepository;
import com.dps.teknokent.repository.IskurMessageRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.util.MeetingSlotTimes;
import com.dps.teknokent.util.ProgramCoordination;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

import lombok.extern.slf4j.Slf4j;

/**
 * Notifications for new channel messages (6.1 Bildirimler). An important announcement reaches everyone in the channel
 * at once and cannot be muted. Any other message notifies only on a weekday between 08:00 and 19:00, only people who
 * have not muted that kind of channel, and only people who have read the channel since their last notification about
 * it, so a busy team channel sends one notification rather than one per message. A message that notified nobody
 * (quiet hours, a mute) leaves the next one free to notify. The unread counts show the rest.
 */
@Slf4j
@Service
public class IskurChannelNotifier {

	static final LocalTime DAY_STARTS = LocalTime.of(8, 0);
	static final LocalTime DAY_ENDS = LocalTime.of(19, 0);
	static final int PREVIEW_LENGTH = 120;

	private final Repositories repositories;
	private final NotificationService notifications;
	private final PlatformTransactionManager transactionManager;
	private final Clock clock;

	/** What the notifier reads, grouped so the constructors stay short. */
	public record Repositories(IskurMessageRepository messages, IskurChannelRepository channels,
			IskurChannelMemberRepository members, IskurChannelReadRepository reads, IskurChannelMuteRepository mutes,
			IskurUnitRepository units, IskurTeamRepository teams, ProgramRepository programs,
			ProgramCoordinatorRepository programCoordinators, AppUserRepository users) {
	}

	@Autowired
	public IskurChannelNotifier(IskurMessageRepository messages, IskurChannelRepository channels,
			IskurChannelMemberRepository members, IskurChannelReadRepository reads, IskurChannelMuteRepository mutes,
			IskurUnitRepository units, IskurTeamRepository teams, ProgramRepository programs,
			ProgramCoordinatorRepository programCoordinators, AppUserRepository users,
			NotificationService notifications, PlatformTransactionManager transactionManager) {
		this(new Repositories(messages, channels, members, reads, mutes, units, teams, programs, programCoordinators,
				users), notifications, transactionManager, Clock.systemUTC());
	}

	IskurChannelNotifier(Repositories repositories, NotificationService notifications,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.repositories = repositories;
		this.notifications = notifications;
		this.transactionManager = transactionManager;
		this.clock = clock;
	}

	/** After the message is committed, in a transaction of its own; a failure is logged and the message stays. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMessagePosted(IskurMessagePostedEvent event) {
		TransactionTemplate own = new TransactionTemplate(transactionManager);
		own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		try {
			own.executeWithoutResult(status -> messagePosted(event.messageId()));
		} catch (RuntimeException failure) {
			log.warn("İŞKUR message notification failed for message {}", event.messageId(), failure);
		}
	}

	/** Notifies the channel's people about one message. */
	void messagePosted(UUID messageId) {
		IskurMessage message = repositories.messages().findById(messageId).orElse(null);
		IskurChannel channel = message == null ? null
				: repositories.channels().findById(message.getChannelId()).orElse(null);
		AppUser sender = message == null || message.getSenderId() == null ? null
				: repositories.users().findById(message.getSenderId()).orElse(null);
		if (message == null || channel == null || sender == null) {
			return;
		}
		Map<UUID, String> hrefs = recipients(channel);
		hrefs.remove(sender.getId());
		Map<UUID, IskurChannelRead> readsByUser = new HashMap<>();
		repositories.reads().findByChannelId(channel.getId())
				.forEach(read -> readsByUser.put(read.getUserId(), read));
		if (!message.isImportant()) {
			if (!duringTheWorkingDay()) {
				return;
			}
			if (channel.getKind().mutable()) {
				repositories.mutes().findByProgramIdAndKind(channel.getProgramId(), channel.getKind()).stream()
						.map(IskurChannelMute::getUserId)
						.forEach(hrefs::remove);
			}
			hrefs.keySet().removeIf(userId -> {
				IskurChannelRead read = readsByUser.get(userId);
				return read != null && !read.readSinceLastNotified();
			});
		}
		if (hrefs.isEmpty()) {
			return;
		}
		String placeName = placeName(channel);
		Instant now = clock.instant();
		List<IskurChannelRead> notified = new ArrayList<>();
		for (AppUser recipient : repositories.users().findAllById(hrefs.keySet())) {
			if (recipient.getStatus() != UserAccountStatus.APPROVED) {
				continue;
			}
			String href = hrefs.getOrDefault(recipient.getId(), internHref(channel));
			if (message.isImportant()) {
				String about = message.getTitle() != null ? message.getTitle()
						: TextUtils.clip(message.getBody(), PREVIEW_LENGTH);
				notifications.create(recipient, NotificationCategory.MESSAGE,
						Texts.get(recipient, "notification.iskurImportant.title"),
						Texts.get(recipient, "notification.iskurImportant.body", about), href);
			} else {
				notifications.create(recipient, NotificationCategory.MESSAGE,
						Texts.get(recipient, "notification.iskurMessage.title",
								channelName(channel, placeName, sender, recipient)),
						Texts.get(recipient, "notification.iskurMessage.body", sender.getFullName()), href);
			}
			IskurChannelRead read = readsByUser.computeIfAbsent(recipient.getId(),
					userId -> new IskurChannelRead(channel.getId(), userId));
			read.setNotifiedAt(now);
			notified.add(read);
		}
		repositories.reads().saveAll(notified);
	}

	/** The channel's interns, plus a unit channel's coordinator while they still run the program. */
	private Map<UUID, String> recipients(IskurChannel channel) {
		Map<UUID, String> hrefs = new LinkedHashMap<>();
		String internHref = internHref(channel);
		for (IskurChannelMember member : repositories.members().findByChannelIdAndLeftAtIsNull(channel.getId())) {
			hrefs.put(member.getUserId(), internHref);
		}
		UUID coordinatorId = unitCoordinator(channel);
		if (coordinatorId != null) {
			hrefs.putIfAbsent(coordinatorId, "/coordinator/programs/" + channel.getProgramId()
					+ "/iskur/messages?channel=" + channel.getId());
		}
		return hrefs;
	}

	private @Nullable UUID unitCoordinator(IskurChannel channel) {
		if (channel.getKind() != IskurChannelKind.UNIT || channel.getUnitId() == null) {
			return null;
		}
		UUID coordinatorId = repositories.units().findById(channel.getUnitId())
				.map(IskurUnit::getCoordinatorId)
				.orElse(null);
		if (coordinatorId == null) {
			return null;
		}
		boolean stillCoordinates = repositories.programs().findById(channel.getProgramId())
				.map(program -> ProgramCoordination.coordinates(program, coordinatorId,
						repositories.programCoordinators()))
				.orElse(false);
		return stillCoordinates ? coordinatorId : null;
	}

	/** "Görev günü dışında ve 19:00'dan sonra bildirim gönderilmez." Work days are weekdays until onboarding lands. */
	boolean duringTheWorkingDay() {
		ZonedDateTime now = clock.instant().atZone(MeetingSlotTimes.APP_ZONE);
		DayOfWeek day = now.getDayOfWeek();
		LocalTime time = now.toLocalTime();
		return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
				&& !time.isBefore(DAY_STARTS) && time.isBefore(DAY_ENDS);
	}

	private @Nullable String placeName(IskurChannel channel) {
		if (channel.getTeamId() != null) {
			return repositories.teams().findById(channel.getTeamId()).map(IskurTeam::getName).orElse(null);
		}
		if (channel.getUnitId() != null) {
			return repositories.units().findById(channel.getUnitId()).map(IskurUnit::getName).orElse(null);
		}
		return null;
	}

	private static String internHref(IskurChannel channel) {
		return "/student/messages?channel=" + channel.getId();
	}

	private static String channelName(IskurChannel channel, @Nullable String placeName, AppUser sender,
			AppUser recipient) {
		return switch (channel.getKind()) {
		case ANNOUNCEMENTS -> Texts.get(recipient, "iskur.channel.announcements");
		case LEADER_DIRECT -> sender.getFullName();
		case UNIT, TEAM -> placeName == null ? "" : placeName;
		};
	}
}
