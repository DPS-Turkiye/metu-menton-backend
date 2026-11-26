package com.dps.teknokent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.dps.teknokent.domain.Edition;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.exception.InvalidRequestException;

import lombok.extern.slf4j.Slf4j;

/** The deployment's edition ({@code APP_EDITION}), read once at startup. */
@Slf4j
@Service
public class EditionService {

	private final Edition edition;

	public EditionService(@Value("${app.edition:}") String edition) {
		this.edition = Edition.parse(edition);
		log.info("Running as the {} edition", this.edition);
	}

	public Edition current() {
		return edition;
	}

	/** False for a program of the other edition's type, which nobody may join here. */
	public boolean serves(Program program) {
		return current().allows(program.getProgramType());
	}

	/** A new program has this edition's one type: a semester at ODTÜ, startup mentoring at Teknokent. */
	public void requireProgramType(ProgramType type) {
		if (!current().creates(type)) {
			throw new InvalidRequestException("This program type is not available here.");
		}
	}
}
