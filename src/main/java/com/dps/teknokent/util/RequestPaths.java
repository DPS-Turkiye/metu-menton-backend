package com.dps.teknokent.util;

import java.util.regex.Pattern;

/**
 * Request paths as the backend writes them out, in logs and error bodies. A calendar feed's path holds its token,
 * which is the only key to someone's meetings, so it never reaches a log line.
 */
public final class RequestPaths {

	private static final Pattern CALENDAR_FEED_TOKEN = Pattern.compile("(/calendar-feeds/)[^/?#;]+");
	static final String REDACTED = "[redacted]";

	private RequestPaths() {
	}

	public static String redacted(String path) {
		return CALENDAR_FEED_TOKEN.matcher(path).replaceAll("$1" + REDACTED);
	}
}
