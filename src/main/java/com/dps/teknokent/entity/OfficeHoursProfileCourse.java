package com.dps.teknokent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A course an academician teaches this term, as they typed it. Not an {@link OfficeHoursCourse}: those come from
 * Moodle launches, and this list is entered by hand until FR-OH-12 links the two.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OfficeHoursProfileCourse {
	@Column(nullable = false, length = 20)
	private String code;

	@Column(nullable = false, length = 120)
	private String name;
}
