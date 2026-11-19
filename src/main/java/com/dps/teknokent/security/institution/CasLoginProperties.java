package com.dps.teknokent.security.institution;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ODTÜ's CAS, as the environment names it ({@code CAS_*}). ODTÜ has not said yet which attributes its CAS sends, so
 * every attribute name and every value that marks a kind of person is a setting; {@link CasLoginProvider} checks
 * them all when it starts. A comma separates several values.
 */
@ConfigurationProperties(prefix = "app.cas-login")
public record CasLoginProperties(
		@Nullable String baseUrl,
		@Nullable String serviceUrl,
		@Nullable String version,
		@Nullable String emailDomains,
		@Nullable String idAttribute,
		@Nullable String emailAttribute,
		@Nullable String nameAttribute,
		@Nullable String kindAttribute,
		@Nullable String studentValues,
		@Nullable String academicValues,
		@Nullable String staffValues,
		@Nullable String departmentAttribute,
		@Nullable String facultyAttribute) {
}
