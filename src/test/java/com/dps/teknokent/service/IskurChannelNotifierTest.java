package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

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
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
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

class IskurChannelNotifierTest {

	/** Thursday 19 Nov 2026, 10:00 in Istanbul. */
	private static final Instant THURSDAY_MORNING = Instant.parse("2026-11-19T07:00:00Z");

	private final IskurMessageRepository messages = mock(IskurMessageRepository.class);
	private final IskurChannelRepository channels = mock(IskurChannelRepository.class);
	private final IskurChannelMemberRepository members = mock(IskurChannelMemberRepository.class);
	private final IskurChannelReadRepository reads = mock(IskurChannelReadRepository.class);
	private final IskurChannelMuteRepository mutes = mock(IskurChannelMuteRepository.class);
	private final IskurUnitRepository units = mock(IskurUnitRepository.class);
	private final IskurTeamRepository teams = mock(IskurTeamRepository.class);
	private final ProgramRepository programs = mock(ProgramRepository.class);
	private final ProgramCoordinatorRepository programCoordinators = mock(ProgramCoordinatorRepository.class);
	private final AppUserRepository users = mock(AppUserRepository.class);
	private final NotificationService notifications = mock(NotificationService.class);
	private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

	private final UUID programId = UUID.randomUUID();
	private final AppUser sender = user("Burak Tan");
	private final AppUser ayse = user("Ayşe Yıldız");
	private final AppUser ceren = user("Ceren Su");
	private final List<AppUser> known = new ArrayList<>(List.of(sender, ayse, ceren));
	private IskurChannel team;

	@BeforeEach
	void setUp() {
		IskurTeam media = new IskurTeam(new IskurUnit(programId, "Kreatif", 0, sender.getId(), THURSDAY_MORNING),
				"Medya", 0, THURSDAY_MORNING);
		team = new IskurChannel(programId, IskurChannelKind.TEAM, THURSDAY_MORNING);
		team.setTeamId(media.getId());
		lenient().when(teams.findById(media.getId())).thenReturn(Optional.of(media));
		lenient().when(members.findByChannelIdAndLeftAtIsNull(team.getId())).thenReturn(List.of(
				member(team, sender), member(team, ayse), member(team, ceren)));
		lenient().when(users.findById(sender.getId())).thenReturn(Optional.of(sender));
		lenient().when(users.findAllById(any())).thenAnswer(call -> known.stream()
				.filter(user -> ((Collection<?>) call.getArgument(0)).contains(user.getId()))
				.toList());
	}

	@Test
	void notifiesTheOthersInTheChannelDuringTheWorkingDayAndRemembersIt() {
		notifier(THURSDAY_MORNING.plusSeconds(5)).messagePosted(posted(team, false));

		ArgumentCaptor<AppUser> recipients = ArgumentCaptor.forClass(AppUser.class);
		verify(notifications, times(2)).create(recipients.capture(), eq(NotificationCategory.MESSAGE),
				eq("Yeni mesaj · Medya"), eq("Burak Tan yazdı."), eq("/student/messages?channel=" + team.getId()));
		assertThat(recipients.getAllValues()).extracting(AppUser::getFullName)
				.containsExactlyInAnyOrder("Ayşe Yıldız", "Ceren Su");
		@SuppressWarnings("unchecked") // Mockito captors of a generic argument
		ArgumentCaptor<List<IskurChannelRead>> saved = ArgumentCaptor.forClass(List.class);
		verify(reads).saveAll(saved.capture());
		assertThat(saved.getValue()).extracting(IskurChannelRead::getNotifiedAt).containsOnly(THURSDAY_MORNING);
	}

	@Test
	void staysQuietAfterSevenInTheEveningBeforeEightAndAtTheWeekend() {
		notifier(Instant.parse("2026-11-19T16:30:00Z")).messagePosted(posted(team, false));
		notifier(Instant.parse("2026-11-21T08:00:00Z")).messagePosted(posted(team, false));
		notifier(Instant.parse("2026-11-19T04:59:00Z")).messagePosted(posted(team, false));

		verify(notifications, never()).create(any(), any(), anyString(), anyString(), anyString());
	}

	@Test
	void anImportantAnnouncementAlwaysGoesOutEvenToWhoWasJustNotified() {
		IskurChannel announcements = new IskurChannel(programId, IskurChannelKind.ANNOUNCEMENTS, THURSDAY_MORNING);
		when(members.findByChannelIdAndLeftAtIsNull(announcements.getId()))
				.thenReturn(List.of(member(announcements, ayse)));
		IskurChannelRead notifiedBefore = new IskurChannelRead(announcements.getId(), ayse.getId());
		notifiedBefore.setNotifiedAt(THURSDAY_MORNING.minusSeconds(60));
		when(reads.findByChannelId(announcements.getId())).thenReturn(List.of(notifiedBefore));
		UUID id = posted(announcements, true);
		messageById(id).setTitle("İş sağlığı eğitimi");

		notifier(Instant.parse("2026-11-21T20:00:00Z")).messagePosted(id);

		verify(notifications).create(ayse, NotificationCategory.MESSAGE, "Önemli duyuru", "İş sağlığı eğitimi",
				"/student/messages?channel=" + announcements.getId());
	}

	@Test
	void skipsWhoMutedTheKindAndWhoHasNotReadSinceTheLastNotification() {
		when(mutes.findByProgramIdAndKind(programId, IskurChannelKind.TEAM))
				.thenReturn(List.of(new IskurChannelMute(programId, ceren.getId(), IskurChannelKind.TEAM)));
		IskurChannelRead stale = new IskurChannelRead(team.getId(), ayse.getId());
		stale.setNotifiedAt(THURSDAY_MORNING.minusSeconds(600));
		stale.setLastReadAt(THURSDAY_MORNING.minusSeconds(900));
		when(reads.findByChannelId(team.getId())).thenReturn(List.of(stale));

		notifier(THURSDAY_MORNING).messagePosted(posted(team, false));

		verify(notifications, never()).create(any(), any(), anyString(), anyString(), anyString());
	}

