package com.dps.teknokent.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurUnitSkill;

public interface IskurUnitSkillRepository extends JpaRepository<IskurUnitSkill, UUID> {

	List<IskurUnitSkill> findByUnitIdInOrderBySortOrderAsc(Collection<UUID> unitIds);

	List<IskurUnitSkill> findByUnitIdOrderBySortOrderAsc(UUID unitId);
}
