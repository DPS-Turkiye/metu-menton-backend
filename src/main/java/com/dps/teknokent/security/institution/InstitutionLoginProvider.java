package com.dps.teknokent.security.institution;

import java.util.Map;
import java.util.Set;

import com.dps.teknokent.domain.UserRole;

/**
 * One institution's own sign-in (SAML, CAS, OIDC or plain OAuth; ODTÜ's protocol is not known yet). Everything after it
 * is
 * shared: {@code InstitutionLoginService} links the account and hands the browser a Firebase custom token.
 */
public interface InstitutionLoginProvider {

	/** The institution's key: its name in institution_person and the prefix of the Firebase uids it creates. */
	String id();

	/** Where to send the browser to sign in; a path starting with "/" is on the Menton frontend. */
	String startLogin(String state);

	/**
	 * Checks what the institution sent back and says who signed in; a 400 when it does not hold up. {@code state} is
	 * the one {@link #startLogin} was given, already checked and spent by the caller.
	 */
	InstitutionIdentity completeLogin(String state, Map<String, String> callback);

	/**
	 * Whether the login's record is itself the approval (ODTÜ: a student is a MENTEE). A login that only proves who
	 * someone is (OTG) answers false: it never creates an account or grants anything, and a newcomer goes through
	 * Menton's own sign-up and coordinator approval like everyone else.
	 */
	default boolean grantsAccess() {
		return true;
	}

	/**
	 * The same question for one person, for a login whose answer depends on who signed in (CAS: yes for a student
	 * or an academic, no for anyone else).
	 */
	default boolean grantsAccess(InstitutionIdentity identity) {
		return grantsAccess();
	}

	/**
	 * Whether a login carries the person's whole record. One that names only part of it (CAS, with no class year)
	 * answers false, and what it leaves out stays as the row has it: what the student typed at sign-up, or what an
	 * import filed.
	 */
	default boolean sendsWholeRecord() {
		return true;
	}

	/**
	 * Whether a student this login creates an account for joins the one running peer-mentoring program, as a student
	 * who signs up by e-mail does. ODTÜ's CAS says yes; the dev stub's personas are placed by hand.
	 */
	default boolean newStudentsJoinTheProgram() {
		return false;
	}

	/**
	 * Permissions beyond what the record gives. Only the dev stub has any: it stands in for badges until they exist.
	 */
	default Set<UserRole> extraGrants(String persistentId) {
		return Set.of();
	}
}
