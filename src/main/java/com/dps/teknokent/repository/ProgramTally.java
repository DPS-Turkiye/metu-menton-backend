package com.dps.teknokent.repository;

import java.util.UUID;

/** A count per program, from a grouped query. */
public interface ProgramTally {

	UUID getProgramId();

	Long getTotal();
}
