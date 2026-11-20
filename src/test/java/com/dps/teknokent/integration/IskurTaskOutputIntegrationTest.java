package com.dps.teknokent.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.DocumentKind;
import com.dps.teknokent.domain.IskurTaskKind;
import com.dps.teknokent.domain.IskurTeamRole;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.IskurInternEmail;
import com.dps.teknokent.entity.IskurPlacement;
import com.dps.teknokent.entity.IskurTask;
import com.dps.teknokent.entity.IskurTaskOutput;
import com.dps.teknokent.entity.IskurTeam;
import com.dps.teknokent.entity.IskurUnit;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.entity.StoredDocument;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.IskurInternEmailRepository;
import com.dps.teknokent.repository.IskurPlacementRepository;
import com.dps.teknokent.repository.IskurTaskOutputRepository;
import com.dps.teknokent.repository.IskurTaskRepository;
import com.dps.teknokent.repository.IskurTeamRepository;
import com.dps.teknokent.repository.IskurUnitRepository;
import com.dps.teknokent.repository.ProgramRepository;
import com.dps.teknokent.repository.StoredDocumentRepository;
import com.dps.teknokent.security.AppUserPrincipal;
import com.dps.teknokent.service.ObjectStorage;
import com.dps.teknokent.util.MeetingSlotTimes;

/** The files handed in with İŞKUR tasks, through the ODTÜ edition's real security chain and an H2 database. */
@SpringBootTest(properties = "app.edition=ODTU")
@AutoConfigureMockMvc
@Transactional
class IskurTaskOutputIntegrationTest {

	private static final String TASK = "/api/iskur/tasks/{taskId}";
	private static final String OUTPUTS = TASK + "/outputs";
	private static final String OUTPUT = OUTPUTS + "/{outputId}";
	private static final String COORDINATOR = "/api/coordinator/programs/{programId}/iskur/task-outputs";
	private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private AppUserRepository userRepository;
	@Autowired
	private ProgramRepository programRepository;
	@Autowired
	private IskurUnitRepository unitRepository;
	@Autowired
	private IskurTeamRepository teamRepository;
	@Autowired
	private IskurPlacementRepository placementRepository;
	@Autowired
	private IskurTaskRepository taskRepository;
	@Autowired
	private IskurTaskOutputRepository outputRepository;
	@Autowired
	private IskurInternEmailRepository internEmailRepository;
	@Autowired
	private StoredDocumentRepository storedDocumentRepository;

	@MockitoBean
	private ObjectStorage objectStorage;

	private AppUser coordinator;
	private Program program;
	private IskurUnit unit;
	private IskurTeam team;
	private AppUser zeynep;
	private AppUser kaan;
	private AppUser mert;
	private AppUser ayse;
	private AppUser lale;
	private UUID taskId;

	@BeforeEach
	void setUp() {
		coordinator = user("Selin Er", UserRole.COORDINATOR, null);
		program = program("İŞKUR Güz 2026", coordinator);
		unit = unitRepository.saveAndFlush(new IskurUnit(program.getId(), "Kreatif ve Dijital Üretim", 0,
				coordinator.getId(), Instant.now()));
		team = teamRepository.saveAndFlush(new IskurTeam(unit, "Medya ve Kreatif Üretim", 0, Instant.now()));
		IskurTeam otherTeam = teamRepository.saveAndFlush(new IskurTeam(unit, "Dijital Dönüşüm", 1, Instant.now()));

		zeynep = user("Zeynep Kaya", UserRole.MENTEE, program);
		kaan = user("Kaan Yurt", UserRole.MENTEE, program);
		mert = user("Mert Demir", UserRole.MENTEE, program);
		ayse = user("Ayşe Yıldız", UserRole.MENTEE, program);
		lale = user("Lale Aksoy", UserRole.MENTEE, program);
		place(program, zeynep, team, IskurTeamRole.LEADER, null);
		place(program, kaan, team, IskurTeamRole.LEADER, null);
		place(program, mert, team, IskurTeamRole.MEMBER, zeynep);
		place(program, ayse, team, IskurTeamRole.MEMBER, zeynep);
		place(program, lale, otherTeam, IskurTeamRole.LEADER, null);

		taskId = task(program, team, mert, zeynep, "Afiş için 2 görsel öneri").getId();
		when(objectStorage.isConfigured()).thenReturn(true);
		when(objectStorage.require(anyString())).thenReturn(PDF);
	}

