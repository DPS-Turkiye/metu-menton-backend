package com.dps.teknokent.domain;

/** Where an address on the İŞKUR intern list stands, as the coordinator's list shows it. */
public enum IskurInternAccountState {
	/** No account uses this address yet; its owner becomes an intern on signing up to the program. */
	NOT_SIGNED_UP,
	/** The account is an approved student or accepted peer mentor of the program: an intern. */
	INTERN,
	/** An account uses this address, but it is not (or not yet) an approved member of this program. */
	OUTSIDE_PROGRAM
}
