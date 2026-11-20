package com.dps.teknokent.repository;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.ProgramStatus;

public interface MentorProgramName {

	UUID getMentorId();

	UUID getProgramId();

	String getProgramName();

	ProgramStatus getProgramStatus();

	/** The mentor's role in the program; none for a membership the Akran Mentoru badge made. */
	@Nullable
	String getRole();
}
