package com.dps.teknokent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One stretch of an intern's work hours: an ISO day (1 = Monday) and minutes since midnight, Turkish time. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IskurScheduleRange {

	@Column(name = "day_of_week", nullable = false)
	private short dayOfWeek;

	@Column(name = "start_minute", nullable = false)
	private short startMinute;

	@Column(name = "end_minute", nullable = false)
	private short endMinute;

	public int minutes() {
		return endMinute - startMinute;
	}
}
