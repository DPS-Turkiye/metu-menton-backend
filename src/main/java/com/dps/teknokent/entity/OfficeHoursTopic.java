package com.dps.teknokent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One thing an academician offers to talk about; the line under the title is optional. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OfficeHoursTopic {
	@Column(nullable = false, length = 80)
	private String title;

	@Column(length = 300)
	private String body;
}
