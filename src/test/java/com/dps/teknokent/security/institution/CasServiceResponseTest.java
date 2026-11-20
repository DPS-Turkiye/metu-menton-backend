package com.dps.teknokent.security.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CasServiceResponseTest {

	private static final String OPEN = "<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\">";
	private static final String CLOSE = "</cas:serviceResponse>";

	@Test
	void aSuccessNamesTheUserAndEveryAttributeValue() {
		CasServiceResponse answer = CasServiceResponse.parse(OPEN + """
				<cas:authenticationSuccess>
				  <cas:user> e123456 </cas:user>
				  <cas:attributes>
				    <cas:mail>e123456@metu.edu.tr</cas:mail>
				    <cas:eduPersonAffiliation>student</cas:eduPersonAffiliation>
				    <cas:eduPersonAffiliation>faculty</cas:eduPersonAffiliation>
				    <cas:empty>  </cas:empty>
				  </cas:attributes>
				</cas:authenticationSuccess>""" + CLOSE);

		assertThat(answer).isInstanceOfSatisfying(CasServiceResponse.Success.class, success -> {
			assertThat(success.user()).isEqualTo("e123456");
			assertThat(success.attributes()).containsOnlyKeys("mail", "edupersonaffiliation");
			assertThat(success.attributes().get("edupersonaffiliation")).containsExactly("student", "faculty");
		});
	}

	// CAS 2.0 sends the user name and nothing else.
	@Test
	void aSuccessWithoutAttributesHasNone() {
		CasServiceResponse answer = CasServiceResponse.parse(
				OPEN + "<cas:authenticationSuccess><cas:user>e1</cas:user></cas:authenticationSuccess>" + CLOSE);

		assertThat(answer).isEqualTo(new CasServiceResponse.Success("e1", Map.of()));
	}

	@Test
	void aFailureCarriesCassCode() {
		CasServiceResponse answer = CasServiceResponse.parse(OPEN
				+ "<cas:authenticationFailure code=\"INVALID_TICKET\">Ticket ST-1 not recognized</cas:authenticationFailure>"
				+ CLOSE);

		assertThat(answer).isEqualTo(new CasServiceResponse.Failure("INVALID_TICKET"));
	}

	// The classic XXE: a document type that reads a local file into the user name. The document type alone is refused.
	@Test
	void aDocumentTypeIsRefusedSoNoEntityIsEverRead() {
		String xxe = """
				<?xml version="1.0"?>
				<!DOCTYPE cas:serviceResponse [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
				""" + OPEN + "<cas:authenticationSuccess><cas:user>&xxe;</cas:user></cas:authenticationSuccess>"
				+ CLOSE;

		assertThatThrownBy(() -> CasServiceResponse.parse(xxe)).isInstanceOf(IllegalArgumentException.class);
	}

	// "Billion laughs": entities that expand into each other need a document type too.
	@Test
	void anEntityExpansionBombIsRefused() {
		String bomb = """
				<!DOCTYPE lolz [<!ENTITY lol "lol"><!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">]>
				""" + OPEN + "<cas:authenticationSuccess><cas:user>&lol2;</cas:user></cas:authenticationSuccess>"
				+ CLOSE;

		assertThatThrownBy(() -> CasServiceResponse.parse(bomb)).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"not xml at all",
			"<html><body>Login</body></html>",
			// The right names in no namespace, or in someone else's.
			"<serviceResponse><authenticationSuccess><user>e1</user></authenticationSuccess></serviceResponse>",
			"<cas:serviceResponse xmlns:cas=\"http://evil.example/cas\"><cas:authenticationSuccess><cas:user>e1"
					+ "</cas:user></cas:authenticationSuccess></cas:serviceResponse>",
			OPEN + CLOSE,
			OPEN + "<cas:somethingElse/>" + CLOSE,
			// Two answers, two users, or a user that is blank: none of them says who signed in.
			OPEN + "<cas:authenticationFailure code=\"X\"/><cas:authenticationSuccess><cas:user>e1</cas:user>"
					+ "</cas:authenticationSuccess>" + CLOSE,
			OPEN + "<cas:authenticationSuccess><cas:user>e1</cas:user><cas:user>e2</cas:user>"
					+ "</cas:authenticationSuccess>" + CLOSE,
			OPEN + "<cas:authenticationSuccess><cas:user>  </cas:user></cas:authenticationSuccess>" + CLOSE,
			OPEN + "<cas:authenticationSuccess/>" + CLOSE })
	void anythingThatIsNotOneCasAnswerIsRefused(String xml) {
		assertThatThrownBy(() -> CasServiceResponse.parse(xml)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aRefusalNeverQuotesWhatWasSent() {
		assertThatThrownBy(() -> CasServiceResponse.parse("<secret>ST-123</secret>"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageNotContaining("ST-123");
	}

	@Test
	void attributeNamesAreReadWithoutTheirCase() {
		CasServiceResponse answer = CasServiceResponse.parse(OPEN + "<cas:authenticationSuccess><cas:user>e1</cas:user>"
				+ "<cas:attributes><cas:Mail>a@metu.edu.tr</cas:Mail></cas:attributes></cas:authenticationSuccess>"
				+ CLOSE);

		assertThat(((CasServiceResponse.Success) answer).attributes()).containsEntry("mail", List.of("a@metu.edu.tr"));
	}
}
