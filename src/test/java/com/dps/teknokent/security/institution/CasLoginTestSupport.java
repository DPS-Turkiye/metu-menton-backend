package com.dps.teknokent.security.institution;

import java.util.List;
import java.util.Map;

/** Lets a test in another package run the real CAS provider against the fake CAS server. */
public final class CasLoginTestSupport {

	private final FakeCasServer server = new FakeCasServer();
	private final CasLoginProvider provider = new CasLoginProvider(server.client(), CasSettings.defaults().build());

	public InstitutionLoginProvider provider() {
		return provider;
	}

	public Browser server() {
		return server::signIn;
	}

	/** What a browser does at CAS: follows the login redirect, signs in, and comes back with a ticket. */
	@FunctionalInterface
	public interface Browser {
		String signIn(String loginRedirect, String user, Map<String, List<String>> attributes);
	}
}