	@Test
	void theAssigneeHandsInAFileAndTheTeamsLeadersReadIt() throws Exception {
		upload(mert, taskId, pdf("rapor.pdf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outputFilesEnabled").value(true))
				.andExpect(jsonPath("$.canAttach").value(true))
				.andExpect(jsonPath("$.outputFiles.length()").value(1))
				.andExpect(jsonPath("$.outputFiles[0].fileName").value("rapor.pdf"))
				.andExpect(jsonPath("$.outputFiles[0].contentType").value("application/pdf"))
				.andExpect(jsonPath("$.outputFiles[0].sizeBytes").value(PDF.length))
				.andExpect(jsonPath("$.outputFiles[0].canRemove").value(true));
		IskurTaskOutput output = onlyOutput();
		StoredDocument file = storedDocumentRepository.findById(output.getDocumentId()).orElseThrow();
		assertThat(file.getKind()).isEqualTo(DocumentKind.ISKUR_TASK_OUTPUT);
		assertThat(file.getUserId()).isEqualTo(mert.getId());
		assertThat(output.getUploadedBy()).isEqualTo(mert.getId());

		// Both leaders of the team read and download; neither may add or remove.
		for (AppUser leader : List.of(zeynep, kaan)) {
			task(leader, taskId)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.outputFiles[0].fileName").value("rapor.pdf"))
					.andExpect(jsonPath("$.outputFiles[0].canRemove").value(false))
					.andExpect(jsonPath("$.canAttach").value(false));
			download(leader, taskId, output.getId())
					.andExpect(status().isOk())
					.andExpect(content().bytes(PDF))
					.andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment;")))
					.andExpect(header().string("X-Content-Type-Options", "nosniff"))
					.andExpect(header().string("Content-Type", "application/pdf"));
			upload(leader, taskId, pdf("lider.pdf")).andExpect(status().isForbidden());
			remove(leader, taskId, output.getId()).andExpect(status().isForbidden());
		}
		download(mert, taskId, output.getId()).andExpect(status().isOk());
		assertThat(outputRepository.count()).isEqualTo(1);
	}

	@Test
	void nobodyElseReachesATasksFiles() throws Exception {
		upload(mert, taskId, pdf("rapor.pdf")).andExpect(status().isOk());
		UUID outputId = onlyOutput().getId();
		Program otherProgram = program("İŞKUR Bahar 2027", user("Başka Koordinatör", UserRole.COORDINATOR, null));
		IskurUnit otherUnit = unitRepository.saveAndFlush(new IskurUnit(otherProgram.getId(), "Birim", 0, null,
				Instant.now()));
		IskurTeam elsewhere = teamRepository.saveAndFlush(new IskurTeam(otherUnit, "Başka Ekip", 0, Instant.now()));
		AppUser stranger = user("Deniz Arslan", UserRole.MENTEE, otherProgram);
		place(otherProgram, stranger, elsewhere, IskurTeamRole.LEADER, null);
		AppUser unplaced = user("Ece Kurt", UserRole.MENTEE, program);

		// A member of the same team, another team's leader, another program's leader, someone not placed.
		for (AppUser other : List.of(ayse, lale, stranger, unplaced)) {
			task(other, taskId).andExpect(status().isNotFound());
			download(other, taskId, outputId).andExpect(status().isNotFound());
			upload(other, taskId, pdf("x.pdf")).andExpect(status().isNotFound());
			remove(other, taskId, outputId).andExpect(status().isNotFound());
		}
		// Without the service credentials or a session, nothing answers.
		mockMvc.perform(get(OUTPUT, taskId, outputId).param("actorId", mert.getId().toString()))
				.andExpect(status().isUnauthorized());
		assertThat(outputRepository.count()).isEqualTo(1);
		verify(objectStorage, never()).require(anyString());
	}

	@Test
	void aSignedInInternIsTheActorWhateverTheRequestNames() throws Exception {
		mockMvc.perform(multipart(OUTPUTS, taskId).file(pdf("rapor.pdf"))
				.param("actorId", mert.getId().toString())
				.with(authentication(signedIn(ayse))))
				.andExpect(status().isNotFound());
		assertThat(outputRepository.count()).isZero();
	}

