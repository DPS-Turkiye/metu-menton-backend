package com.dps.teknokent.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

	private static final Set<ProgramType> STUDENT_TYPES = EnumSet.of(ProgramType.SEMESTER,
			ProgramType.PEER_MENTORING);

	private final ProgramRepository programs;

	/**
	 * Every running student program: the active semester, or a peer mentoring program from before semesters. Exactly
	 * one means "that one"; none or several, there is no right guess.
	 */
	public List<Program> running() {
		return programs.findByProgramTypeIn(STUDENT_TYPES).stream()
				.filter(program -> program.getStatus() == ProgramStatus.ACTIVE)
				.toList();
	}
}
