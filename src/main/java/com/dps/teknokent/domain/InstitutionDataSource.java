package com.dps.teknokent.domain;

/**
 * How an institution_person row was last written: at that person's sign-in, by an admin import, by the dev stub, or
 * from what a student typed at e-mail sign-up. A SIGNUP row is the person's own word, so the institution's login
 * takes it over and overwrites it.
 */
public enum InstitutionDataSource {
	LOGIN,
	IMPORT,
	DEV_STUB,
	SIGNUP
}
