package com.dps.teknokent.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dps.teknokent.entity.PrismTrainingLink;

public interface PrismTrainingLinkRepository extends JpaRepository<PrismTrainingLink, UUID> {
}