	@Test
	void readingUpToTheNotifiedMessageOpensTheNextNotification() {
		IskurChannelRead readTheLastOne = new IskurChannelRead(team.getId(), ayse.getId());
		readTheLastOne.setNotifiedAt(THURSDAY_MORNING.minusSeconds(600));
		readTheLastOne.setLastReadAt(THURSDAY_MORNING.minusSeconds(600));
		when(reads.findByChannelId(team.getId())).thenReturn(List.of(readTheLastOne));

		notifier(THURSDAY_MORNING).messagePosted(posted(team, false));

		verify(notifications).create(eq(ayse), any(), anyString(), anyString(), anyString());
	}

	@Test
	void readingTheChannelAfterANotificationOpensTheNextOne() {
		IskurChannelRead caughtUp = new IskurChannelRead(team.getId(), ayse.getId());
		caughtUp.setNotifiedAt(THURSDAY_MORNING.minusSeconds(600));
		caughtUp.setLastReadAt(THURSDAY_MORNING.minusSeconds(300));
		when(reads.findByChannelId(team.getId())).thenReturn(List.of(caughtUp));

		notifier(THURSDAY_MORNING).messagePosted(posted(team, false));

		verify(notifications).create(eq(ayse), any(), anyString(), anyString(), anyString());
		verify(notifications).create(eq(ceren), any(), anyString(), anyString(), anyString());
	}

	@Test
	void aUnitMessageReachesTheUnitCoordinatorOnlyWhileTheyStillRunTheProgram() {
		AppUser kaan = user("Kaan Yurt");
		known.add(kaan);
		IskurUnit unit = new IskurUnit(programId, "Kreatif", 0, kaan.getId(), THURSDAY_MORNING);
		unit.setCoordinatorId(kaan.getId());
		IskurChannel unitChannel = new IskurChannel(programId, IskurChannelKind.UNIT, THURSDAY_MORNING);
		unitChannel.setUnitId(unit.getId());
		when(members.findByChannelIdAndLeftAtIsNull(unitChannel.getId()))
				.thenReturn(List.of(member(unitChannel, sender)));
		when(units.findById(unit.getId())).thenReturn(Optional.of(unit));
		Program program = new Program();
		program.setId(programId);
		program.setCreatedBy(kaan.getId());
		when(programs.findById(programId)).thenReturn(Optional.of(program));

		notifier(THURSDAY_MORNING).messagePosted(posted(unitChannel, false));
		verify(notifications).create(kaan, NotificationCategory.MESSAGE, "Yeni mesaj · Kreatif", "Burak Tan yazdı.",
				"/coordinator/programs/" + programId + "/iskur/messages?channel=" + unitChannel.getId());

		program.setCreatedBy(UUID.randomUUID());
		notifier(THURSDAY_MORNING).messagePosted(posted(unitChannel, false));
		verify(notifications, times(1)).create(eq(kaan), any(), anyString(), anyString(), anyString());
	}

	@Test
	void aOneToOneIsNamedAfterTheSenderAndSkipsAccountsThatAreNotApproved() {
		IskurChannel direct = new IskurChannel(programId, IskurChannelKind.LEADER_DIRECT, THURSDAY_MORNING);
		when(members.findByChannelIdAndLeftAtIsNull(direct.getId()))
				.thenReturn(List.of(member(direct, sender), member(direct, ayse), member(direct, ceren)));
		ceren.setStatus(UserAccountStatus.PENDING);

		notifier(THURSDAY_MORNING).messagePosted(posted(direct, false));

		verify(notifications).create(ayse, NotificationCategory.MESSAGE, "Yeni mesaj · Burak Tan", "Burak Tan yazdı.",
				"/student/messages?channel=" + direct.getId());
		verify(notifications, never()).create(eq(ceren), any(), anyString(), anyString(), anyString());
	}

	@Test
	void aMessageThatIsGoneNotifiesNobody() {
		notifier(THURSDAY_MORNING).messagePosted(UUID.randomUUID());
		verify(notifications, never()).create(any(), any(), anyString(), anyString(), anyString());
	}

	@Test
	void aFailedNotificationIsLoggedAndNeverReachesTheSender() {
		when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		when(messages.findById(any())).thenThrow(new IllegalStateException("database is gone"));

		assertThatCode(() -> notifier(THURSDAY_MORNING).onMessagePosted(new IskurMessagePostedEvent(UUID.randomUUID())))
				.doesNotThrowAnyException();
	}

	private IskurChannelNotifier notifier(Instant now) {
		return new IskurChannelNotifier(new IskurChannelNotifier.Repositories(messages, channels, members, reads,
				mutes, units, teams, programs, programCoordinators, users), notifications, transactions,
				Clock.fixed(now, ZoneOffset.UTC));
	}

	private final List<IskurMessage> posted = new ArrayList<>();

	private UUID posted(IskurChannel channel, boolean important) {
		IskurMessage message = new IskurMessage(channel.getId(), sender.getId(), null, "Toplantı 14:00'te", important,
				THURSDAY_MORNING);
		posted.add(message);
		when(messages.findById(message.getId())).thenReturn(Optional.of(message));
		when(channels.findById(channel.getId())).thenReturn(Optional.of(channel));
		return message.getId();
	}

	private IskurMessage messageById(UUID id) {
		return posted.stream().filter(message -> message.getId().equals(id)).findFirst().orElseThrow();
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
