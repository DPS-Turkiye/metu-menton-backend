package com.dps.teknokent.exception;

import java.util.List;

import com.dps.teknokent.domain.ProgramBlocker;

/** A 409 that lists the open work, so the dialog can say what to finish first. */
public class ProgramWorkOpenException extends ConflictException {

	public static final String CODE = "PROGRAM_WORK_OPEN";

	private final List<ProgramBlocker> blockers;

	public ProgramWorkOpenException(String message, List<ProgramBlocker> blockers) {
		super(message, CODE);
		this.blockers = List.copyOf(blockers);
	}

	public List<ProgramBlocker> blockers() {
		return blockers;
	}
}
