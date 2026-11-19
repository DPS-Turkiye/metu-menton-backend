package com.dps.teknokent.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dps.teknokent.domain.IskurChannelKind;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurChannel;
import com.dps.teknokent.entity.IskurChannelMember;
import com.dps.teknokent.entity.IskurChannelMute;
import com.dps.teknokent.entity.IskurChannelRead;
import com.dps.teknokent.entity.IskurMessage;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurChannelMemberRepository;
import com.dps.teknokent.repository.IskurChannelMuteRepository;
import com.dps.teknokent.repository.IskurChannelReadRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.util.MeetingSlotTimes;
import com.dps.teknokent.util.TextUtils;
import com.dps.teknokent.util.Texts;

/**
 * Notifications for new channel messages (6.1 Bildirimler). An important announcement reaches everyone in the channel
 * at once and cannot be muted. Any other message notifies only on a weekday between 08:00 and 19:00, only people who
 * have not muted that kind of channel, and only when it is the first one they have not read there, so a busy team
 * channel sends one notification rather than one per message. The unread counts show the rest.
 */
@Service
public class IskurChannelNotifier {

	static final LocalTime DAY_STARTS = LocalTime.of(8, 0);
	static final LocalTime DAY_ENDS = LocalTime.of(19, 0);
	static final int PREVIEW_LENGTH = 120;

	private final IskurChannelMemberRepository members;
	private final IskurChannelReadRepository reads;
	private final IskurChannelMuteRepository mutes;
	private final IskurUnitRepository units;
	private final AppUserRepository users;
	private final NotificationService notifications;
	private final Clock clock;

	@Autowired
	public IskurChannelNotifier(IskurChannelMemberRepository members, IskurChannelReadRepository reads,
			IskurChannelMuteRepository mutes, IskurUnitRepository units, AppUserRepository users,
			NotificationService notifications) {
		this(members, reads, mutes, units, users, notifications, Clock.systemUTC());
	}

	IskurChannelNotifier(IskurChannelMemberRepository members, IskurChannelReadRepository reads,
			IskurChannelMuteRepository mutes, IskurUnitRepository units, AppUserRepository users,
			NotificationService notifications, Clock clock) {
		this.members = members;
		this.reads = reads;
		this.mutes = mutes;
		this.units = units;
		this.users = users;
		this.notifications = notifications;
		this.clock = clock;
	}

	/**
	 * Notifies the channel's people about a new message.
	 *
	 * @param placeName         the unit's or the team's name, for those channels
	 * @param previousMessageAt when the channel's last message before this one was sent, or null for its first
	 */
	public void messagePosted(IskurChannel channel, @Nullable String placeName, IskurMessage message,
			AppUser sender, @Nullable Instant previousMessageAt) {
		Map<UUID, String> hrefs = new LinkedHashMap<>();
		String internHref = "/student/messages?channel=" + channel.getId();
		for (IskurChannelMember member : members.findByChannelIdAndLeftAtIsNull(channel.getId())) {
			hrefs.put(member.getUserId(), internHref);
		}
		if (channel.getKind() == IskurChannelKind.UNIT && channel.getUnitId() != null) {
			units.findById(channel.getUnitId())
					.map(unit -> unit.getCoordinatorId())
					.ifPresent(coordinatorId -> hrefs.putIfAbsent(coordinatorId, "/coordinator/programs/"
							+ channel.getProgramId() + "/iskur/messages?channel=" + channel.getId()));
		}
		hrefs.remove(sender.getId());
		if (!message.isImportant()) {
			if (!duringTheWorkingDay()) {
				return;
			}
			if (channel.getKind().mutable()) {
				mutes.findByProgramIdAndKind(channel.getProgramId(), channel.getKind()).stream()
						.map(IskurChannelMute::getUserId)
						.forEach(hrefs::remove);
			}
			if (previousMessageAt != null) {
				Map<UUID, Instant> readUpTo = new HashMap<>();
				for (IskurChannelRead read : reads.findByChannelId(channel.getId())) {
					readUpTo.put(read.getUserId(), read.getLastReadAt());
				}
				hrefs.keySet().removeIf(userId -> {
					Instant read = readUpTo.get(userId);
					return read == null || read.isBefore(previousMessageAt);
				});
			}
		}
		if (hrefs.isEmpty()) {
			return;
		}
		for (AppUser recipient : users.findAllById(hrefs.keySet())) {
			if (recipient.getStatus() != UserAccountStatus.APPROVED) {
				continue;
			}
			String href = hrefs.getOrDefault(recipient.getId(), internHref);
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
		}
	}

	/** "Görev günü dışında ve 19:00'dan sonra bildirim gönderilmez." Work days are weekdays until onboarding lands. */
	boolean duringTheWorkingDay() {
		ZonedDateTime now = clock.instant().atZone(MeetingSlotTimes.APP_ZONE);
		DayOfWeek day = now.getDayOfWeek();
		LocalTime time = now.toLocalTime();
		return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
				&& !time.isBefore(DAY_STARTS) && time.isBefore(DAY_ENDS);
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
