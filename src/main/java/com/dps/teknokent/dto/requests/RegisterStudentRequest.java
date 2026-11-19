package com.dps.teknokent.dto.requests;

import com.dps.teknokent.domain.StudentYear;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An ODTÜ student's e-mail sign-up. There is no address field: the address is the verified one in the token. */
public record RegisterStudentRequest(
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank @Size(max = 255) String department,
		@NotNull StudentYear year) {
}
