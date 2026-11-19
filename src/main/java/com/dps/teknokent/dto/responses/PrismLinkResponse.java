package com.dps.teknokent.dto.responses;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * The program's Prism link for the training editor; null before the first save. {@code inAppQuiz}: whether this
 * deployment still runs Menton's own quiz, so the editor shows the quiz form only where it counts.
 */
public record PrismLinkResponse(@Nullable String url, @Nullable Instant updatedAt, boolean inAppQuiz) {
}
