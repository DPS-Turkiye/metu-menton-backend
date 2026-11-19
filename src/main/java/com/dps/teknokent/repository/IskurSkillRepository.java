package com.dps.teknokent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.IskurSkill;

public interface IskurSkillRepository extends JpaRepository<IskurSkill, UUID> {

	List<IskurSkill> findByProgramId(UUID programId);
}
