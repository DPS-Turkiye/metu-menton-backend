package com.dps.teknokent.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dps.teknokent.domain.AiFeature;
import com.dps.teknokent.domain.Edition;
import com.dps.teknokent.dto.responses.AiFeaturesResponse;
import com.dps.teknokent.exception.AiFeatureDisabledException;

import lombok.extern.slf4j.Slf4j;

/**
 * Which {@link AiFeature}s this deployment may use, and whether the pgvector shortlist (which only feeds matching)
 * runs. An unset switch follows the edition: on in Teknokent, so a deployment that sets none of them behaves as
 * before. A feature the edition does not allow ({@link Edition#allowsAi}) stays off whatever its switch says, and a
 * switch that turns one on stops the startup, so a copied env file cannot quietly send that edition's data to the AI
 * provider.
 */
@Slf4j
@Component
public class AiFeatures {

	/** The environment variable behind each switch, for the startup messages. */
	private static final Map<AiFeature, String> VARIABLES = new EnumMap<>(Map.of(
			AiFeature.PROFILE_PARSING, "AI_PROFILE_PARSING_ENABLED",
			AiFeature.SYLLABUS, "AI_SYLLABUS_ENABLED",
			AiFeature.MATCHING, "AI_MATCHING_ENABLED",
			AiFeature.MEETING_REPORT, "AI_MEETING_REPORT_ENABLED",
			AiFeature.TASK_HANDOVER, "AI_TASK_HANDOVER_ENABLED"));
	private static final String PGVECTOR_VARIABLE = "MATCHING_PGVECTOR_ENABLED";

	private final Set<AiFeature> enabled = EnumSet.noneOf(AiFeature.class);
	private final boolean pgvector;

	@Autowired
	public AiFeatures(
			EditionService editions,
			@Value("${ai.features.profile-parsing:}") String profileParsing,
			@Value("${ai.features.syllabus:}") String syllabus,
			@Value("${ai.features.matching:}") String matching,
			@Value("${ai.features.meeting-report:}") String meetingReport,
			@Value("${ai.features.task-handover:}") String taskHandover,
			@Value("${matching.pgvector.enabled:}") String pgvector) {
		this(editions.current(), Map.of(
				AiFeature.PROFILE_PARSING, profileParsing,
				AiFeature.SYLLABUS, syllabus,
				AiFeature.MATCHING, matching,
				AiFeature.MEETING_REPORT, meetingReport,
				AiFeature.TASK_HANDOVER, taskHandover), pgvector);
	}

	/** The switches as written, blank meaning unset. A feature missing from {@code switches} is unset. */
	AiFeatures(Edition edition, Map<AiFeature, String> switches, String pgvectorSwitch) {
		for (AiFeature feature : AiFeature.values()) {
			String variable = VARIABLES.getOrDefault(feature, feature.name());
			if (decide(edition, edition.allowsAi(feature), variable, switches.getOrDefault(feature, ""))) {
				enabled.add(feature);
			}
		}
		pgvector = decide(edition, edition.allowsAi(AiFeature.MATCHING), PGVECTOR_VARIABLE, pgvectorSwitch);
		log.info("AI features on in the {} edition: {}; pgvector shortlist: {}", edition,
				enabled.isEmpty() ? "none" : enabled, pgvector ? "on" : "off");
	}

	private static boolean decide(Edition edition, boolean allowed, String variable, String value) {
		Boolean setting = parse(variable, value);
		if (allowed) {
			return setting == null || setting;
		}
		if (Boolean.TRUE.equals(setting)) {
			throw new IllegalStateException(variable + " is on, but the " + edition
					+ " edition never runs this AI feature; leave it unset or set it to false.");
		}
		return false;
	}

	/** Spring's own boolean spellings, so a Teknokent deployment's existing values keep their meaning. */
	private static @Nullable Boolean parse(String variable, String value) {
		if (value.isBlank()) {
			return null;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
		case "true", "on", "yes", "1" -> Boolean.TRUE;
		case "false", "off", "no", "0" -> Boolean.FALSE;
		default -> throw new IllegalStateException(
				variable + " must be true or false, not '" + value.trim() + "'.");
		};
	}

	public boolean isEnabled(AiFeature feature) {
		return enabled.contains(feature);
	}

	/** Whether matching may take the pgvector shortlist before ranking; never in an edition without matching. */
	public boolean pgvectorEnabled() {
		return pgvector;
	}

	/** Refuses the request with {@code AI_FEATURE_DISABLED} when {@code feature} is off. */
	public void require(AiFeature feature) {
		if (!isEnabled(feature)) {
			throw new AiFeatureDisabledException(feature);
		}
	}

	public AiFeaturesResponse snapshot() {
		return new AiFeaturesResponse(
				isEnabled(AiFeature.PROFILE_PARSING),
				isEnabled(AiFeature.SYLLABUS),
				isEnabled(AiFeature.MATCHING),
				isEnabled(AiFeature.MEETING_REPORT),
				isEnabled(AiFeature.TASK_HANDOVER));
	}
}
