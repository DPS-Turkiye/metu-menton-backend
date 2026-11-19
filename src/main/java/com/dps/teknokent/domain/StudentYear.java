package com.dps.teknokent.domain;

import org.jspecify.annotations.Nullable;

/**
 * The year a student picks at sign-up, stored as the institution record's class year and degree level. Preparatory
 * school and graduate students have no class year there.
 */
public enum StudentYear {
	PREP(null, StudyLevel.BACHELOR),
	YEAR_1(1, StudyLevel.BACHELOR),
	YEAR_2(2, StudyLevel.BACHELOR),
	YEAR_3(3, StudyLevel.BACHELOR),
	YEAR_4(4, StudyLevel.BACHELOR),
	YEAR_5_PLUS(5, StudyLevel.BACHELOR),
	MASTER(null, StudyLevel.MASTER),
	DOCTORATE(null, StudyLevel.DOCTORATE);

	private final @Nullable Integer classYear;
	private final StudyLevel studyLevel;

	StudentYear(@Nullable Integer classYear, StudyLevel studyLevel) {
		this.classYear = classYear;
		this.studyLevel = studyLevel;
	}

	public @Nullable Integer classYear() {
		return classYear;
	}

	public StudyLevel studyLevel() {
		return studyLevel;
	}
}
