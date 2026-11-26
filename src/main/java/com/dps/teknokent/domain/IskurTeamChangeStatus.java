package com.dps.teknokent.domain;

/**
 * An intern's request to change team during the trial (student handbook p.12, p.18: "Ekip değiştirmek istersen 4.
 * haftada belirt; kararı koordinatör verir"). The coordinator either moves them, which closes it as {@code MOVED}, or
 * declines it with a note; unplacing the intern cancels it.
 */
public enum IskurTeamChangeStatus {
	PENDING,
	MOVED,
	DECLINED,
	CANCELLED
}
