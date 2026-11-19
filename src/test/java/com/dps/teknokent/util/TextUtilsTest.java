package com.dps.teknokent.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TextUtilsTest {

	@Test
	void normalizedListDropsBlanksAndDuplicatesKeepingOrder() {
		assertThat(TextUtils.normalizedList(java.util.Arrays.asList("Finans", "   ", "Finans", null, " Pazarlama ")))
				.containsExactly("Finans", "Pazarlama");
		assertThat(TextUtils.normalizedList(null)).isEmpty();
	}

	// Regression: Hibernate's clear() on merge threw on an immutable list.
	@Test
	void normalizedListIsMutableForHibernate() {
		var areas = TextUtils.normalizedList(java.util.List.of("Finans"));
		areas.clear();
		assertThat(areas).isEmpty();
		assertThat(TextUtils.normalizedList(null)).isInstanceOf(java.util.ArrayList.class);
	}

	@Test
	void normalizesOptionalAndComparisonText() {
		assertThat(TextUtils.normalizeOptional("  hello  ")).isEqualTo("hello");
		assertThat(TextUtils.normalizeOptional("   ")).isNull();
		assertThat(TextUtils.normalizeOptional(null)).isNull();
		assertThat(TextUtils.normalizeForComparison(" Next.js ")).isEqualTo("nextjs");
		assertThat(TextUtils.normalizeForComparison("İş Modeli")).isEqualTo("işmodeli");
	}

	@Test
	void convertsNullToEmptyString() {
		assertThat(TextUtils.nullToEmpty(null)).isEmpty();
		assertThat(TextUtils.nullToEmpty("value")).isEqualTo("value");
	}

	@Test
	void initials_takesTheFirstLetterOfTheFirstTwoWords() {
		assertThat(TextUtils.initials("Dr. Kemal Arslan")).isEqualTo("DK");
		assertThat(TextUtils.initials("leyla aksoy")).isEqualTo("LA");
		assertThat(TextUtils.initials("Cher")).isEqualTo("C");
		assertThat(TextUtils.initials("  Ece   Demir  ")).isEqualTo("ED");
	}

	@Test
	void initials_fallsBackWhenThereIsNoUsableName() {
		assertThat(TextUtils.initials(null)).isEqualTo("?");
		assertThat(TextUtils.initials("   ")).isEqualTo("?");
	}

	@Test
	void normalizeInviteCode_trimsAndUppercasesWithoutTurkishI() {
		assertThat(TextUtils.normalizeInviteCode("  abcd1234 ")).isEqualTo("ABCD1234");
		assertThat(TextUtils.normalizeInviteCode(null)).isEmpty();
		// Turkish locale would give "İ" here, which is in no invite code.
		assertThat(TextUtils.normalizeInviteCode("invite")).isEqualTo("INVITE");
	}

	@Test
	void clipLeavesShortTextAloneAndEndsLongTextWithAnEllipsisWithinTheLimit() {
		assertThat(TextUtils.clip("kısa", 10)).isEqualTo("kısa");
		assertThat(TextUtils.clip("abcdefghij", 10)).isEqualTo("abcdefghij");
		assertThat(TextUtils.clip("abcdefghijk", 10)).isEqualTo("abcdefghi…").hasSize(10);
	}

	@Test
	void clipRefusesALimitWithNoRoomForTheEllipsis() {
		assertThatIllegalArgumentException().isThrownBy(() -> TextUtils.clip("abc", 1));
	}

	@Test
	void clipNeverSplitsASurrogatePair() {
		String clipped = TextUtils.clip("abcdefgh😀xyz", 10);
		assertThat(clipped).isEqualTo("abcdefgh…");
		assertThat(clipped.length()).isLessThanOrEqualTo(10);
	}
}
