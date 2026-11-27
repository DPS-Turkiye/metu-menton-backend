package com.dps.teknokent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import com.dps.teknokent.domain.DocumentKind;
import com.dps.teknokent.domain.ProgramMentorStatus;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.ProgramMentor;
import com.dps.teknokent.entity.StoredDocument;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.ProgramCoordinatorRepository;
import com.dps.teknokent.repository.ProgramMentorRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.repository.StoredDocumentRepository;
import com.dps.teknokent.util.AvatarImages;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonPhotoServiceTest {

	@Mock
	private AppUserRepository appUserRepository;
	@Mock
	private StoredDocumentRepository storedDocumentRepository;
	@Mock
	private ObjectStorage objectStorage;
	@Mock
	private PersonAccessService personAccessService;
	@Mock
	private MenteeProgramService menteeProgramService;
	@Mock
	private ProgramMentorRepository programMentorRepository;
	@Mock
	private ProgramRepository programRepository;
	@Mock
	private ProgramCoordinatorRepository programCoordinatorRepository;
	@Mock
	private PlatformTransactionManager transactionManager;

	@InjectMocks
	private PersonPhotoService service;

	private final List<Program> programs = new ArrayList<>();
	private final List<ProgramMentor> pools = new ArrayList<>();

	private AppUser person;
	private Program semester;

	@BeforeEach
	void setUp() {
		semester = program(ProgramStatus.ACTIVE);
		person = user(UserRole.MENTEE, UserAccountStatus.APPROVED);
		person.setProgramId(semester.getId());

		when(menteeProgramService.programOf(any(AppUser.class))).thenAnswer(
				invocation -> Optional.ofNullable(invocation.<AppUser>getArgument(0).getProgramId()));
		when(programRepository.findAllById(anyCollection())).thenAnswer(invocation -> {
			Collection<?> ids = invocation.getArgument(0);
			return programs.stream().filter(program -> ids.contains(program.getId())).toList();
		});
		// Only accepted rows come back, as the production query asks for.
		when(programMentorRepository.findByMentorIdAndStatusOrderByCreatedAtDesc(any(),
				eq(ProgramMentorStatus.ACCEPTED))).thenAnswer(invocation -> {
					UUID mentorId = invocation.getArgument(0);
					return pools.stream()
							.filter(row -> row.getMentorId().equals(mentorId)
									&& row.getStatus() == ProgramMentorStatus.ACCEPTED)
							.toList();
				});
	}

	// Rule 1: the person and approved admins.

	@Test
	void theirOwnPhotoShowsEvenWhilePending() {
		person.setStatus(UserAccountStatus.PENDING);

		assertThat(service.canSeePhoto(person, person)).isTrue();
	}

	@Test
	void anApprovedAdminSeesAnyonesPhotoPendingOnesToo() {
		person.setStatus(UserAccountStatus.PENDING);

		assertThat(service.canSeePhoto(user(UserRole.ADMIN, UserAccountStatus.APPROVED), person)).isTrue();
	}

	@Test
	void aPendingAdminSeesNoOneElsesPhoto() {
		assertThat(service.canSeePhoto(user(UserRole.ADMIN, UserAccountStatus.PENDING), person)).isFalse();
	}

	@Test
	void aDeletedPersonShowsNoPhotoEvenToAnAdmin() {
		person.setStatus(UserAccountStatus.DELETED);

		assertThat(service.canSeePhoto(user(UserRole.ADMIN, UserAccountStatus.APPROVED), person)).isFalse();
	}

	// Rule 2: whoever may open the person's profile.

	@Test
	void someoneWhoMayOpenTheProfileSeesThePhoto() {
		AppUser mentor = user(UserRole.MENTOR, UserAccountStatus.APPROVED);
		when(personAccessService.sees(mentor, person)).thenReturn(true);

		assertThat(service.canSeePhoto(mentor, person)).isTrue();
	}

	@Test
	void profileAccessStillShowsAPendingPersonsPhoto() {
		person.setStatus(UserAccountStatus.PENDING);
		AppUser teammate = user(UserRole.MENTEE, UserAccountStatus.APPROVED);
		when(personAccessService.sees(teammate, person)).thenReturn(true);

		assertThat(service.canSeePhoto(teammate, person)).isTrue();
	}

	@Test
	void aRejectedViewerGetsNothingEvenInTheSameProgram() {
		AppUser rejected = user(UserRole.MENTEE, UserAccountStatus.REJECTED);
		rejected.setProgramId(semester.getId());
		when(personAccessService.sees(rejected, person)).thenReturn(true);

		assertThat(service.canSeePhoto(rejected, person)).isFalse();
	}

	// Rule 3: coordinators of a program the person is in.

	@Test
	void theSemestersCoordinatorSeesItsStudentsPhoto() {
		AppUser coordinator = user(UserRole.COORDINATOR, UserAccountStatus.APPROVED);
		coordinate(semester, coordinator);

		assertThat(service.canSeePhoto(coordinator, person)).isTrue();
	}

	@Test
	void aCoordinatorOfAFinishedProgramStillSeesItsPeople() {
		semester.setStatus(ProgramStatus.COMPLETED);
		AppUser coordinator = user(UserRole.COORDINATOR, UserAccountStatus.APPROVED);
		coordinate(semester, coordinator);

		assertThat(service.canSeePhoto(coordinator, person)).isTrue();
	}

	@Test
	void theCoordinatorOfAPoolSeesItsAcceptedMentor() {
		AppUser mentor = user(UserRole.MENTOR, UserAccountStatus.APPROVED);
		Program peer = program(ProgramStatus.ACTIVE);
		pool(peer, mentor, ProgramMentorStatus.ACCEPTED);
		AppUser coordinator = user(UserRole.COORDINATOR, UserAccountStatus.APPROVED);
		coordinate(peer, coordinator);

		assertThat(service.canSeePhoto(coordinator, mentor)).isTrue();
	}

	@Test
	void aCoordinatorOfAnotherProgramDoesNot() {
		AppUser coordinator = user(UserRole.COORDINATOR, UserAccountStatus.APPROVED);
		coordinate(program(ProgramStatus.ACTIVE), coordinator);

		assertThat(service.canSeePhoto(coordinator, person)).isFalse();
	}

	@Test
	void aCoordinatorDoesNotSeeAPendingPersonsPhotoThroughTheirProgram() {
		person.setStatus(UserAccountStatus.PENDING);
		AppUser coordinator = user(UserRole.COORDINATOR, UserAccountStatus.APPROVED);
		coordinate(semester, coordinator);

		assertThat(service.canSeePhoto(coordinator, person)).isFalse();
	}

	@Test
	void aPendingCoordinatorDoesNot() {
		AppUser coordinator = user(UserRole.COORDINATOR, UserAccountStatus.PENDING);
		coordinate(semester, coordinator);

		assertThat(service.canSeePhoto(coordinator, person)).isFalse();
	}

	// Rule 4: people who share a running program.

	@Test
	void aStudentOfTheSameRunningSemesterSeesThePhoto() {
		AppUser classmate = user(UserRole.MENTEE, UserAccountStatus.APPROVED);
		classmate.setProgramId(semester.getId());

		assertThat(service.canSeePhoto(classmate, person)).isTrue();
	}

	@Test
	void aStudentSeesTheAcceptedPeerMentorOfTheirSemester() {
		AppUser peerMentor = user(UserRole.MENTOR, UserAccountStatus.APPROVED);
		pool(semester, peerMentor, ProgramMentorStatus.ACCEPTED);

		assertThat(service.canSeePhoto(person, peerMentor)).isTrue();
	}

	@Test
	void aMentorWhoOnlyAskedToJoinSharesNothing() {
		AppUser applicant = user(UserRole.MENTOR, UserAccountStatus.APPROVED);
		pool(semester, applicant, ProgramMentorStatus.PENDING);

		assertThat(service.canSeePhoto(person, applicant)).isFalse();
	}

	@Test
	void aFinishedProgramNoLongerSharesPhotos() {
		semester.setStatus(ProgramStatus.COMPLETED);
		AppUser classmate = user(UserRole.MENTEE, UserAccountStatus.APPROVED);
		classmate.setProgramId(semester.getId());

		assertThat(service.canSeePhoto(classmate, person)).isFalse();
	}

	@Test
	void aStudentOfAnotherProgramDoesNot() {
		AppUser stranger = user(UserRole.MENTEE, UserAccountStatus.APPROVED);
		stranger.setProgramId(program(ProgramStatus.ACTIVE).getId());

		assertThat(service.canSeePhoto(stranger, person)).isFalse();
	}

	@Test
	void aPendingClassmateDoesNot() {
		AppUser classmate = user(UserRole.MENTEE, UserAccountStatus.PENDING);
		classmate.setProgramId(semester.getId());

		assertThat(service.canSeePhoto(classmate, person)).isFalse();
	}

	@Test
	void aPendingPersonsPhotoIsNotSharedWithTheirClassmates() {
		person.setStatus(UserAccountStatus.PENDING);
		AppUser classmate = user(UserRole.MENTEE, UserAccountStatus.APPROVED);
		classmate.setProgramId(semester.getId());

		assertThat(service.canSeePhoto(classmate, person)).isFalse();
	}

	// The photo itself.

	@Test
	void sendsTheNewestPhotoShrunkForAnAvatar() throws IOException {
		AppUser admin = user(UserRole.ADMIN, UserAccountStatus.APPROVED);
		StoredDocument older = avatar("avatars/older", Instant.parse("2026-11-01T10:00:00Z"));
		StoredDocument newer = avatar("avatars/newer", Instant.parse("2026-11-20T10:00:00Z"));
		when(storedDocumentRepository.findByUserIdAndKind(person.getId(), DocumentKind.AVATAR))
				.thenReturn(List.of(older, newer));
		when(objectStorage.get("avatars/newer")).thenReturn(png(1024, 512));

		AvatarImages.Image photo = service.photo(admin.getId(), person.getId());

		assertThat(photo.contentType()).isEqualTo(AvatarImages.PNG);
		BufferedImage shown = ImageIO.read(new ByteArrayInputStream(photo.bytes()));
		assertThat(shown.getWidth()).isEqualTo(AvatarImages.MAX_SIDE);
		assertThat(shown.getHeight()).isEqualTo(AvatarImages.MAX_SIDE / 2);
	}

	@Test
	void someoneWithoutAPhotoIsNotFound() {
		when(storedDocumentRepository.findByUserIdAndKind(person.getId(), DocumentKind.AVATAR)).thenReturn(List.of());

		assertThatThrownBy(() -> service.photo(person.getId(), person.getId()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void aHiddenPhotoIsNotFoundAndNeverRead() {
		AppUser stranger = user(UserRole.MENTEE, UserAccountStatus.APPROVED);
		when(storedDocumentRepository.findByUserIdAndKind(person.getId(), DocumentKind.AVATAR))
				.thenReturn(List.of(avatar("avatars/hidden", Instant.now())));

		assertThatThrownBy(() -> service.photo(stranger.getId(), person.getId()))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(objectStorage, never()).get(any());
	}

	@Test
	void aStoredFileThatIsNotAPictureIsNotFound() {
		when(storedDocumentRepository.findByUserIdAndKind(person.getId(), DocumentKind.AVATAR))
				.thenReturn(List.of(avatar("avatars/broken", Instant.now())));
		when(objectStorage.get("avatars/broken")).thenReturn(new byte[] { 1, 2, 3 });

		assertThatThrownBy(() -> service.photo(person.getId(), person.getId()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void anUnknownPersonGetsTheSameAnswerAsAHiddenPhoto() {
		UUID nobody = UUID.randomUUID();

		assertThatThrownBy(() -> service.photo(person.getId(), nobody))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Photo not found: " + nobody);
	}

	@Test
	void aHiddenPhotoSaysOnlyThatThePhotoIsNotFound() {
		AppUser stranger = user(UserRole.MENTEE, UserAccountStatus.APPROVED);
		when(storedDocumentRepository.findByUserIdAndKind(person.getId(), DocumentKind.AVATAR))
				.thenReturn(List.of(avatar("avatars/hidden", Instant.now())));

		assertThatThrownBy(() -> service.photo(stranger.getId(), person.getId()))
				.hasMessage("Photo not found: " + person.getId());
	}

	@Test
	void aPhotoWhoseFileIsGoneFromStorageIsNotFound() {
		when(storedDocumentRepository.findByUserIdAndKind(person.getId(), DocumentKind.AVATAR))
				.thenReturn(List.of(avatar("avatars/gone", Instant.now())));

		assertThatThrownBy(() -> service.photo(person.getId(), person.getId()))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Photo not found: " + person.getId());
	}

	@Test
	void aFailedReadGivesItsTurnToDecodeBack() {
		when(storedDocumentRepository.findByUserIdAndKind(person.getId(), DocumentKind.AVATAR))
				.thenReturn(List.of(avatar("avatars/gone", Instant.now())));

		// More failures than there are turns: each must still answer at once, not wait for a turn.
		for (int attempt = 0; attempt < 10; attempt++) {
			assertThatThrownBy(() -> service.photo(person.getId(), person.getId()))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	private StoredDocument avatar(String key, Instant createdAt) {
		StoredDocument document = new StoredDocument();
		document.setId(UUID.randomUUID());
		document.setUserId(person.getId());
		document.setKind(DocumentKind.AVATAR);
		document.setS3Key(key);
		document.setContentType(AvatarImages.PNG);
		document.setCreatedAt(createdAt);
		return document;
	}

	private static byte[] png(int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private AppUser user(UserRole role, UserAccountStatus status) {
		AppUser user = new AppUser();
		user.setId(UUID.randomUUID());
		user.setFullName("Kişi " + role);
		user.setRole(role);
		user.setStatus(status);
		when(appUserRepository.findById(user.getId())).thenReturn(Optional.of(user));
		return user;
	}

	private Program program(ProgramStatus status) {
		Program program = new Program();
		program.setId(UUID.randomUUID());
		program.setProgramType(ProgramType.SEMESTER);
		program.setStatus(status);
		program.setCreatedBy(UUID.randomUUID());
		programs.add(program);
		return program;
	}

	private void coordinate(Program program, AppUser coordinator) {
		when(programCoordinatorRepository.existsByProgramIdAndUserId(program.getId(), coordinator.getId()))
				.thenReturn(true);
	}

	private void pool(Program program, AppUser mentor, ProgramMentorStatus status) {
		ProgramMentor row = new ProgramMentor();
		row.setProgramId(program.getId());
		row.setMentorId(mentor.getId());
		row.setStatus(status);
		pools.add(row);
	}
}
