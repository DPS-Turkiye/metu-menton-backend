package com.dps.teknokent.security.institution;

/** The Firebase uids the backend makes for people who sign in through an institution: {@code <provider>_<their id>}. */
public final class InstitutionLoginUids {

	private InstitutionLoginUids() {
	}

	public static String of(InstitutionLoginProvider provider, String persistentId) {
		return provider.id() + "_" + persistentId;
	}

	/**
	 * True for a uid of an OTG or CAS login. Such a login reaches an account only through
	 * {@code InstitutionLoginService}, which checks which person the account is bound to, so nothing else may bind
	 * it by its address or its uid.
	 */
	public static boolean boundByLoginOnly(String uid) {
		return uid.startsWith(OtgLoginProvider.ID + "_") || uid.startsWith(CasLoginProvider.ID + "_");
	}
}
