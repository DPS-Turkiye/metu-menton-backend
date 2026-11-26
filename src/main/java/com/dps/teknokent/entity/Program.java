package com.dps.teknokent.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.dps.teknokent.domain.ProgramFeature;
import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "program")
@Getter
@Setter
@NoArgsConstructor
public class Program {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ProgramStatus status = ProgramStatus.ACTIVE;

	@Enumerated(EnumType.STRING)
	@Column(name = "program_type", nullable = false, length = 32)
	private ProgramType programType = ProgramType.STARTUP_MENTORING;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(name = "meeting_frequency_days", nullable = false)
	private int meetingFrequencyDays = 14;

	@Column(name = "stale_after_days", nullable = false)
	private int staleAfterDays = 14;

	@Column(name = "meeting_note_required", nullable = false)
	private boolean meetingNoteRequired = true;

	@Column(name = "auto_match_suggestions", nullable = false)
	private boolean autoMatchSuggestions = true;

	@Column(name = "default_mentor_capacity", nullable = false)
	private int defaultMentorCapacity = 4;

	@Column(name = "communication_gap_days", nullable = false)
	private int communicationGapDays = 14;

	@Column(name = "bottleneck_flag_days", nullable = false)
	private int bottleneckFlagDays = 21;

	/** Lowercase, no "@"; subdomains count. Null lets any address join. */
	@Column(name = "eligible_email_domain")
	private String eligibleEmailDomain;
	@Column(name = "roster_only", nullable = false)
	private boolean rosterOnly;
	@Column(name = "individual_mentees", nullable = false)
	private boolean individualMentees;
	@Column(name = "syllabus_document_id")
	private UUID syllabusDocumentId;

	/** The feature switches of a {@link ProgramType#SEMESTER}; the older types ignore them (see {@link #features}). */
	@Column(name = "peer_mentoring_enabled", nullable = false)
	private boolean peerMentoringEnabled;
	@Column(name = "office_hours_enabled", nullable = false)
	private boolean officeHoursEnabled;
	@Column(name = "iskur_enabled", nullable = false)
	private boolean iskurEnabled;

	/**
	 * What this program runs. A semester has its switches; the single-purpose types keep what they always ran, so a
	 * peer mentoring program still has İŞKUR and a startup mentoring program has none of these.
	 */
	public Set<ProgramFeature> features() {
		return switch (programType) {
		case SEMESTER -> {
			Set<ProgramFeature> on = EnumSet.noneOf(ProgramFeature.class);
			if (peerMentoringEnabled) {
				on.add(ProgramFeature.PEER_MENTORING);
			}
			if (officeHoursEnabled) {
				on.add(ProgramFeature.OFFICE_HOURS);
			}
			if (iskurEnabled) {
				on.add(ProgramFeature.ISKUR);
			}
			yield on;
		}
		case PEER_MENTORING -> EnumSet.of(ProgramFeature.PEER_MENTORING, ProgramFeature.ISKUR);
		case OFFICE_HOURS -> EnumSet.of(ProgramFeature.OFFICE_HOURS);
		case STARTUP_MENTORING -> EnumSet.noneOf(ProgramFeature.class);
		};
	}

	public boolean has(ProgramFeature feature) {
		return features().contains(feature);
	}

	/** Sets a semester's switches; the caller has checked the set with {@link ProgramFeature#validSet}. */
	public void setFeatures(Set<ProgramFeature> features) {
		peerMentoringEnabled = features.contains(ProgramFeature.PEER_MENTORING);
		officeHoursEnabled = features.contains(ProgramFeature.OFFICE_HOURS);
		iskurEnabled = features.contains(ProgramFeature.ISKUR);
	}
}
