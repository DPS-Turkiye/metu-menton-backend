package com.dps.teknokent.config;

import org.springframework.core.env.Environment;

/** Spring profiles the code checks for by name. */
public final class AppProfiles {

	/** A developer's own machine: local stand-ins (throwaway keys, template secrets) are allowed. */
	public static final String DEV = "dev";

	private AppProfiles() {
	}

	/**
	 * True only when {@code dev} is the one active profile. A server that lists it next to others ({@code prod,dev})
	 * is still a server, so it gets no local stand-ins.
	 */
	public static boolean onlyDev(Environment environment) {
		String[] active = environment.getActiveProfiles();
		return active.length == 1 && DEV.equals(active[0].trim());
	}
}
