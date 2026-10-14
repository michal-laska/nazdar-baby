package com.lafi.cardgame.nazdarbaby.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PredictionHintTest {

	@Nested
	class FormatTest {

		@Test
		void showsTheTwoLikeliestPredictions_bestFirst() {
			assertThat(PredictionHint.format(Map.of(0, 0.2, 1, 0.38, 2, 0.45, 3, 0.07))).isEqualTo("2: 45% · 1: 38%");
		}

		@Test
		void equalChances_lowerPredictionFirst() {
			Map<Integer, Double> chances = new HashMap<>();
			chances.put(3, 0.4);
			chances.put(2, 0.4);
			chances.put(1, 0.2);

			assertThat(PredictionHint.format(chances)).isEqualTo("2: 40% · 3: 40%");
		}

		@Test
		void roundsToWholePercent() {
			assertThat(PredictionHint.format(Map.of(0, 0.996, 1, 0.004))).isEqualTo("0: 100% · 1: 0%");
		}

		@Test
		void singleAllowedPrediction_showsJustThatOne() {
			assertThat(PredictionHint.format(Map.of(0, 1.0))).isEqualTo("0: 100%");
		}

		@Test
		void noChances_noText() {
			assertThat(PredictionHint.format(Map.of())).isEmpty();
		}

		@Test
		void twoDigitPredictions() {
			assertThat(PredictionHint.format(Map.of(9, 0.3, 10, 0.55, 8, 0.1))).isEqualTo("10: 55% · 9: 30%");
		}
	}
}
