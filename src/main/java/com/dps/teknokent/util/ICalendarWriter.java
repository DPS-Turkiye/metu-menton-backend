package com.dps.teknokent.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Writes an iCalendar (RFC 5545) feed for calendars that subscribe to a link, such as Moodle's. Times are written in
 * UTC, so no time zone block is needed and every calendar shows them in its own zone.
 */
public final class ICalendarWriter {

	/**
	 * One event. A cancelled one stays in the feed under the same UID with a higher SEQUENCE, so a calendar that
	 * already holds it marks it cancelled instead of keeping a stale entry until it next drops missing events.
	 */
	public record Event(String uid, Instant startsAt, Instant endsAt, String summary, @Nullable String description,
			boolean cancelled) {
	}

	private static final String CRLF = "\r\n";
	/** C0 controls other than tab, LF and CR, plus DEL: line breaks are escaped instead. */
	private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
	/** RFC 5545 §3.1: a line is at most 75 octets; longer ones go on with CRLF and a space. */
	private static final int MAX_LINE_OCTETS = 75;
	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
			.withZone(ZoneOffset.UTC);

	private ICalendarWriter() {
	}

	public static String write(String calendarName, Instant stamp, List<Event> events) {
		StringBuilder out = new StringBuilder();
		line(out, "BEGIN:VCALENDAR");
		line(out, "VERSION:2.0");
		line(out, "PRODID:-//Menton//Office Hours//TR");
		line(out, "CALSCALE:GREGORIAN");
		line(out, "METHOD:PUBLISH");
		line(out, "X-WR-CALNAME:" + text(calendarName));
		line(out, "X-WR-TIMEZONE:" + MeetingSlotTimes.APP_ZONE.getId());
		// A hint to calendars that honour it; Moodle refreshes on the schedule the subscriber picks.
		line(out, "REFRESH-INTERVAL;VALUE=DURATION:PT1H");
		line(out, "X-PUBLISHED-TTL:PT1H");
		for (Event event : events) {
			line(out, "BEGIN:VEVENT");
			line(out, "UID:" + text(event.uid()));
			line(out, "DTSTAMP:" + UTC.format(stamp));
			line(out, "DTSTART:" + UTC.format(event.startsAt()));
			line(out, "DTEND:" + UTC.format(event.endsAt()));
			line(out, "SUMMARY:" + text(event.summary()));
			if (event.description() != null) {
				line(out, "DESCRIPTION:" + text(event.description()));
			}
			line(out, "SEQUENCE:" + (event.cancelled() ? 1 : 0));
			line(out, "STATUS:" + (event.cancelled() ? "CANCELLED" : "CONFIRMED"));
			// A cancelled meeting no longer blocks the time in the subscriber's calendar.
			line(out, "TRANSP:" + (event.cancelled() ? "TRANSPARENT" : "OPAQUE"));
			line(out, "END:VEVENT");
		}
		line(out, "END:VCALENDAR");
		return out.toString();
	}

	/**
	 * RFC 5545 §3.3.11: backslash, semicolon, comma and line breaks are escaped in text values. Other control
	 * characters (§3.1 allows none) are dropped, so a typed topic can't carry one into a strict parser.
	 */
	static String text(String value) {
		return CONTROL.matcher(value).replaceAll("").replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
				.replace("\r\n", "\\n")
				.replace("\n", "\\n").replace("\r", "\\n");
	}

	/** Folds at 75 octets of UTF-8, never inside a character, so Turkish letters survive the fold. */
	private static void line(StringBuilder out, String content) {
		int octets = 0;
		for (int i = 0; i < content.length();) {
			int codePoint = content.codePointAt(i);
			int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
			if (octets + size > MAX_LINE_OCTETS) {
				// The leading space counts toward the next line's 75.
				out.append(CRLF).append(' ');
				octets = 1;
			}
			out.appendCodePoint(codePoint);
			octets += size;
			i += Character.charCount(codePoint);
		}
		out.append(CRLF);
	}
}
