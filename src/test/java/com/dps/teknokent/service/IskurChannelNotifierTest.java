package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dps.teknokent.domain.IskurChannelKind;
import com.dps.teknokent.domain.NotificationCategory;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurChannel;
import com.dps.teknokent.entity.IskurChannelMember;
import com.dps.teknokent.entity.IskurChannelMute;
import com.dps.teknokent.entity.IskurChannelRead;
import com.dps.teknokent.entity.IskurMessage;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurChannelMemberRepository;
import com.dps.teknokent.repository.IskurChannelMuteRepository;
import com.dps.teknokent.repository.IskurChannelReadRepository;
import com.dps.teknokent.repository.IskurUnitRepository;

class IskurChannelNotifierTest {

	/** Thursday 19 Nov 2026, 10:00 in Istanbul. */
	private static final Instant THURSDAY_MORNING = Instant.parse("2026-11-19T07:00:00Z");

	private final IskurChannelMemberRepository members = mock(IskurChannelMemberRepository.class);
	private final IskurChannelReadRepository reads = mock(IskurChannelReadRepository.class);
	private final IskurChannelMuteRepository mutes = mock(IskurChannelMuteRepository.class);
	private final IskurUnitRepository units = mock(IskurUnitRepository.class);
	private final AppUserRepository users = mock(AppUserRepository.class);
	private final NotificationService notifications = mock(NotificationService.class);

	private final UUID programId = UUID.randomUUID();
	private final AppUser sender = user("Burak Tan");
	private final AppUser ayse = user("Ayşe Yıldız");
	private final AppUser ceren = user("Ceren Su");
	private final List<AppUser> known = new ArrayList<>(List.of(sender, ayse, ceren));
	private IskurChannel team;

	@BeforeEach
	void setUp() {
		team = new IskurChannel(programId, IskurChannelKind.TEAM, THURSDAY_MORNING);
		team.setTeamId(UUID.randomUUID());
		lenient().when(members.findByChannelIdAndLeftAtIsNull(team.getId())).thenReturn(List.of(
				member(team, sender), member(team, ayse), member(team, ceren)));
		lenient().when(users.findAllById(any())).thenAnswer(call -> known.stream()
				.filter(user -> ((Collection<?>) call.getArgument(0)).contains(user.getId()))
				.toList());
	}

	@Test
	void notifiesTheOthersInTheChannelDuringTheWorkingDay() {
		notifier(THURSDAY_MORNING).messagePosted(team, "Medya", message(false), sender, null);

		ArgumentCaptor<AppUser> recipients = ArgumentCaptor.forClass(AppUser.class);
		verify(notifications, times(2)).create(recipients.capture(),
				eq(NotificationCategory.MESSAGE), eq("Yeni mesaj · Medya"), eq("Burak Tan yazdı."),
				eq("/student/messages?channel=" + team.getId()));
		assertThat(recipients.getAllValues()).extracting(AppUser::getFullName)
				.containsExactlyInAnyOrder("Ayşe Yıldız", "Ceren Su");
	}

	@Test
	void staysQuietAfterSevenInTheEveningAndAtTheWeekend() {
		notifier(Instant.parse("2026-11-19T16:30:00Z")).messagePosted(team, "Medya", message(false), sender, null);
		notifier(Instant.parse("2026-11-21T08:00:00Z")).messagePosted(team, "Medya", message(false), sender, null);
		notifier(Instant.parse("2026-11-19T04:59:00Z")).messagePosted(team, "Medya", message(false), sender, null);

		verify(notifications, never()).create(any(), any(), anyString(), anyString(), anyString());
	}

	@Test
	void anImportantAnnouncementAlwaysGoesOut() {
		IskurChannel announcements = new IskurChannel(programId, IskurChannelKind.ANNOUNCEMENTS, THURSDAY_MORNING);
		when(members.findByChannelIdAndLeftAtIsNull(announcements.getId()))
				.thenReturn(List.of(member(announcements, ayse)));
		IskurMessage important = message(true);
		important.setTitle("İş sağlığı eğitimi");

		notifier(Instant.parse("2026-11-21T20:00:00Z"))
				.messagePosted(announcements, null, important, sender, THURSDAY_MORNING);

		verify(notifications).create(ayse, NotificationCategory.MESSAGE, "Önemli duyuru", "İş sağlığı eğitimi",
				"/student/messages?channel=" + announcements.getId());
	}

