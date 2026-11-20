package com.dps.teknokent.security.institution;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The HTTP client the institution logins call out with. */
final class InstitutionHttp {

	private InstitutionHttp() {
	}

	/**
	 * Short timeouts, because a browser is waiting; and no redirects, so an answer only ever comes from the address
	 * that was configured.
	 */
	static RestClient client() {
		HttpClient client = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		return RestClient.builder().requestFactory(requestFactory).build();
	}
}
