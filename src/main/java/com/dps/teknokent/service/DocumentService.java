package com.dps.teknokent.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dps.teknokent.domain.DocumentKind;
import com.dps.teknokent.dto.responses.StoredDocumentResponse;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.entity.Meeting;
import com.dps.teknokent.entity.StoredDocument;
import com.dps.teknokent.exception.InvalidRequestException;
import com.dps.teknokent.exception.ResourceNotFoundException;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.repository.MeetingRepository;
import com.dps.teknokent.repository.StoredDocumentRepository;
import com.dps.teknokent.util.PdfBytes;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

	private static final Set<String> PDF_TYPES = Set.of("application/pdf");
	private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
	private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
	private static final String PPTX_TYPE = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
	private static final Set<String> DOC_TYPES = Set.of("application/pdf", DOCX_TYPE, XLSX_TYPE, PPTX_TYPE);
	// Office Open XML formats accepted beside PDF, with the part every genuine file must contain.
	private static final Map<String, String> OFFICE_MAIN_PARTS = Map.of(
			"docx", "word/document.xml",
			"xlsx", "xl/workbook.xml",
			"pptx", "ppt/presentation.xml");
	private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");
	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
	private static final byte[] JPEG_SIGNATURE = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	private static final String ZIP_TYPE = "application/zip";
	/** What a work-file kind accepts, with the type each is stored and served as. */
	private static final Map<String, String> WORK_FILE_TYPES = Map.of(
			"pdf", "application/pdf",
			"png", "image/png",
			"jpg", "image/jpeg",
			"jpeg", "image/jpeg",
			"docx", DOCX_TYPE,
			"xlsx", XLSX_TYPE,
			"pptx", PPTX_TYPE,
			"zip", ZIP_TYPE);
	private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
	private static final Set<String> DANGEROUS_ARCHIVE_SUFFIXES = Set.of(
			".js", ".jar", ".exe", ".dll", ".bat", ".cmd", ".sh", ".ps1", ".vbs", ".msi");
	// A handed-in ZIP may hold source code (.js, .py, .html): nothing here runs it, and it is only ever downloaded as
	// an attachment. Only what runs on its own on a double click is refused.
	private static final Set<String> SELF_RUNNING_SUFFIXES = Set.of(
			".exe", ".msi", ".bat", ".cmd", ".com", ".scr", ".ps1", ".vbs", ".jar", ".app", ".dmg", ".sh", ".lnk",
			".reg");
	private static final int MAX_DOCX_ENTRIES = 2_000;
	private static final long MAX_DOCX_TOTAL_UNCOMPRESSED_BYTES = 40L * 1024L * 1024L;
	private static final long MAX_DOCX_ENTRY_UNCOMPRESSED_BYTES = 20L * 1024L * 1024L;
	private static final int MAX_DOCX_COMPRESSION_RATIO = 200;
	// A plain ZIP is only ever stored and handed back, never unpacked here; the limit keeps the check itself bounded.
	private static final long MAX_ZIP_UNCOMPRESSED_BYTES = 100L * 1024L * 1024L;
	private static final int ZIP_READ_BUFFER = 8 * 1024;

	private final AppUserRepository appUserRepository;
	private final StoredDocumentRepository storedDocumentRepository;
	private final ObjectStorage objectStorage;
	private final MeetingRepository meetingRepository;

	/**
	 * {@code kindValue} is the kind as the upload form names it ("cv", "pitch-deck", ...); an unknown one is a 400.
	 * With a meetingId the file is attached to that meeting, which must be the uploader's startup's.
	 */
	public StoredDocumentResponse storeForUser(@Nullable UUID userId, String kindValue, MultipartFile file,
			@Nullable UUID meetingId) {
		if (userId == null) {
			throw new InvalidRequestException("A signed-in user is required.");
		}
		DocumentKind kind = DocumentKind.find(kindValue)
				.orElseThrow(
						() -> rejectUpload(userId, null, kindValue, file == null ? null : file.getOriginalFilename(),
								file == null ? null : file.getSize(), "unknown-kind", "Unknown document kind."));
		if (kind.featureOwned()) {
			// Only the feature that owns these stores them, with who may see them.
			throw new InvalidRequestException("This kind of document is uploaded from its own screen.");
		}
		AppUser user = appUserRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
		boolean avatar = kind == DocumentKind.AVATAR;
		// A profile photo belongs to the person, so it is kept off the startup's file list.
		UUID organizationId = avatar || user.getOrganization() == null ? null : user.getOrganization().getId();
		if (meetingId != null) {
			Meeting meeting = meetingRepository.findById(meetingId)
					.orElseThrow(() -> new ResourceNotFoundException("Meeting not found: " + meetingId));
			if (organizationId == null || !organizationId.equals(meeting.getOrganization().getId())) {
				throw new InvalidRequestException("The meeting belongs to another startup.");
			}
		}
		try {
			StoredDocumentResponse stored = store(
					user.getId(),
					organizationId,
					kind,
					file.getOriginalFilename(),
					file.getContentType(),
					file.getBytes(),
					meetingId);
			if (avatar) {
				removeOlderAvatars(user.getId());
			}
			return stored;
		} catch (IOException error) {
			throw rejectUpload(
					userId,
					organizationId,
					kindValue,
					file == null ? null : file.getOriginalFilename(),
					file == null ? null : file.getSize(),
					"io-read-failure",
					"The uploaded file could not be read.");
		}
	}

	// Only the newest photo is ever shown, so the rest are removed. Keeping the newest,
	// rather than this request's own upload, means two uploads racing each other agree
	// on what stays; a failed cleanup is left for the next upload to finish.
	private void removeOlderAvatars(UUID userId) {
		List<StoredDocument> avatars = storedDocumentRepository
				.findByUserIdAndKind(userId, DocumentKind.AVATAR);
		if (avatars.size() < 2) {
			return;
		}
		UUID newestId = avatars.stream()
				.max(Comparator
						.comparing(StoredDocument::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
						.thenComparing(StoredDocument::getId))
				.orElseThrow()
				.getId();
		List<StoredDocument> older = avatars.stream()
				.filter(document -> !document.getId().equals(newestId))
				.toList();
		try {
			storedDocumentRepository.deleteAll(older);
		} catch (RuntimeException error) {
			log.warn("Stored a new profile photo for {} but could not remove the older ones: {}",
					userId, error.getMessage());
			return;
		}
		for (StoredDocument document : older) {
			try {
				objectStorage.delete(document.getS3Key());
			} catch (RuntimeException error) {
				log.warn("Replaced profile photo {} but its stored object {} remains: {}",
						document.getId(), document.getS3Key(), error.getMessage());
			}
		}
	}

	/** The person's own files, newest first; peer mentor evidence is listed by the peer mentoring profile instead. */
	public List<StoredDocumentResponse> listForUser(UUID userId) {
		if (userId == null) {
			throw new InvalidRequestException("A signed-in user is required.");
		}
		return storedDocumentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.filter(document -> !document.getKind().featureOwned())
				.map(StoredDocumentResponse::fromEntity)
				.toList();
	}

	/** Another person's CV reads as missing, so its id reveals nothing. */
	public byte[] readOwnCv(@Nullable UUID userId, UUID documentId) {
		if (userId == null) {
			throw new InvalidRequestException("A signed-in user is required.");
		}
		StoredDocument document = storedDocumentRepository.findById(documentId)
				.filter(found -> userId.equals(found.getUserId()) && found.getKind() == DocumentKind.CV)
				.orElseThrow(() -> new ResourceNotFoundException("CV not found: " + documentId));
		return objectStorage.require(document.getS3Key());
	}

	public StoredDocumentResponse store(
			UUID userId,
			@Nullable UUID organizationId,
			DocumentKind kind,
			@Nullable String originalFilename,
			@Nullable String contentType,
			byte[] bytes) {
		return store(userId, organizationId, kind, originalFilename, contentType, bytes, null);
	}

	private StoredDocumentResponse store(
			UUID userId,
			@Nullable UUID organizationId,
			DocumentKind kind,
			@Nullable String originalFilename,
			@Nullable String contentType,
			byte[] bytes,
			@Nullable UUID meetingId) {
		String kindValue = kind.value();
		Long fileSize = bytes == null ? null : (long) bytes.length;
		if (bytes == null || bytes.length == 0) {
			throw rejectUpload(userId, organizationId, kindValue, originalFilename, fileSize, "empty-file",
					"A file is required.");
		}
		if (bytes.length > kind.maxBytes()) {
			throw rejectUpload(userId, organizationId, kindValue, originalFilename, fileSize, "size-limit",
					"The file is too large.");
		}
		String filename = originalFilename == null ? "" : originalFilename.trim();
		if (filename.isBlank()) {
			throw rejectUpload(userId, organizationId, kindValue, originalFilename, fileSize, "missing-filename",
					"A file name is required.");
		}
		String extension = extensionOf(filename);
		if (kind.workFiles()) {
			String sniffedType = workFileType(userId, organizationId, kindValue, filename, extension, bytes);
			return persist(userId, organizationId, kind, filename, sniffedType, bytes, meetingId);
		}
		if (kind.pdfOnly() && !extension.equals("pdf")) {
			throw rejectUpload(userId, organizationId, kindValue, filename, fileSize, "extension-not-pdf",
					"Please upload a PDF.");
		}
		if (kind.imageOnly() && !IMAGE_EXTENSIONS.contains(extension)) {
			throw rejectUpload(
					userId,
					organizationId,
					kindValue,
					filename,
					fileSize,
					"extension-not-image",
					"Please upload a PNG, JPG, or WebP image.");
		}
		if (!kind.imageOnly() && !extension.equals("pdf") && !OFFICE_MAIN_PARTS.containsKey(extension)) {
			throw rejectUpload(
					userId,
					organizationId,
					kindValue,
					filename,
					fileSize,
					"extension-not-allowed",
					"Please upload a PDF, DOCX or XLSX file.");
		}
		String resolvedType = resolveContentType(contentType, extension);
		if (kind.pdfOnly() && !PDF_TYPES.contains(resolvedType)) {
			throw rejectUpload(userId, organizationId, kindValue, filename, fileSize, "mime-not-pdf",
					"Please upload a PDF.");
		}
		if (kind.imageOnly() && !IMAGE_TYPES.contains(resolvedType)) {
			throw rejectUpload(
					userId,
					organizationId,
					kindValue,
					filename,
					fileSize,
					"mime-not-image",
					"Please upload a PNG, JPG, or WebP image.");
		}
		if (!kind.pdfOnly() && !kind.imageOnly() && !DOC_TYPES.contains(resolvedType)) {
			throw rejectUpload(
					userId,
					organizationId,
					kindValue,
					filename,
					fileSize,
					"mime-not-allowed",
					"Please upload a PDF, DOCX or XLSX file.");
		}
		validateFileContent(userId, organizationId, kindValue, filename, extension, bytes);
		return persist(userId, organizationId, kind, filename, resolvedType, bytes, meetingId);
	}

	private StoredDocumentResponse persist(
			UUID userId,
			@Nullable UUID organizationId,
			DocumentKind kind,
			String filename,
			String resolvedType,
			byte[] bytes,
			@Nullable UUID meetingId) {
		UUID id = UUID.randomUUID();
		String keyPrefix = userId != null
				? "documents/" + userId + "/" + kind.value()
				: "documents/org/" + organizationId + "/" + kind.value();
		String key = keyPrefix + "/" + id + "-" + sanitize(filename);

		objectStorage.put(key, bytes, resolvedType);

		StoredDocument document = new StoredDocument();
		document.setId(id);
		document.setUserId(userId);
		document.setOrganizationId(organizationId);
		document.setKind(kind);
		document.setOriginalFilename(filename);
		document.setContentType(resolvedType);
		document.setSizeBytes(bytes.length);
		document.setS3Key(key);
		document.setCreatedAt(Instant.now());
		document.setMeetingId(meetingId);
		return StoredDocumentResponse.fromEntity(storedDocumentRepository.save(document));
	}

	private static String extensionOf(String filename) {
		int dot = filename.lastIndexOf('.');
		if (dot < 0 || dot == filename.length() - 1) {
			return "";
		}
		return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String resolveContentType(@Nullable String contentType, String extension) {
		if (contentType != null && !contentType.isBlank() && !contentType.equals("application/octet-stream")) {
			return contentType.toLowerCase(Locale.ROOT);
		}
		return switch (extension) {
		case "docx" -> DOCX_TYPE;
		case "xlsx" -> XLSX_TYPE;
		case "pptx" -> PPTX_TYPE;
		case "png" -> "image/png";
		case "jpg", "jpeg" -> "image/jpeg";
		case "webp" -> "image/webp";
		default -> "application/pdf";
		};
	}

	private static String sanitize(String filename) {
		String cleaned = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
		if (cleaned.length() > 80) {
			return cleaned.substring(cleaned.length() - 80);
		}
		return cleaned;
	}

	private void validateFileContent(
			UUID userId,
			@Nullable UUID organizationId,
			String kindValue,
			String filename,
			String extension,
			byte[] bytes) {
		if ("pdf".equals(extension)) {
			if (!PdfBytes.looksComplete(bytes)) {
				throw rejectUpload(
						userId,
						organizationId,
						kindValue,
						filename,
						(long) bytes.length,
						"invalid-pdf-structure",
						"The uploaded PDF is invalid.");
			}
			return;
		}

		if (IMAGE_EXTENSIONS.contains(extension)) {
			if (!hasImageSignature(bytes)) {
				throw rejectUpload(
						userId,
						organizationId,
						kindValue,
						filename,
						(long) bytes.length,
						"invalid-image-structure",
						"The uploaded image is invalid.");
			}
			return;
		}

		String officeMainPart = OFFICE_MAIN_PARTS.get(extension);
		if (officeMainPart != null && !isSafeOfficeFile(bytes, officeMainPart)) {
			throw rejectUpload(
					userId,
					organizationId,
					kindValue,
					filename,
					(long) bytes.length,
					"invalid-or-unsafe-" + extension,
					"The uploaded " + extension.toUpperCase(Locale.ROOT) + " file is invalid.");
		}
	}

	/**
	 * The type a work file is stored as, decided by its bytes: the extension only says which format to check them
	 * against, and what the browser called the file is ignored. A file that is not what its name says is refused.
	 */
	private String workFileType(
			UUID userId,
			@Nullable UUID organizationId,
			String kindValue,
			String filename,
			String extension,
			byte[] bytes) {
		String type = WORK_FILE_TYPES.get(extension);
		if (type == null) {
			throw rejectUpload(userId, organizationId, kindValue, filename, (long) bytes.length,
					"extension-not-allowed", Texts.forRequest("error.workFileType"));
		}
		String officeMainPart = OFFICE_MAIN_PARTS.get(extension);
		boolean genuine = switch (extension) {
		case "pdf" -> PdfBytes.looksComplete(bytes);
		case "png" -> startsWith(bytes, PNG_SIGNATURE);
		case "jpg", "jpeg" -> startsWith(bytes, JPEG_SIGNATURE);
		case "zip" -> isSafeArchive(bytes, null, SELF_RUNNING_SUFFIXES, MAX_ZIP_UNCOMPRESSED_BYTES,
				MAX_ZIP_UNCOMPRESSED_BYTES);
		default -> officeMainPart != null && isSafeOfficeFile(bytes, officeMainPart);
		};
		if (!genuine) {
			throw rejectUpload(userId, organizationId, kindValue, filename, (long) bytes.length,
					"content-does-not-match-" + extension,
					Texts.forRequest("zip".equals(extension) ? "error.workFileZip" : "error.workFileContent"));
		}
		return type;
	}

	private static boolean isSafeOfficeFile(byte[] bytes, String mainPart) {
		return isSafeArchive(bytes, mainPart, DANGEROUS_ARCHIVE_SUFFIXES, MAX_DOCX_ENTRY_UNCOMPRESSED_BYTES,
				MAX_DOCX_TOTAL_UNCOMPRESSED_BYTES);
	}

	/**
	 * A readable ZIP with nothing that escapes it, ends in a {@code blockedSuffixes} name or expands like a bomb. With
	 * a {@code mainPart} it must also be the Office format that part belongs to; without one, any ZIP with a file in
	 * it passes.
	 */
	private static boolean isSafeArchive(byte[] bytes, @Nullable String mainPart, Set<String> blockedSuffixes,
			long maxEntryBytes, long maxTotalBytes) {
		boolean hasContentTypes = false;
		boolean hasMainDocument = false;
		int files = 0;
		int entries = 0;
		long totalUncompressedBytes = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				entries++;
				if (entries > MAX_DOCX_ENTRIES) {
					return false;
				}
				if (entry.isDirectory()) {
					continue;
				}
				files++;
				String name = entry.getName();
				if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
					return false;
				}
				String lower = name.toLowerCase(Locale.ROOT);
				if ("[content_types].xml".equals(lower)) {
					hasContentTypes = true;
				}
				if (lower.equals(mainPart)) {
					hasMainDocument = true;
				}
				if (lower.contains("vbaproject.bin") || hasBlockedSuffix(lower, blockedSuffixes)) {
					return false;
				}

				long entryBytes = 0;
				byte[] buffer = new byte[ZIP_READ_BUFFER];
				int read;
				while ((read = zip.read(buffer)) != -1) {
					entryBytes += read;
					totalUncompressedBytes += read;
					if (entryBytes > maxEntryBytes || totalUncompressedBytes > maxTotalBytes) {
						return false;
					}
				}

				long declaredUncompressed = entry.getSize();
				long declaredCompressed = entry.getCompressedSize();
				if (declaredUncompressed > 0 && declaredCompressed > 0) {
					long ratio = declaredUncompressed / Math.max(1, declaredCompressed);
					if (ratio > MAX_DOCX_COMPRESSION_RATIO) {
						return false;
					}
				}
			}
		} catch (IOException error) {
			return false;
		}
		return mainPart == null ? files > 0 : hasContentTypes && hasMainDocument;
	}

	// A folder on the way counts too: a macOS application is a folder named *.app with its program inside.
	private static boolean hasBlockedSuffix(String entryName, Set<String> blockedSuffixes) {
		String path = entryName.replace('\\', '/');
		for (String suffix : blockedSuffixes) {
			if (path.endsWith(suffix) || path.contains(suffix + "/")) {
				return true;
			}
		}
		return false;
	}

	private boolean hasImageSignature(byte[] bytes) {
		if (bytes == null) {
			return false;
		}
		if (startsWith(bytes, PNG_SIGNATURE) || startsWith(bytes, JPEG_SIGNATURE)) {
			return true;
		}
		return bytes.length >= 12
				&& startsWith(bytes, new byte[] { 'R', 'I', 'F', 'F' })
				&& bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
	}

	private static boolean startsWith(byte[] bytes, byte[] prefix) {
		if (bytes.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (bytes[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	private InvalidRequestException rejectUpload(
			@Nullable UUID userId,
			@Nullable UUID organizationId,
			String kindValue,
			@Nullable String filename,
			@Nullable Long sizeBytes,
			String reason,
			String message) {
		log.warn(
				"Blocked document upload: reason={}, userId={}, organizationId={}, kind={}, filename={}, sizeBytes={}",
				reason,
				userId,
				organizationId,
				kindValue,
				filename,
				sizeBytes);
		return new InvalidRequestException(message);
	}
}