	@Test
	void skipsWhoMutedTheKindAndWhoAlreadyHasSomethingUnreadThere() {
		when(mutes.findByProgramIdAndKind(programId, IskurChannelKind.TEAM))
				.thenReturn(List.of(new IskurChannelMute(programId, ceren.getId(), IskurChannelKind.TEAM)));
		Instant previous = THURSDAY_MORNING.minusSeconds(600);
		when(reads.findByChannelId(team.getId())).thenReturn(List.of(
				new IskurChannelRead(team.getId(), ayse.getId(), previous.minusSeconds(60))));

		notifier(THURSDAY_MORNING).messagePosted(team, "Medya", message(false), sender, previous);

		verify(notifications, never()).create(any(), any(), anyString(), anyString(), anyString());
	}

	@Test
	void theFirstUnreadMessageInAChannelReadUpToDateNotifies() {
		Instant previous = THURSDAY_MORNING.minusSeconds(600);
		when(reads.findByChannelId(team.getId())).thenReturn(List.of(
				new IskurChannelRead(team.getId(), ayse.getId(), previous.plusSeconds(60))));

		notifier(THURSDAY_MORNING).messagePosted(team, "Medya", message(false), sender, previous);

		verify(notifications).create(eq(ayse), any(), anyString(), anyString(), anyString());
		verify(notifications, never()).create(eq(ceren), any(), anyString(), anyString(), anyString());
	}

	@Test
	void aUnitMessageAlsoReachesTheUnitCoordinatorOnTheirOwnScreen() {
		AppUser kaan = user("Kaan Yurt");
		IskurUnit unit = new IskurUnit(programId, "Kreatif", 0, kaan.getId(), THURSDAY_MORNING);
		unit.setCoordinatorId(kaan.getId());
		IskurChannel unitChannel = new IskurChannel(programId, IskurChannelKind.UNIT, THURSDAY_MORNING);
		unitChannel.setUnitId(unit.getId());
		when(members.findByChannelIdAndLeftAtIsNull(unitChannel.getId()))
				.thenReturn(List.of(member(unitChannel, sender)));
		when(units.findById(unit.getId())).thenReturn(Optional.of(unit));
		known.add(kaan);

		notifier(THURSDAY_MORNING).messagePosted(unitChannel, "Kreatif", message(false), sender, null);

		verify(notifications).create(kaan, NotificationCategory.MESSAGE, "Yeni mesaj · Kreatif", "Burak Tan yazdı.",
				"/coordinator/programs/" + programId + "/iskur/messages?channel=" + unitChannel.getId());
	}

	@Test
	void aOneToOneIsNamedAfterTheSenderAndSkipsAccountsThatAreNotApproved() {
		IskurChannel direct = new IskurChannel(programId, IskurChannelKind.LEADER_DIRECT, THURSDAY_MORNING);
		when(members.findByChannelIdAndLeftAtIsNull(direct.getId()))
				.thenReturn(List.of(member(direct, sender), member(direct, ayse), member(direct, ceren)));
		ceren.setStatus(UserAccountStatus.PENDING);

		notifier(THURSDAY_MORNING).messagePosted(direct, null, message(false), sender, null);

		verify(notifications).create(ayse, NotificationCategory.MESSAGE, "Yeni mesaj · Burak Tan", "Burak Tan yazdı.",
				"/student/messages?channel=" + direct.getId());
		verify(notifications, never()).create(eq(ceren), any(), anyString(), anyString(), anyString());
	}

	private IskurChannelNotifier notifier(Instant now) {
		return new IskurChannelNotifier(members, reads, mutes, units, users, notifications,
				Clock.fixed(now, ZoneOffset.UTC));
	}

	private IskurMessage message(boolean important) {
		return new IskurMessage(team.getId(), sender.getId(), null, "Toplantı 14:00'te", important,
				THURSDAY_MORNING);
	}

	private static IskurChannelMember member(IskurChannel channel, AppUser user) {
		return new IskurChannelMember(channel.getId(), user.getId(), true, THURSDAY_MORNING);
	}

	private static AppUser user(String name) {
		AppUser user = new AppUser();
		user.setId(UUID.randomUUID());
		user.setFullName(name);
		user.setRole(UserRole.MENTEE);
		return user;
	}
}
