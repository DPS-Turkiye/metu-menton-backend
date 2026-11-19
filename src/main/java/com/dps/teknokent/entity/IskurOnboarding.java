package com.dps.teknokent.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import com.dps.teknokent.domain.IskurSkillLevel;
import com.dps.teknokent.domain.IskurTeamRolePreference;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An intern's onboarding form in one İŞKUR program (Figma 284:2, frames 02–04): a draft until it is submitted, then
 * the answers the coordinator places them from.
 */
@Entity
@Table(name = "iskur_onboarding", uniqueConstraints = @UniqueConstraint(name = "uq_iskur_onboarding_user", columnNames = {
		"program_id", "user_id" }))
@Getter
@Setter
@NoArgsConstructor
public class IskurOnboarding {

	@Id
	private UUID id;

	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "weekly_days")
	private Short weeklyDays;

	@Column(name = "prior_iskur_days")
	private Short priorIskurDays;

	@Column(name = "cv_document_id")
	private UUID cvDocumentId;

	@Column(name = "transcript_document_id")
	private UUID transcriptDocumentId;

	@Column(name = "wants_leadership", nullable = false)
	private boolean wantsLeadership;

	@Column(name = "leadership_motivation", length = 500)
	private String leadershipMotivation;

	@Column(name = "leadership_experience", length = 500)
	private String leadershipExperience;

	@Column(name = "leadership_conflict", length = 500)
	private String leadershipConflict;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@ElementCollection
	@CollectionTable(name = "iskur_onboarding_range", joinColumns = @JoinColumn(name = "onboarding_id"))
	@OrderColumn(name = "position")
	@BatchSize(size = 50)
	private List<IskurScheduleRange> ranges = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "iskur_onboarding_unit", joinColumns = @JoinColumn(name = "onboarding_id"))
	@Column(name = "unit_id", nullable = false)
	@BatchSize(size = 50)
	private Set<UUID> unitIds = new LinkedHashSet<>();

	@ElementCollection
	@CollectionTable(name = "iskur_onboarding_skill", joinColumns = @JoinColumn(name = "onboarding_id"))
	@MapKeyColumn(name = "skill_id")
	@Enumerated(EnumType.STRING)
	@Column(name = "level", nullable = false, length = 16)
	@BatchSize(size = 50)
	private Map<UUID, IskurSkillLevel> skills = new LinkedHashMap<>();

	@ElementCollection
	@CollectionTable(name = "iskur_onboarding_team_role", joinColumns = @JoinColumn(name = "onboarding_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "team_role", nullable = false, length = 24)
	@BatchSize(size = 50)
	private Set<IskurTeamRolePreference> teamRoles = new LinkedHashSet<>();

	public IskurOnboarding(UUID programId, UUID userId, Instant now) {
		this.id = UUID.randomUUID();
		this.programId = programId;
		this.userId = userId;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public boolean isSubmitted() {
		return submittedAt != null;
	}
}
