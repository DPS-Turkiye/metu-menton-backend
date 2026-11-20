package com.dps.teknokent.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dps.teknokent.domain.AppLanguage;
import com.dps.teknokent.domain.UserAccountStatus;
import com.dps.teknokent.domain.UserRole;
import com.dps.teknokent.entity.AppUser;
import com.dps.teknokent.repository.AppUserRepository;
import com.dps.teknokent.security.institution.InstitutionLoginUids;
import com.dps.teknokent.util.EmailDomains;
import com.dps.teknokent.util.Texts;

/**
 * Creates a deployment's first staff accounts from {@code APP_BOOTSTRAP_STAFF}, so a new database gets its admin and
 * coordinators without anyone's address in this (public) repository or a manual SQL step. Each entry is
 * {@code ROLE:email:firebaseUid}. The row is linked by uid, because staff rows are never claimed by email at sign-in
 * (FirebaseBearerFilter).
 *
 * <p>Insert only: an entry whose uid or address already has a row leaves that row exactly as it is, so nothing here
 * promotes, demotes or relinks an account. The whole list is checked when the bean is created, before anything is
 * written; one bad entry stops the startup with nothing inserted. The inserts run as an ApplicationRunner, before the
 * application counts as started, in one transaction: a failed insert rolls back the others and stops the startup.
 * The log carries counts, never addresses. A uid is not checked against Firebase, so a mistyped one leaves an account
 * nobody can sign in to (staff rows are never claimed by email); fix such a row by hand.
 */
@Service
public class StaffBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(StaffBootstrap.class);

	private static final String VARIABLE = "APP_BOOTSTRAP_STAFF";
	private static final Set<UserRole> STAFF_ROLES = Set.of(UserRole.ADMIN, UserRole.COORDINATOR);
	private static final Pattern FIREBASE_UID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

	private final AppUserRepository users;
	private final List<StaffEntry> entries;

	public StaffBootstrap(AppUserRepository users, @Value("${app.bootstrap.staff:}") String staff) {
		this.users = users;
		this.entries = parse(staff);
	}

	@Override
	@Transactional
	public void run(ApplicationArguments arguments) {
		if (entries.isEmpty()) {
			return;
		}
		int created = 0;
		for (StaffEntry entry : entries) {
			// exists, not find: two old rows that differ only in case would make a find throw.
			if (users.findByFirebaseUid(entry.firebaseUid()).isPresent()
					|| users.existsByEmailIgnoreCase(entry.email())) {
				continue;
			}
			users.save(entry.newAccount());
			created++;
		}
		log.info("Staff bootstrap: {} of {} accounts created; the others already had a row and were left as they are",
				created, entries.size());
	}

	/**
	 * Entries are separated by commas; an empty one (a trailing comma) is skipped. The messages give the entry's
	 * position and what is wrong with it, never its value, since the value holds a person's address.
	 */
	static List<StaffEntry> parse(String staff) {
		List<StaffEntry> parsed = new ArrayList<>();
		Set<String> emails = new HashSet<>();
		Set<String> uids = new HashSet<>();
		String[] raw = staff.split(",", -1);
		for (int index = 0; index < raw.length; index++) {
			if (raw[index].isBlank()) {
				continue;
			}
			int position = index + 1;
			String[] fields = raw[index].split(":", -1);
			if (fields.length != 3) {
				throw invalid(position, "must be ROLE:email:firebaseUid");
			}
			UserRole role = role(fields[0], position);
			String email = EmailDomains.normalizeEmail(fields[1]);
			if (!EmailDomains.isEmail(email)) {
				throw invalid(position, "does not hold a valid e-mail address");
			}
			String uid = fields[2].strip();
			if (!FIREBASE_UID.matcher(uid).matches()) {
				throw invalid(position, "needs a Firebase uid of 1 to 128 letters, digits, '-' or '_'");
			}
			// Those uids belong to ODTÜ logins, which reach their row only through InstitutionLoginService.
			if (InstitutionLoginUids.boundByLoginOnly(uid)) {
				throw invalid(position, "has an ODTÜ-login uid; use the person's Firebase e-mail login");
			}
			if (!emails.add(email) || !uids.add(uid)) {
				throw invalid(position, "repeats the e-mail address or uid of an earlier entry");
			}
			parsed.add(new StaffEntry(role, email, uid));
		}
		return List.copyOf(parsed);
	}

	@SuppressWarnings("UnusedException") // the role name is the whole story; the enum's message adds nothing
	private static UserRole role(String value, int position) {
		UserRole role;
		try {
			role = UserRole.valueOf(value.strip().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException unknown) {
			throw invalid(position, "must start with ADMIN or COORDINATOR");
		}
		if (!STAFF_ROLES.contains(role)) {
			throw invalid(position, "must start with ADMIN or COORDINATOR");
		}
		return role;
	}

	private static IllegalStateException invalid(int position, String problem) {
		return new IllegalStateException(VARIABLE + " entry " + position + " " + problem + "; nothing was created.");
	}

	record StaffEntry(UserRole role, String email, String firebaseUid) {

		AppUser newAccount() {
			AppUser user = new AppUser();
			user.setEmail(email);
			user.setFullName(Texts.get(AppLanguage.TR, role == UserRole.ADMIN
					? "account.bootstrap.adminName"
					: "account.bootstrap.coordinatorName"));
			user.setRole(role);
			user.setStatus(UserAccountStatus.APPROVED);
			user.setFirebaseUid(firebaseUid);
			return user;
		}
	}
}
