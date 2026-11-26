package com.dps.teknokent.dto.responses;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * What launching a semester draft will do, for the launch dialog: the running semester it completes ({@code null}
 * when none runs), how many of its accounts move into the draft, and how many of its Akran mentors keep mentoring.
 */
public record LaunchPreviewResponse(@Nullable ProgramRef previous, int students, int peerMentors) {

	public record ProgramRef(UUID id, String name) {
	}
}
