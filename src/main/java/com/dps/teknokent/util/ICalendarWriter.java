package com.dps.teknokent.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes an iCalendar (RFC 5545) feed for calendars that subscribe to a link, such as Moodle's. Times are written in
 * UTC, so no time zone block is needed and every calendar shows them in its own zone.
 */
public final class ICalendarWriter {

	public record Event(String uid, Instant startsAt, Instant endsAt, String summary, String description) {
	}

	private static final String CRLF = "\r\n";
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
			line(out, "TRANSP:OPAQUE");
			line(out, "END:VEVENT");
		}
		line(out, "END:VCALENDAR");
		return out.toString();
	}

	/** RFC 5545 §3.3.11: backslash, semicolon, comma and line breaks are escaped in text values. */
	static String text(String value) {
		return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n")
				.replace("\n", "\\n").replace("\r", "\\n");
	}

	/** Folds at 75 octets of UTF-8, never inside a character, so Turkish letters survive the fold. */
	private static void line(StringBuilder out, String content) {
		int octets = 0;
		for (int i = 0; i < content.length(); ) {
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
