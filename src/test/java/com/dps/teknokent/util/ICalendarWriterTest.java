package com.dps.teknokent.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ICalendarWriterTest {

	private static final Instant STAMP = Instant.parse("2026-11-12T15:00:00Z");

	private static ICalendarWriter.Event event(String summary, String description) {
		return event(summary, description, false);
	}

	private static ICalendarWriter.Event event(String summary, String description, boolean cancelled) {
		return new ICalendarWriter.Event("meeting-1@office-hours.menton", Instant.parse("2026-11-17T11:00:00Z"),
				Instant.parse("2026-11-17T11:45:00Z"), summary, description, cancelled);
	}

	/** RFC 5545 §3.1: a folded line goes on after CRLF and one space. */
	private static String unfold(String calendar) {
		return calendar.replace("\r\n ", "");
	}

	@Test
	void writesOneEventInUtcWithCrlfLineEnds() {
		String calendar = ICalendarWriter.write("Ofis saatlerim", STAMP,
				List.of(event("Ofis saati: Ayşe Yılmaz", "Ders: CENG 140")));

		assertThat(calendar).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n").endsWith("END:VCALENDAR\r\n");
		assertThat(calendar.replace("\r\n", "")).doesNotContain("\n", "\r");
		assertThat(calendar).contains(
				"X-WR-CALNAME:Ofis saatlerim\r\n",
				"BEGIN:VEVENT\r\nUID:meeting-1@office-hours.menton\r\nDTSTAMP:20261112T150000Z\r\n",
				"DTSTART:20261117T110000Z\r\nDTEND:20261117T114500Z\r\n",
				"SUMMARY:Ofis saati: Ayşe Yılmaz\r\n",
				"DESCRIPTION:Ders: CENG 140\r\n");
	}

	@Test
	void leavesOutAMissingDescription() {
		assertThat(ICalendarWriter.write("x", STAMP, List.of(event("Ofis saati", null)))).doesNotContain("DESCRIPTION");
	}

	@Test
	void escapesTextSoATopicCannotAddItsOwnLines() {
		String calendar = ICalendarWriter.write("x", STAMP,
				List.of(event("a;b,c\\d", "Konu: proje\r\nEND:VEVENT\nBEGIN:VEVENT")));

		assertThat(calendar).contains("SUMMARY:a\\;b\\,c\\\\d\r\n",
				"DESCRIPTION:Konu: proje\\nEND:VEVENT\\nBEGIN:VEVENT\r\n");
		assertThat(calendar.split("\r\n")).filteredOn("BEGIN:VEVENT"::equals).hasSize(1);
	}

	@Test
	void foldsLongLinesAt75OctetsWithoutSplittingATurkishLetter() {
		String summary = "Ofis saati: " + "Çağrı Gümüşdağ Öztürk İşçi ".repeat(6);

		String calendar = ICalendarWriter.write("x", STAMP, List.of(event(summary, null)));

		assertThat(Arrays.stream(calendar.split("\r\n")))
				.allSatisfy(line -> assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75));
		assertThat(unfold(calendar)).contains("SUMMARY:" + summary + "\r\n");
		assertThat(calendar).doesNotContain("�");
	}

	@Test
	void aLiveEventIsConfirmedAtSequenceZeroAndBlocksTheTime() {
		assertThat(ICalendarWriter.write("x", STAMP, List.of(event("Ofis saati", null))))
				.contains("SEQUENCE:0\r\nSTATUS:CONFIRMED\r\nTRANSP:OPAQUE\r\n");
	}

	@Test
	void aCancelledEventKeepsItsUidAndIsCancelledAtSequenceOne() {
		String calendar = ICalendarWriter.write("x", STAMP, List.of(event("Ofis saati", null, true)));

		assertThat(calendar).contains("UID:meeting-1@office-hours.menton\r\n",
				"SEQUENCE:1\r\nSTATUS:CANCELLED\r\nTRANSP:TRANSPARENT\r\n");
	}

	@Test
	void dropsControlCharactersButKeepsTheEscapedLineBreak() {
		String typed = "a" + (char) 0x00 + "b" + (char) 0x07 + "c" + (char) 0x1F + "d" + (char) 0x7F + "e\nf";

		assertThat(ICalendarWriter.text(typed)).isEqualTo("abcde\\nf");
	}
}
