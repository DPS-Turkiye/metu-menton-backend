package com.dps.teknokent.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.dps.teknokent.domain.ProgramStatus;
import com.dps.teknokent.domain.ProgramType;
import com.dps.teknokent.entity.Program;
import com.dps.teknokent.repository.ProgramRepository;

import lombok.RequiredArgsConstructor;

/**
 * The program an ODTÜ student joins without choosing: the one running peer-mentoring program. E-mail sign-up and
 * ODTÜ's own login both ask here, so a student lands in the same place whichever way they came.
 */
@Service
@RequiredArgsConstructor
public class StudentProgramLookup {

	private final ProgramRepository programs;

	/** Every running peer-mentoring program. Exactly one means "that one"; none or several, there is no right guess. */
	public List<Program> running() {
		return programs.findByProgramType(ProgramType.PEER_MENTORING).stream()
				.filter(program -> program.getStatus() == ProgramStatus.ACTIVE)
				.toList();
	}
}
