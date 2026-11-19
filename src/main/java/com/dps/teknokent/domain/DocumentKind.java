package com.dps.teknokent.domain;

import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DocumentKind {
	CV("cv", 25 * 1024 * 1024, false, false),
	CANVAS("canvas", 25 * 1024 * 1024, false, false),
	PITCH_DECK("pitch-deck", 25 * 1024 * 1024, false, false),
	MVP("mvp", 25 * 1024 * 1024, false, false),
	VALIDATION("validation", 25 * 1024 * 1024, false, false),
	HOMEWORK("homework", 25 * 1024 * 1024, false, false),
	MEETING_FILE("meeting-file", 25 * 1024 * 1024, false, false),
	NOTE_PHOTO("note-photo", 5 * 1024 * 1024, false, true),
	OTHER("other", 25 * 1024 * 1024, false, false),
	LOGO("logo", 5 * 1024 * 1024, false, true),
	AVATAR("avatar", 5 * 1024 * 1024, false, true),
	PROFILE_PDF("profile-pdf", 50 * 1024 * 1024, true, false),
	REQUESTED_DOC("requested-doc", 25 * 1024 * 1024, false, false),
	SYLLABUS("syllabus", 10 * 1024 * 1024, true, false),
	WEEKLY_PROGRAM("weekly-program", 10 * 1024 * 1024, true, false),
	PEER_EVIDENCE("peer-evidence", 10 * 1024 * 1024, true, false),
	/** An İŞKUR intern's CV or transcript, from the onboarding form; only the program's coordinators read it. */
	ISKUR_DOCUMENT("iskur-document", 10 * 1024 * 1024, true, false);

	private final String value;
	private final long maxBytes;
	private final boolean pdfOnly;
	private final boolean imageOnly;

	DocumentKind(String value, long maxBytes, boolean pdfOnly, boolean imageOnly) {
		this.value = value;
		this.maxBytes = maxBytes;
		this.pdfOnly = pdfOnly;
		this.imageOnly = imageOnly;
	}

	/** The kind's name in the stored_document.kind column, the S3 key and the JSON ("cv", "pitch-deck", ...). */
	@JsonValue
	public String value() {
		return value;
	}

	public long maxBytes() {
		return maxBytes;
	}

	public boolean pdfOnly() {
		return pdfOnly;
	}

	public boolean imageOnly() {
		return imageOnly;
	}

	/**
	 * Stored, listed and shown only by the feature that owns it (peer mentor evidence, İŞKUR onboarding files), with
	 * its own rules on who sees it: never uploaded through the general endpoint, never on a person's or a team's list.
	 */
	public boolean featureOwned() {
		return this == PEER_EVIDENCE || this == ISKUR_DOCUMENT;
	}

	public static Optional<DocumentKind> find(String raw) {
		return Arrays.stream(values()).filter(kind -> kind.value.equals(raw)).findFirst();
	}
}
