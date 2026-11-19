package com.dps.teknokent.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.AppLanguage;
import com.dps.teknokent.entity.IskurSkill;
import com.dps.teknokent.entity.IskurUnitSkill;
import com.dps.teknokent.repository.IskurSkillRepository;
import com.dps.teknokent.repository.IskurUnitSkillRepository;
import com.dps.teknokent.util.Texts;

import lombok.RequiredArgsConstructor;

/**
 * The skills each İŞKUR unit asks its applicants to rate (onboarding 03). Skills live in one catalogue per program,
 * so a skill two units ask about is one skill: the intern rates it once and both units see the answer.
 */
@Service
@RequiredArgsConstructor
public class IskurSkillService {

	/**
	 * The default structure's units that Figma 03 lists skills for: Kreatif ve Dijital Üretim, Etki ve Organizasyon.
	 */
	static final List<Integer> DEFAULT_UNITS_WITH_SKILLS = List.of(1, 4);

	private static final Locale TR = Locale.forLanguageTag("tr");

	private final IskurSkillRepository skills;
	private final IskurUnitSkillRepository unitSkills;

	/** Each unit's skills in the order the form asks them; a unit without skills maps to an empty list. */
	@Transactional(readOnly = true)
	public Map<UUID, List<IskurSkill>> skillsByUnit(UUID programId, Collection<UUID> unitIds) {
		Map<UUID, List<IskurSkill>> byUnit = new LinkedHashMap<>();
		unitIds.forEach(unitId -> byUnit.put(unitId, new ArrayList<>()));
		if (unitIds.isEmpty()) {
			return byUnit;
		}
		Map<UUID, IskurSkill> catalogue = skills.findByProgramId(programId).stream()
				.collect(Collectors.toMap(IskurSkill::getId, Function.identity()));
		for (IskurUnitSkill link : unitSkills.findByUnitIdInOrderBySortOrderAsc(unitIds)) {
			IskurSkill skill = catalogue.get(link.getSkillId());
			List<IskurSkill> list = byUnit.get(link.getUnitId());
			if (skill != null && list != null) {
				list.add(skill);
			}
		}
		return byUnit;
	}

	/** Every skill id of the program's catalogue, asked by a unit or not. */
	@Transactional(readOnly = true)
	public List<UUID> catalogueIds(UUID programId) {
		return skills.findByProgramId(programId).stream().map(IskurSkill::getId).toList();
	}

	/**
	 * Makes {@code names} the unit's skills, in that order. A name the catalogue already has (in any letter case)
	 * reuses
	 * that skill, so answers given to it stay; a skill dropped from every unit stays in the catalogue with its answers.
	 * The caller holds the program's lock.
	 */
	@Transactional
	public void replaceUnitSkills(UUID programId, UUID unitId, List<String> names) {
		Map<String, IskurSkill> byName = new HashMap<>();
		skills.findByProgramId(programId).forEach(skill -> byName.put(key(skill.getName()), skill));
		Instant now = Instant.now();
		List<UUID> wanted = new ArrayList<>();
		for (String raw : names) {
			String name = raw.strip();
			IskurSkill skill = byName.get(key(name));
			if (skill == null) {
				skill = skills.save(new IskurSkill(programId, name, now));
				byName.put(key(name), skill);
			}
			if (!wanted.contains(skill.getId())) {
				wanted.add(skill.getId());
			}
		}
		// Kept rows are re-ordered in place and only the rest are removed or added, so the (unit, skill) key is
		// never inserted while its old row still waits to be deleted.
		Map<UUID, IskurUnitSkill> current = unitSkills.findByUnitIdOrderBySortOrderAsc(unitId).stream()
				.collect(Collectors.toMap(IskurUnitSkill::getSkillId, Function.identity()));
		List<IskurUnitSkill> removed = current.values().stream()
				.filter(link -> !wanted.contains(link.getSkillId()))
				.toList();
		unitSkills.deleteAll(removed);
		unitSkills.flush();
		List<IskurUnitSkill> kept = new ArrayList<>();
		for (int order = 0; order < wanted.size(); order++) {
			UUID skillId = wanted.get(order);
			IskurUnitSkill link = current.get(skillId);
			if (link == null) {
				link = new IskurUnitSkill(unitId, skillId, order);
			} else {
				link.setSortOrder(order);
			}
			kept.add(link);
		}
		unitSkills.saveAll(kept);
	}

	/** The skills Figma 03 gives the default structure's unit number {@code unitNumber}, if it gives any. */
	@Transactional
	public void seedDefaultSkills(UUID programId, UUID unitId, int unitNumber) {
		if (!DEFAULT_UNITS_WITH_SKILLS.contains(unitNumber)) {
			return;
		}
		String names = Texts.get(AppLanguage.TR, "iskur.defaultSkills" + unitNumber);
		replaceUnitSkills(programId, unitId, List.of(names.split("\\|")));
	}

	private static String key(String name) {
		return name.strip().toLowerCase(TR);
	}
}
