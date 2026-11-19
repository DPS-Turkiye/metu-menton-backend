package com.dps.teknokent.security.institution;

import java.time.Instant;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.entity.InstitutionPerson;

public final class InstitutionRecords {

	private InstitutionRecords() {
	}

	/** Copies what the institution says onto its row; the row's user link and source are the caller's. */
	public static void apply(InstitutionIdentity identity, InstitutionPerson person, Instant now) {
		person.setKinds(identity.kinds());
		person.setFullName(identity.fullName());
		person.setEmail(identity.email());
		person.setFaculty(identity.faculty());
		person.setDepartmentCode(identity.departmentCode());
		person.setDepartmentName(identity.departmentName());
		person.setClassYear(identity.classYear());
		person.setProgramLevel(identity.programLevel());
		person.setAcademicTitle(identity.academicTitle());
		person.setIskurParticipant(identity.iskurParticipant());
		person.setActive(true);
		person.setSyncedAt(now);
	}

	/**
	 * For a login that carries only part of the record ({@link InstitutionLoginProvider#sendsWholeRecord()} false):
	 * what it says is written, what it leaves out stays as the row has it.
	 */
	public static void applyKnown(InstitutionIdentity identity, InstitutionPerson person, Instant now) {
		person.setKinds(identity.kinds());
		person.setEmail(identity.email());
		keep(identity.fullName(), person::setFullName);
		keep(identity.faculty(), person::setFaculty);
		keep(identity.departmentCode(), person::setDepartmentCode);
		keep(identity.departmentName(), person::setDepartmentName);
		keep(identity.classYear(), person::setClassYear);
		keep(identity.programLevel(), person::setProgramLevel);
		keep(identity.academicTitle(), person::setAcademicTitle);
		person.setActive(true);
		person.setSyncedAt(now);
	}

	private static <T> void keep(@Nullable T said, Consumer<T> write) {
		if (said != null) {
			write.accept(said);
		}
	}
}
