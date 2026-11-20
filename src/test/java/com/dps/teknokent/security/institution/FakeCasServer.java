package com.dps.teknokent.security.institution;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * A CAS server for tests, behind the provider's HTTP client. It keeps CAS's own rules: a ticket is issued for one
 * service address, is good once, and is confirmed only under that exact address.
 */
final class FakeCasServer implements ClientHttpRequestFactory {

	static final String BASE = "https://cas.example.test/cas";
	static final String SERVICE = "https://menton.example.test/login/odtu/callback";

	private final Map<String, SignIn> tickets = new HashMap<>();
	private final List<URI> validations = new ArrayList<>();
	private final List<String> refusals = new ArrayList<>();
	private String cannedAnswer;
	private boolean down;

	RestClient client() {
		return RestClient.builder().requestFactory(this).build();
	}

	/** What the browser does with the provider's redirect: signs in at CAS and gets a ticket for that service. */
	String signIn(String loginRedirect, String user, Map<String, List<String>> attributes) {
		assertThat(loginRedirect).startsWith(BASE + "/login?service=");
		String ticket = "ST-" + (tickets.size() + 1) + "-fake.cas.example";
		tickets.put(ticket, new SignIn(param(URI.create(loginRedirect), "service"), user, attributes));
		return ticket;
	}

	/** From now on every validation gets this body, whatever the ticket. */
	void answerWith(String body) {
		cannedAnswer = body;
	}

	void goDown() {
		down = true;
	}

	/** Every validation call the provider made. */
	List<URI> validations() {
		return validations;
	}

	/** The failure codes handed out, in order. */
	List<String> refusals() {
		return refusals;
	}

	@Override
	public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
		return new MockClientHttpRequest(method, uri) {
			@Override
			protected ClientHttpResponse executeInternal() {
				return respond(uri);
			}
		};
	}

	private ClientHttpResponse respond(URI uri) {
		validations.add(uri);
		if (down) {
			return new MockClientHttpResponse(new byte[0], HttpStatus.SERVICE_UNAVAILABLE);
		}
		boolean withAttributes = uri.getPath().equals("/cas/p3/serviceValidate");
		if (!withAttributes && !uri.getPath().equals("/cas/serviceValidate")) {
			return new MockClientHttpResponse(new byte[0], HttpStatus.NOT_FOUND);
		}
		String body = cannedAnswer != null ? cannedAnswer : validate(uri, withAttributes);
		MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8),
				HttpStatus.OK);
		response.getHeaders().setContentType(MediaType.APPLICATION_XML);
		return response;
	}

	private String validate(URI uri, boolean withAttributes) {
		SignIn signIn = tickets.remove(param(uri, "ticket"));
		if (signIn == null) {
			return failure("INVALID_TICKET");
		}
		if (!signIn.service().equals(param(uri, "service"))) {
			return failure("INVALID_SERVICE");
		}
		StringBuilder xml = new StringBuilder("<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\">")
				.append("<cas:authenticationSuccess><cas:user>").append(escape(signIn.user())).append("</cas:user>");
		if (withAttributes) {
			xml.append("<cas:attributes>");
			signIn.attributes().forEach((name, values) -> values.forEach(value -> xml.append("<cas:").append(name)
					.append('>').append(escape(value)).append("</cas:").append(name).append('>')));
			xml.append("</cas:attributes>");
		}
		return xml.append("</cas:authenticationSuccess></cas:serviceResponse>").toString();
	}

	private String failure(String code) {
		refusals.add(code);
		return "<cas:serviceResponse xmlns:cas=\"http://www.yale.edu/tp/cas\"><cas:authenticationFailure code=\""
				+ code + "\">refused</cas:authenticationFailure></cas:serviceResponse>";
	}

	private static String param(URI uri, String name) {
		String raw = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
		return raw == null ? "" : URLDecoder.decode(raw, StandardCharsets.UTF_8);
	}

	private static String escape(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private record SignIn(String service, String user, Map<String, List<String>> attributes) {
	}
}
