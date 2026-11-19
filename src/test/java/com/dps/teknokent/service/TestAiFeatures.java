package com.dps.teknokent.service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import com.dps.teknokent.domain.AiFeature;
import com.dps.teknokent.domain.Edition;

/** Teknokent's {@link AiFeatures} for tests, named by what is switched off rather than by five switches. */
public final class TestAiFeatures {

	private TestAiFeatures() {
	}

	public static AiFeatures allOn() {
		return allOnExcept();
	}

	public static AiFeatures allOnExcept(AiFeature... off) {
		Set<AiFeature> disabled = Set.of(off);
		Map<AiFeature, String> switches = new EnumMap<>(AiFeature.class);
		for (AiFeature feature : AiFeature.values()) {
			switches.put(feature, Boolean.toString(!disabled.contains(feature)));
		}
		return new AiFeatures(Edition.TEKNOKENT, switches, "");
	}

	/** Every feature on, but matching ranks without the pgvector shortlist. */
	public static AiFeatures withoutPgvector() {
		return new AiFeatures(Edition.TEKNOKENT, Map.of(), "false");
	}
}
