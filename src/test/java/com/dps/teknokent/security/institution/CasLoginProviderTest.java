package com.dps.teknokent.security.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dps.teknokent.domain.IdentityKind;
import com.dps.teknokent.exception.InvalidRequestException;

class CasLoginProviderTest {

	private static final Map<String, List<String>> STUDENT = Map.of(
			"mail", List.of("e123456@metu.edu.tr"),
			"cn", List.of("Deniz Kaya"),
			"eduPersonAffiliation", List.of("student"));

	private final FakeCasServer cas = new FakeCasServer();
	private final CasLoginProvider provider = provider(CasSettings.defaults());

	private CasLoginProvider provider(CasSettings settings) {
		return new CasLoginProvider(cas.client(), settings.build());
	}

	@Test
	void isOdtusCasAndLeavesAloneWhatItDoesNotSend() {
		assertThat(provider.id()).isEqualTo("odtu-cas");
		assertThat(provider.sendsWholeRecord()).isFalse();
		assertThat(provider.newStudentsJoinTheProgram()).isTrue();
		assertThat(provider.extraGrants("e123456")).isEmpty();
	}

	// The whole service address, state included, is one strictly encoded query value.
	@Test
	void startSendsTheBrowserToCasWithTheStateInsideTheServiceAddress() {
		assertThat(provider.startLogin("state-1")).isEqualTo("https://cas.example.test/cas/login?service="
				+ "https%3A%2F%2Fmenton.example.test%2Flogin%2Fodtu%2Fcallback%3Fstate%3Dstate-1");
	}

	@Test
	void aGoodTicketSaysWhoSignedIn() {
		String ticket = cas.signIn(provider.startLogin("state-1"), "E123456", STUDENT);

		InstitutionIdentity identity = provider.completeLogin("state-1", Map.of("ticket", ticket));

		// Lower-cased: CAS may echo the user name as it was typed.
		assertThat(identity.persistentId()).isEqualTo("e123456");
		assertThat(identity.email()).isEqualTo("e123456@metu.edu.tr");
		assertThat(identity.emailVerified()).isTrue();
		assertThat(identity.fullName()).isEqualTo("Deniz Kaya");
		assertThat(identity.kinds()).containsExactly(IdentityKind.STUDENT);
		assertThat(identity.departmentName()).isNull();
		assertThat(identity.classYear()).isNull();
		assertThat(provider.grantsAccess(identity)).isTrue();
		assertThat(cas.validations()).singleElement().satisfies(call -> {
			assertThat(call.getPath()).isEqualTo("/cas/p3/serviceValidate");
			assertThat(call.getRawQuery()).isEqualTo("service=https%3A%2F%2Fmenton.example.test%2Flogin%2Fodtu"
					+ "%2Fcallback%3Fstate%3Dstate-1&ticket=" + ticket);
		});
	}

	@Test
	void aTicketIsGoodOnce() {
		String ticket = cas.signIn(provider.startLogin("state-1"), "e123456", STUDENT);
		provider.completeLogin("state-1", Map.of("ticket", ticket));

		assertThatThrownBy(() -> provider.completeLogin("state-1", Map.of("ticket", ticket)))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(cas.refusals()).containsExactly("INVALID_TICKET");
	}

