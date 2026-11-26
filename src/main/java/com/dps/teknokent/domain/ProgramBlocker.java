package com.dps.teknokent.domain;

/** How much open work of one kind there is; counts only, so a refusal never names the people involved. */
public record ProgramBlocker(ProgramBlockerKind kind, ProgramFeature feature, long count) {
}
