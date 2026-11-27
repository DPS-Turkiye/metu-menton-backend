package com.dps.teknokent.dto.responses;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.dps.teknokent.domain.IdentityKind;
import com.dps.teknokent.domain.StudyLevel;
import com.dps.teknokent.entity.InstitutionPerson;

/**
 * What the institution says about the signed-in person, shown read-only ("Kurumsal Bilgiler"). {@code advisor} is
 * null when the record names none; its {@code userId} is null until that academician has signed in to Menton.
 * {@code iskurParticipant} is also true for every intern of an İŞKUR program (any approved student there): a student
 * who
 * signed up by e-mail has no ODTÜ record to say so.
 */
public record InstitutionRecordResponse(
		List<IdentityKind> kinds,
		String fullName,
		String email,
		String faculty,
		String departmentName,
		Integer classYear,
		StudyLevel programLevel,
		String academicTitle,
		boolean iskurParticipant,
		@Nullable Advisor advisor,
		Instant syncedAt) {

	public record Advisor(@Nullable UUID userId, String fullName, String academicTitle) {
	}

	public static InstitutionRecordResponse of(InstitutionPerson person, boolean iskurParticipant,
			@Nullable InstitutionPerson advisor) {
		return new InstitutionRecordResponse(List.copyOf(person.kinds()), person.getFullName(), person.getEmail(),
				person.getFaculty(), person.getDepartmentName(), person.getClassYear(), person.getProgramLevel(),
				person.getAcademicTitle(), iskurParticipant,
				advisor == null ? null
						: new Advisor(
								advisor.getUser() == null ? null : advisor.getUser().getId(),
								advisor.getFullName(), advisor.getAcademicTitle()),
				person.getSyncedAt());
	}
}