	@Test
	void aTicketCasNeverIssuedIsRefused() {
		assertThatThrownBy(() -> provider.completeLogin("state-1", Map.of("ticket", "ST-999-made.up")))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(cas.refusals()).containsExactly("INVALID_TICKET");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "PT-1-proxy", "ST-", "ST-1 or 1=1", "ST-1&service=https://evil.example",
			"ST-1\nX-Injected: 1" })
	void somethingThatIsNotAServiceTicketNeverReachesCas(String ticket) {
		assertThatThrownBy(() -> provider.completeLogin("state-1", Map.of("ticket", ticket)))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(cas.validations()).isEmpty();
	}

	@Test
	void aCallbackWithoutATicketIsRefused() {
		assertThatThrownBy(() -> provider.completeLogin("state-1", Map.of()))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(cas.validations()).isEmpty();
	}

	@Test
	void anOverlongTicketIsRefused() {
		assertThatThrownBy(() -> provider.completeLogin("state-1", Map.of("ticket", "ST-" + "a".repeat(254))))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(cas.validations()).isEmpty();
	}

	// Login CSRF: an attacker signs in as themselves, then gets a victim's browser to finish with that ticket. The
	// ticket belongs to the attacker's state, so under the victim's state CAS says INVALID_SERVICE.
	@Test
	void aTicketFromSomeoneElsesLoginFailsTheServiceCheck() {
		String attackersTicket = cas.signIn(provider.startLogin("attacker-state"), "e999999", STUDENT);

		assertThatThrownBy(() -> provider.completeLogin("victim-state", Map.of("ticket", attackersTicket)))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(cas.refusals()).containsExactly("INVALID_SERVICE");
	}

	// The service address is the backend's own; nothing the browser adds to the callback changes it.
	@Test
	void theBrowserCannotNameTheServiceAddress() {
		String ticket = cas.signIn(provider.startLogin("state-1"), "e123456", STUDENT);

		provider.completeLogin("state-1",
				Map.of("ticket", ticket, "service", "https://evil.example/callback", "state", "other"));

		assertThat(cas.validations()).singleElement().satisfies(call -> assertThat(call.getRawQuery())
				.startsWith("service=https%3A%2F%2Fmenton.example.test%2F")
				.doesNotContain("evil"));
	}

	@Test
	void anXxeAnswerIsABadGatewayAndNobodySignsIn() {
		cas.answerWith("""
				<?xml version="1.0"?>
				<!DOCTYPE r [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
				<cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas"><cas:authenticationSuccess>
				<cas:user>&xxe;</cas:user><cas:attributes><cas:mail>&xxe;@metu.edu.tr</cas:mail></cas:attributes>
				</cas:authenticationSuccess></cas:serviceResponse>""");

		assertBadGateway(() -> provider.completeLogin("state-1", Map.of("ticket", "ST-1-x")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "<html>Service unavailable</html>" })
	void anAnswerThatIsNotCasXmlIsABadGateway(String body) {
		cas.answerWith(body);

		assertBadGateway(() -> provider.completeLogin("state-1", Map.of("ticket", "ST-1-x")));
	}

	@Test
	void anOversizedAnswerIsABadGateway() {
		cas.answerWith("<a>" + "x".repeat(70_000) + "</a>");

		assertBadGateway(() -> provider.completeLogin("state-1", Map.of("ticket", "ST-1-x")));
	}

	// Just under the cap is still read; the cap is on bytes, not on what the parser later makes of them.
	@Test
	void anAnswerJustUnderTheCapIsStillRead() {
		String ticket = cas.signIn(provider.startLogin("state-1"), "e123456", Map.of(
				"mail", List.of("e123456@metu.edu.tr"), "cn", List.of("a".repeat(60_000))));

		assertThat(provider.completeLogin("state-1", Map.of("ticket", ticket)).fullName()).hasSize(60_000);
	}

	@Test
	void casBeingDownIsABadGateway() {
		cas.goDown();

		assertBadGateway(() -> provider.completeLogin("state-1", Map.of("ticket", "ST-1-x")));
	}

	@Test
	void aMissingEmailAttributeIsABadGateway() {
		String ticket = cas.signIn(provider.startLogin("state-1"), "e123456",
				Map.of("cn", List.of("Deniz Kaya"), "eduPersonAffiliation", List.of("student")));

		assertBadGateway(() -> provider.completeLogin("state-1", Map.of("ticket", ticket)));
	}

	@Test
	void anEmailAttributeThatHoldsNoAddressIsABadGateway() {
		String ticket = cas.signIn(provider.startLogin("state-1"), "e123456",
				Map.of("mail", List.of("not-an-address")));

		assertBadGateway(() -> provider.completeLogin("state-1", Map.of("ticket", ticket)));
	}

	// CAS 2.0 sends the user name only, so with no address there is nobody to sign in.
	@Test
	void cas2IsValidatedOnItsOwnPathAndWithoutAttributesIsABadGateway() {
		CasLoginProvider cas2 = provider(CasSettings.defaults().version("2.0"));
		String ticket = cas.signIn(cas2.startLogin("state-1"), "e123456", STUDENT);

		assertBadGateway(() -> cas2.completeLogin("state-1", Map.of("ticket", ticket)));
		assertThat(cas.validations()).singleElement()
				.satisfies(call -> assertThat(call.getPath()).isEqualTo("/cas/serviceValidate"));
	}

	@Test
	void aConfiguredIdAttributeReplacesTheUserNameAndItsAbsenceIsABadGateway() {
		CasLoginProvider withId = provider(CasSettings.defaults().idAttribute("metuId"));
		String ticket = cas.signIn(withId.startLogin("state-1"), "deniz", Map.of(
				"mail", List.of("deniz@metu.edu.tr"), "metuId", List.of("1234567")));
		assertThat(withId.completeLogin("state-1", Map.of("ticket", ticket)).persistentId()).isEqualTo("1234567");

		String noId = cas.signIn(withId.startLogin("state-2"), "deniz", Map.of("mail", List.of("deniz@metu.edu.tr")));
		assertBadGateway(() -> withId.completeLogin("state-2", Map.of("ticket", noId)));
	}

	// The address is what binds a login to an existing account, so one ODTÜ did not issue is never taken.
	@Test
	void anAddressOutsideOdtusDomainsIsRefused() {
		String ticket = cas.signIn(provider.startLogin("state-1"), "e123456",
				Map.of("mail", List.of("someone@gmail.com", "x@metu.edu.tr.evil.example")));

		assertThatThrownBy(() -> provider.completeLogin("state-1", Map.of("ticket", ticket)))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
	}

	@Test
	void theOdtuAddressIsPickedAmongSeveralAndADepartmentSubdomainCounts() {
		String ticket = cas.signIn(provider.startLogin("state-1"), "e123456",
				Map.of("mail", List.of("deniz@gmail.com", " Deniz@CENG.metu.edu.tr ")));

		assertThat(provider.completeLogin("state-1", Map.of("ticket", ticket)).email())
				.isEqualTo("deniz@ceng.metu.edu.tr");
	}

	@Test
	void theKindAttributeDecidesWhoIsAStudentAnAcademicOrStaff() {
		assertThat(kindsOf("student")).containsExactly(IdentityKind.STUDENT);
		assertThat(kindsOf("Faculty")).containsExactly(IdentityKind.ACADEMIC);
		assertThat(kindsOf("student", "faculty")).containsExactlyInAnyOrder(IdentityKind.STUDENT,
				IdentityKind.ACADEMIC);
		assertThat(kindsOf("staff")).containsExactly(IdentityKind.STAFF);
		assertThat(kindsOf("employee", "member")).containsExactly(IdentityKind.STAFF);
		assertThat(kindsOf("alum")).isEmpty();
		assertThat(kindsOf()).isEmpty();
	}

	// Only what the record says: administrative staff, or nobody in particular, follow the ordinary sign-up.
	@Test
	void onlyAStudentOrAnAcademicRecordStandsInForApproval() {
		assertThat(provider.grantsAccess(signedInAs("student"))).isTrue();
		assertThat(provider.grantsAccess(signedInAs("faculty"))).isTrue();
		assertThat(provider.grantsAccess(signedInAs("staff"))).isFalse();
		assertThat(provider.grantsAccess(signedInAs())).isFalse();
	}

	@Test
	void withoutAKindAttributeNobodyIsGivenAKind() {
		CasLoginProvider noKinds = provider(CasSettings.defaults().kindAttribute(""));
		String ticket = cas.signIn(noKinds.startLogin("state-1"), "e123456", STUDENT);

		assertThat(noKinds.completeLogin("state-1", Map.of("ticket", ticket)).kinds()).isEmpty();
	}

	@Test
	void departmentFacultyAndNameComeFromTheirConfiguredAttributes() {
		CasLoginProvider full = provider(CasSettings.defaults().departmentAttribute("ou").facultyAttribute("faculty")
				.nameAttribute("displayName").emailAttribute("Email"));
		String ticket = cas.signIn(full.startLogin("state-1"), "e123456", Map.of(
				"email", List.of("e123456@metu.edu.tr"), "displayName", List.of(" Deniz Kaya "),
				"ou", List.of("Bilgisayar Mühendisliği"), "faculty", List.of("Mühendislik Fakültesi")));

		InstitutionIdentity identity = full.completeLogin("state-1", Map.of("ticket", ticket));

		assertThat(identity.fullName()).isEqualTo("Deniz Kaya");
		assertThat(identity.departmentName()).isEqualTo("Bilgisayar Mühendisliği");
		assertThat(identity.faculty()).isEqualTo("Mühendislik Fakültesi");
	}

	@Test
	void aPersonWithoutANameAttributeHasNoName() {
		String ticket = cas.signIn(provider.startLogin("state-1"), "e123456", Map.of("mail", List.of("a@metu.edu.tr")));

		assertThat(provider.completeLogin("state-1", Map.of("ticket", ticket)).fullName()).isNull();
	}

	@Test
	void aTrailingSlashOnTheCasAddressChangesNothing() {
		CasLoginProvider slashed = provider(CasSettings.defaults().baseUrl(FakeCasServer.BASE + "//"));

		assertThat(slashed.startLogin("s")).startsWith("https://cas.example.test/cas/login?service=");
	}

	@Test
	void refusesToStartWithoutItsAddresses() {
		assertRefusesToStart(CasSettings.defaults().baseUrl(""), "CAS_BASE_URL");
		assertRefusesToStart(CasSettings.defaults().baseUrl(null), "CAS_BASE_URL");
		assertRefusesToStart(CasSettings.defaults().serviceUrl("  "), "CAS_SERVICE_URL");
		assertRefusesToStart(CasSettings.defaults().emailAttribute(""), "CAS_ATTR_EMAIL");
		assertRefusesToStart(CasSettings.defaults().emailDomains(" , "), "CAS_EMAIL_DOMAINS");
		assertRefusesToStart(CasSettings.defaults().version(""), "CAS_VERSION");
	}

	@Test
	void refusesToStartOnPlainHttp() {
		assertRefusesToStart(CasSettings.defaults().baseUrl("http://cas.example.test/cas"), "CAS_BASE_URL");
		assertRefusesToStart(CasSettings.defaults().baseUrl("http://localhost:8443/cas"), "CAS_BASE_URL");
		assertRefusesToStart(CasSettings.defaults().serviceUrl("http://menton.example.test/login/odtu/callback"),
				"CAS_SERVICE_URL");
		assertRefusesToStart(CasSettings.defaults().serviceUrl("http://localhost.evil.example/login/odtu/callback"),
				"CAS_SERVICE_URL");
	}

	// The state is appended as the only query parameter, so the address must bring none of its own.
	@Test
	void refusesAServiceAddressWithAQueryAFragmentOrNoHost() {
		assertRefusesToStart(CasSettings.defaults().serviceUrl(FakeCasServer.SERVICE + "?a=b"), "CAS_SERVICE_URL");
		assertRefusesToStart(CasSettings.defaults().serviceUrl(FakeCasServer.SERVICE + "#top"), "CAS_SERVICE_URL");
		assertRefusesToStart(CasSettings.defaults().serviceUrl("https:///login"), "CAS_SERVICE_URL");
		assertRefusesToStart(CasSettings.defaults().serviceUrl("https://menton.example.test/a b"), "CAS_SERVICE_URL");
	}

	@Test
	void refusesAnUnknownVersion() {
		assertRefusesToStart(CasSettings.defaults().version("1.0"), "CAS_VERSION");
	}

	@Test
	void takesPlainHttpForTheServiceAddressOnThisMachineOnly() {
		assertThatCode(() -> provider(CasSettings.defaults().serviceUrl("http://localhost:3000/login/odtu/callback")))
				.doesNotThrowAnyException();
		assertThatCode(() -> provider(CasSettings.defaults().serviceUrl("http://127.0.0.1/login/odtu/callback")))
				.doesNotThrowAnyException();
	}

	@Test
	void theEmailDomainsMayBeWrittenWithAnAtSign() {
		CasLoginProvider withAt = provider(CasSettings.defaults().emailDomains("@metu.edu.tr, @odtu.edu.tr"));
		String ticket = cas.signIn(withAt.startLogin("state-1"), "e123456", Map.of("mail", List.of("a@odtu.edu.tr")));

		assertThat(withAt.completeLogin("state-1", Map.of("ticket", ticket)).email()).isEqualTo("a@odtu.edu.tr");
	}

	private Set<IdentityKind> kindsOf(String... affiliations) {
		return signedInAs(affiliations).kinds();
	}

	private InstitutionIdentity signedInAs(String... affiliations) {
		String state = "state-" + cas.validations().size();
		String ticket = cas.signIn(provider.startLogin(state), "e123456", Map.of(
				"mail", List.of("e123456@metu.edu.tr"), "eduPersonAffiliation", List.of(affiliations)));
		return provider.completeLogin(state, Map.of("ticket", ticket));
	}

	private void assertRefusesToStart(CasSettings settings, String names) {
		assertThatThrownBy(() -> provider(settings)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(names);
	}

	private static void assertBadGateway(ThrowingCallable login) {
		assertThatThrownBy(login).isInstanceOfSatisfying(ResponseStatusException.class,
				error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
	}
}
