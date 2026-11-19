package com.dps.teknokent.dto.requests;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dps.teknokent.domain.IskurWorkEntryKind;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The whole draft of a weekly log; it replaces the lines saved before. */
public record IskurWorkEntriesRequest(
		@NotNull UUID actorId,
		@NotNull @Size(max = 60) List<@Valid @NotNull Entry> entries) {

	/**
	 * One line. A team line has its own title, or takes its task's; a support line takes its activity's title, and
	 * carries no free text, since its minutes count on the mentoring side the leader sees only as numbers.
	 */
	public record Entry(
			@NotNull LocalDate date,
			@NotNull IskurWorkEntryKind kind,
			UUID taskId,
			UUID supportActivityId,
			@Size(max = 200) String title,
			@NotNull @Min(1) @Max(450) Integer minutes,
			@Size(max = 500) String output,
			@Size(max = 500) String note) {
	}
}
