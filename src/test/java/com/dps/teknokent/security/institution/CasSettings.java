package com.dps.teknokent.security.institution;

/** CAS settings for tests: the defaults of application.yml, with one value changed at a time. */
final class CasSettings {

	private String baseUrl = FakeCasServer.BASE;
	private String serviceUrl = FakeCasServer.SERVICE;
	private String version = "3.0";
	private String emailDomains = "metu.edu.tr";
	private String idAttribute = "";
	private String emailAttribute = "mail";
	private String nameAttribute = "cn";
	private String kindAttribute = "eduPersonAffiliation";
	private String departmentAttribute = "";
	private String facultyAttribute = "";

	static CasSettings defaults() {
		return new CasSettings();
	}

	CasSettings baseUrl(String value) {
		baseUrl = value;
		return this;
	}

	CasSettings serviceUrl(String value) {
		serviceUrl = value;
		return this;
	}

	CasSettings version(String value) {
		version = value;
		return this;
	}

	CasSettings emailDomains(String value) {
		emailDomains = value;
		return this;
	}

	CasSettings idAttribute(String value) {
		idAttribute = value;
		return this;
	}

	CasSettings emailAttribute(String value) {
		emailAttribute = value;
		return this;
	}

	CasSettings nameAttribute(String value) {
		nameAttribute = value;
		return this;
	}

	CasSettings kindAttribute(String value) {
		kindAttribute = value;
		return this;
	}

	CasSettings departmentAttribute(String value) {
		departmentAttribute = value;
		return this;
	}

	CasSettings facultyAttribute(String value) {
		facultyAttribute = value;
		return this;
	}

	CasLoginProperties build() {
		return new CasLoginProperties(baseUrl, serviceUrl, version, emailDomains, idAttribute, emailAttribute,
				nameAttribute, kindAttribute, "student", "faculty", "staff,employee", departmentAttribute,
				facultyAttribute);
	}
}