	@Test
	void aFileIsNotReachedThroughAnotherTask() throws Exception {
		upload(mert, taskId, pdf("rapor.pdf")).andExpect(status().isOk());
		UUID outputId = onlyOutput().getId();
		UUID otherTask = task(program, team, mert, zeynep, "Sponsor listesi").getId();

		download(mert, otherTask, outputId).andExpect(status().isNotFound());
		remove(mert, otherTask, outputId).andExpect(status().isNotFound());
		download(mert, taskId, UUID.randomUUID()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Dosya bulunamadı."));
		assertThat(outputRepository.count()).isEqualTo(1);
	}

	@Test
	void coordinatorsOfTheProgramListAndDownloadEveryFile() throws Exception {
		upload(mert, taskId, pdf("rapor.pdf")).andExpect(status().isOk());
		UUID outputId = onlyOutput().getId();
		AppUser second = user("İkinci Koordinatör", UserRole.COORDINATOR, null);
		AppUser admin = user("Yönetici", UserRole.ADMIN, null);
		AppUser otherCoordinator = user("Başka Koordinatör", UserRole.COORDINATOR, null);
		Program otherProgram = program("İŞKUR Bahar 2027", otherCoordinator);

		for (AppUser reader : List.of(coordinator, admin)) {
			mockMvc.perform(get(COORDINATOR, program.getId()).param("actorId", reader.getId().toString())
					.with(httpBasic("test", "test")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.enabled").value(true))
					.andExpect(jsonPath("$.tasks.length()").value(1))
					.andExpect(jsonPath("$.tasks[0].taskId").value(taskId.toString()))
					.andExpect(jsonPath("$.tasks[0].title").value("Afiş için 2 görsel öneri"))
					.andExpect(jsonPath("$.tasks[0].teamName").value("Medya ve Kreatif Üretim"))
					.andExpect(jsonPath("$.tasks[0].assignee.fullName").value("Mert Demir"))
					.andExpect(jsonPath("$.tasks[0].files[0].fileName").value("rapor.pdf"));
			coordinatorDownload(reader, program, taskId, outputId)
					.andExpect(status().isOk())
					.andExpect(content().bytes(PDF))
					.andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment;")));
		}
		// Not a coordinator of this program: another program's, one who manages none, a leader, the uploader.
		for (AppUser refused : List.of(otherCoordinator, second, zeynep, mert)) {
			mockMvc.perform(get(COORDINATOR, program.getId()).param("actorId", refused.getId().toString())
					.with(httpBasic("test", "test")))
					.andExpect(status().is4xxClientError());
			coordinatorDownload(refused, program, taskId, outputId).andExpect(status().is4xxClientError());
		}
		// A coordinator reaches a file only through its own program.
		coordinatorDownload(otherCoordinator, otherProgram, taskId, outputId).andExpect(status().isNotFound());
		coordinatorDownload(coordinator, program, taskId, UUID.randomUUID()).andExpect(status().isNotFound());
		mockMvc.perform(get(COORDINATOR, otherProgram.getId())
				.param("actorId", otherCoordinator.getId().toString()).with(httpBasic("test", "test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tasks").isEmpty());
	}

	@Test
	void theStoredTypeComesFromTheBytesNotFromTheBrowser() throws Exception {
		upload(mert, taskId, new MockMultipartFile("file", "afis.png", "text/html", PNG))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outputFiles[0].contentType").value("image/png"));

		upload(mert, taskId, new MockMultipartFile("file", "rapor.png", "image/png", PDF))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Dosyanın içeriği uzantısıyla uyuşmuyor ya da dosya bozuk."));
		upload(mert, taskId, new MockMultipartFile("file", "betik.js", "application/pdf", PDF))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("PDF, PNG, JPG, DOCX, XLSX, PPTX ya da ZIP dosyası yükleyebilirsin."));
		assertThat(outputRepository.count()).isEqualTo(1);
	}

	@Test
	void refusesAFileOverTenMegabytesAndAnEmptyOne() throws Exception {
		byte[] tooLarge = new byte[10 * 1024 * 1024 + 1];
		System.arraycopy(PDF, 0, tooLarge, 0, PDF.length);

		upload(mert, taskId, new MockMultipartFile("file", "buyuk.pdf", "application/pdf", tooLarge))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Dosya en fazla 10 MB olabilir."));
		upload(mert, taskId, new MockMultipartFile("file", "bos.pdf", "application/pdf", new byte[0]))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Yüklenecek bir dosya seç."));
		mockMvc.perform(multipart(OUTPUTS, taskId).param("actorId", mert.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isBadRequest());
		assertThat(outputRepository.count()).isZero();
		verify(objectStorage, never()).put(anyString(), any(), anyString());
	}

	@Test
	void aTaskTakesAtMostFiveFiles() throws Exception {
		for (int index = 1; index <= 5; index++) {
			upload(mert, taskId, pdf("rapor-" + index + ".pdf"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.outputFiles.length()").value(index))
					.andExpect(jsonPath("$.canAttach").value(index < 5));
		}
		upload(mert, taskId, pdf("rapor-6.pdf"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Bir göreve en fazla 5 dosya eklenebilir."));

		// Taking one back makes room again.
		remove(mert, taskId, outputRepository.findAll().getFirst().getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outputFiles.length()").value(4))
				.andExpect(jsonPath("$.canAttach").value(true));
		upload(mert, taskId, pdf("rapor-6.pdf")).andExpect(status().isOk());
	}

	@Test
	void aFileCountsAsTheOutputAndADoneTaskIsLocked() throws Exception {
		IskurTask task = taskRepository.findById(taskId).orElseThrow();
		task.setNeedsOutput(true);
		taskRepository.saveAndFlush(task);
		complete(mert, taskId).andExpect(status().isBadRequest());

		upload(mert, taskId, pdf("rapor.pdf")).andExpect(status().isOk());
		UUID outputId = onlyOutput().getId();
		complete(mert, taskId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DONE"))
				.andExpect(jsonPath("$.canAttach").value(false))
				.andExpect(jsonPath("$.outputFiles[0].canRemove").value(false));

		// Done: nothing is added or taken back, but the file is still read.
		upload(mert, taskId, pdf("ek.pdf")).andExpect(status().isConflict());
		remove(mert, taskId, outputId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message")
						.value("Tamamlanan görev değiştirilemez; değiştirmek için önce yeniden aç."));
		download(zeynep, taskId, outputId).andExpect(status().isOk());
		assertThat(outputRepository.count()).isEqualTo(1);

		update(mert, taskId, "\"status\":\"OPEN\"").andExpect(status().isOk());
		UUID documentId = onlyOutput().getDocumentId();
		remove(mert, taskId, outputId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outputFiles").isEmpty());
		assertThat(outputRepository.count()).isZero();
		assertThat(storedDocumentRepository.findById(documentId)).isEmpty();
	}

	@Test
	void onlyTheUploaderTakesAFileBack() throws Exception {
		StoredDocument file = storedFile(zeynep);
		IskurTaskOutput output = outputRepository.saveAndFlush(new IskurTaskOutput(taskId, file.getId(),
				zeynep.getId(), Instant.now()));

		remove(mert, taskId, output.getId())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Dosyayı yalnızca yükleyen kişi kaldırabilir."));
		task(mert, taskId).andExpect(jsonPath("$.outputFiles[0].canRemove").value(false));
		assertThat(outputRepository.findById(output.getId())).isPresent();
	}

	@Test
	void answersServiceUnavailableWhileStorageIsOff() throws Exception {
		IskurTaskOutput output = outputRepository.saveAndFlush(new IskurTaskOutput(taskId, storedFile(mert).getId(),
				mert.getId(), Instant.now()));
		when(objectStorage.isConfigured()).thenReturn(false);

		upload(mert, taskId, pdf("rapor.pdf"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.message").value("Dosya yükleme şu an kapalı."));
		download(zeynep, taskId, output.getId()).andExpect(status().isServiceUnavailable());
		// Taking a file back would leave its stored copy behind.
		remove(mert, taskId, output.getId()).andExpect(status().isServiceUnavailable());
		coordinatorDownload(coordinator, program, taskId, output.getId())
				.andExpect(status().isServiceUnavailable());
		// The screens read this to hide the upload control; what is already there stays listed.
		task(mert, taskId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outputFilesEnabled").value(false))
				.andExpect(jsonPath("$.canAttach").value(false))
				.andExpect(jsonPath("$.outputFiles.length()").value(1));
		mockMvc.perform(get(COORDINATOR, program.getId()).param("actorId", coordinator.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(false));
		assertThat(outputRepository.count()).isEqualTo(1);
	}

	@Test
	void anArchivedProgramTakesNoFiles() throws Exception {
		program.setStatus(ProgramStatus.COMPLETED);
		programRepository.saveAndFlush(program);

		upload(mert, taskId, pdf("rapor.pdf")).andExpect(status().isBadRequest());
		assertThat(outputRepository.count()).isZero();
	}

	@Test
	void theTeamListCarriesEachTasksFilesAndALeaderSeesOnlyWorkInThem() throws Exception {
		upload(mert, taskId, pdf("rapor.pdf")).andExpect(status().isOk());
		String week = taskRepository.findById(taskId).orElseThrow().getDueDate()
				.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();

		// The leader first, then her sub-group by name: Ayşe, Mert.
		mockMvc.perform(get("/api/iskur/team/tasks").param("actorId", zeynep.getId().toString())
				.param("weekStart", week).with(httpBasic("test", "test")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members[2].fullName").value("Mert Demir"))
				.andExpect(jsonPath("$.members[2].tasks[0].outputFiles[0].fileName").value("rapor.pdf"))
				// A file row is a name, a type, a size, a time and a flag: nothing that could carry a mentee or a note.
				.andExpect(jsonPath("$.members[2].tasks[0].outputFiles[0]").value(Matchers.aMapWithSize(6)))
				.andExpect(jsonPath("$.members[2].tasks[0].outputFiles[0].canRemove").value(false));
	}

	@Test
	void deletingATaskRemovesItsFiles() throws Exception {
		upload(mert, taskId, pdf("rapor.pdf")).andExpect(status().isOk());
		UUID documentId = onlyOutput().getDocumentId();

		mockMvc.perform(delete(TASK, taskId).param("actorId", zeynep.getId().toString())
				.with(httpBasic("test", "test")))
				.andExpect(status().isOk());

		assertThat(taskRepository.findById(taskId)).isEmpty();
		assertThat(outputRepository.count()).isZero();
		assertThat(storedDocumentRepository.findById(documentId)).isEmpty();
	}

	@Test
	void deletingAnEmptyTeamRemovesTheFilesOfItsTasks() throws Exception {
		IskurTeam empty = teamRepository.saveAndFlush(new IskurTeam(unit, "Etkinlik Ekibi", 2, Instant.now()));
		IskurTask left = task(program, empty, mert, zeynep, "Eski görev");
		StoredDocument file = storedFile(mert);
		outputRepository.saveAndFlush(new IskurTaskOutput(left.getId(), file.getId(), mert.getId(), Instant.now()));

		mockMvc.perform(delete("/api/coordinator/programs/{programId}/iskur/teams/{teamId}", program.getId(),
				empty.getId()).param("actorId", coordinator.getId().toString()).with(httpBasic("test", "test")))
				.andExpect(status().isOk());

		assertThat(outputRepository.count()).isZero();
		assertThat(storedDocumentRepository.findById(file.getId())).isEmpty();
		assertThat(teamRepository.findById(empty.getId())).isEmpty();
	}

	@Test
	void keepsTaskOutputsOffTheGeneralUploadAndFileList() throws Exception {
		mockMvc.perform(multipart("/api/documents").file(pdf("rapor.pdf")).param("kind", "iskur-task-output")
				.with(authentication(signedIn(mert))))
				.andExpect(status().isBadRequest());
		upload(mert, taskId, pdf("rapor.pdf")).andExpect(status().isOk());
		mockMvc.perform(get("/api/documents").with(authentication(signedIn(mert))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	private IskurTaskOutput onlyOutput() {
		List<IskurTaskOutput> all = outputRepository.findAll();
		assertThat(all).hasSize(1);
		return all.getFirst();
	}

	private static MockMultipartFile pdf(String name) {
		return new MockMultipartFile("file", name, "application/pdf", PDF);
	}

	private ResultActions upload(AppUser actor, UUID task, MockMultipartFile file) throws Exception {
		return mockMvc.perform(multipart(OUTPUTS, task).file(file)
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions download(AppUser actor, UUID task, UUID outputId) throws Exception {
		return mockMvc.perform(get(OUTPUT, task, outputId).param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions coordinatorDownload(AppUser actor, Program of, UUID task, UUID outputId)
			throws Exception {
		return mockMvc.perform(get(COORDINATOR + "/{taskId}/{outputId}", of.getId(), task, outputId)
				.param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions remove(AppUser actor, UUID task, UUID outputId) throws Exception {
		return mockMvc.perform(delete(OUTPUT, task, outputId).param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions task(AppUser actor, UUID task) throws Exception {
		return mockMvc.perform(get(TASK, task).param("actorId", actor.getId().toString())
				.with(httpBasic("test", "test")));
	}

	private ResultActions complete(AppUser actor, UUID task) throws Exception {
		return update(actor, task, "\"status\":\"DONE\",\"actualMinutes\":90");
	}

	private ResultActions update(AppUser actor, UUID task, String fields) throws Exception {
		return mockMvc.perform(patch(TASK, task).with(httpBasic("test", "test"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actorId\":\"" + actor.getId() + "\"," + fields + "}"));
	}

	private StoredDocument storedFile(AppUser owner) {
		StoredDocument file = new StoredDocument();
		file.setId(UUID.randomUUID());
		file.setUserId(owner.getId());
		file.setKind(DocumentKind.ISKUR_TASK_OUTPUT);
		file.setOriginalFilename("rapor.pdf");
		file.setContentType("application/pdf");
		file.setSizeBytes(PDF.length);
		file.setS3Key("documents/" + owner.getId() + "/iskur-task-output/" + file.getId() + "-rapor.pdf");
		file.setCreatedAt(Instant.now());
		return storedDocumentRepository.saveAndFlush(file);
	}

	private IskurTask task(Program of, IskurTeam in, AppUser assignee, AppUser by, String title) {
		IskurTask task = new IskurTask(of.getId(), in.getId(), assignee.getId(), by.getId(), Instant.now());
		task.setTitle(title);
		task.setKind(IskurTaskKind.EVENT);
		task.setEstimatedMinutes(120);
		task.setDueDate(LocalDate.now(MeetingSlotTimes.APP_ZONE));
		task.setNeedsOutput(false);
		return taskRepository.saveAndFlush(task);
	}

	private void place(Program of, AppUser who, IskurTeam into, IskurTeamRole role, AppUser leader) {
		IskurPlacement placement = new IskurPlacement(of.getId(), who.getId(), null, Instant.now());
		placement.setTeamId(into.getId());
		placement.setTeamRole(role);
		placement.setLeaderId(leader == null ? null : leader.getId());
		placement.setWeeklyDays((short) 3);
		placementRepository.saveAndFlush(placement);
	}

	private AppUser user(String name, UserRole role, Program joined) {
		AppUser user = new AppUser();
		user.setFullName(name);
		user.setEmail(UUID.randomUUID() + "@example.test");
		user.setRole(role);
		if (joined != null) {
			user.setProgramId(joined.getId());
		}
		AppUser saved = userRepository.saveAndFlush(user);
		if (joined != null) {
			// On the program's İŞKUR intern list, as the coordinator would put them.
			internEmailRepository.saveAndFlush(new IskurInternEmail(joined.getId(),
					saved.getEmail().toLowerCase(Locale.ROOT), null, Instant.now()));
		}
		return saved;
	}

	private Program program(String name, AppUser creator) {
		Program created = new Program();
		created.setId(UUID.randomUUID());
		created.setName(name);
		created.setCreatedBy(creator.getId());
		created.setCreatedAt(Instant.now());
		created.setStatus(ProgramStatus.ACTIVE);
		created.setProgramType(ProgramType.PEER_MENTORING);
		return programRepository.saveAndFlush(created);
	}

	private static UsernamePasswordAuthenticationToken signedIn(AppUser user) {
		return new UsernamePasswordAuthenticationToken(new AppUserPrincipal(
				user.getId(), user.getEmail(), user.getRole(), user.getStatus(), "iskur-test-uid"), null, List.of());
	}
}
