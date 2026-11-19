package com.dps.teknokent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IskurWeeklySplitTest {

	@Test
	void theDefaultMentoringShareFollowsTheThreeDayModel() {
		assertThat(IskurWeeklySplit.of(3, null)).isEqualTo(new IskurWeeklySplit(1350, 360, 990, true));
		assertThat(IskurWeeklySplit.of(2, null)).isEqualTo(new IskurWeeklySplit(900, 240, 660, true));
		assertThat(IskurWeeklySplit.of(1, null)).isEqualTo(new IskurWeeklySplit(450, 120, 330, true));
	}

	@Test
	void aCoordinatorsShareReplacesTheDefaultDownToNone() {
		assertThat(IskurWeeklySplit.of(3, 0)).isEqualTo(new IskurWeeklySplit(1350, 0, 1350, false));
		assertThat(IskurWeeklySplit.of(1, 450)).isEqualTo(new IskurWeeklySplit(450, 450, 0, false));
	}

	@Test
	void refusesDaysOutsideOneToThreeAndSharesThatDoNotFit() {
		assertThatThrownBy(() -> IskurWeeklySplit.of(0, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> IskurWeeklySplit.of(4, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> IskurWeeklySplit.of(1, 480)).isInstanceOf(IllegalArgumentException.class);
		assertThat(IskurWeeklySplit.fits(2, 45)).isFalse();
		assertThat(IskurWeeklySplit.fits(2, -30)).isFalse();
		assertThat(IskurWeeklySplit.fits(2, 900)).isTrue();
	}
}
