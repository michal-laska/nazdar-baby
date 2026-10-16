package com.lafi.cardgame.nazdarbaby.mcts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.lafi.cardgame.nazdarbaby.card.TestCards.getCard;
import static org.assertj.core.api.Assertions.within;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.mcts.HintCalibration.CalibrationBot;
import com.lafi.cardgame.nazdarbaby.mcts.HintCalibration.HandTable;
import com.lafi.cardgame.nazdarbaby.mcts.HintCalibration.Prediction;
import com.lafi.cardgame.nazdarbaby.mcts.MctsEngine.LogOddsCalibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HintCalibrationTest {

	@Nested
	class TablesTest {

		@Test
		void everyHandSizeTheGameDeals() {
			assertThat(HintCalibration.tables())
					.hasSize(10 + 10 + 10 + 8 + 7)
					.doesNotHaveDuplicates()
					.contains(new HandTable(3, 1), new HandTable(3, 10), new HandTable(6, 8), new HandTable(7, 7))
					.doesNotContain(new HandTable(3, 11), new HandTable(6, 9), new HandTable(7, 8));
		}
	}

	@Nested
	class FitTest {

		private static final LogOddsCalibration PER_CARD = new LogOddsCalibration(-0.1, 1.0, 0.06, -0.05);

		@Test
		void recoversTheCalibrationThatProducedTheOutcomes() {
			LogOddsCalibration fit =
					HintCalibration.fit(predictionsFrom(new LogOddsCalibration(0.3, 0.7), new Random(1), 8, 8), false);

			assertThat(fit.shift()).isCloseTo(0.3, within(0.05));
			assertThat(fit.scale()).isCloseTo(0.7, within(0.05));
		}

		@Test
		void calibratedChances_needNoCorrection() {
			LogOddsCalibration fit =
					HintCalibration.fit(predictionsFrom(new LogOddsCalibration(0, 1), new Random(2), 8, 8), false);

			assertThat(fit.shift()).isCloseTo(0, within(0.05));
			assertThat(fit.scale()).isCloseTo(1, within(0.05));
		}

		@Test
		void perCard_recoversTheHandSizeTerms() {
			LogOddsCalibration fit = HintCalibration.fit(predictionsFrom(PER_CARD, new Random(3), 1, 7), true);

			assertThat(fit.shift()).isCloseTo(-0.1, within(0.1));
			assertThat(fit.scale()).isCloseTo(1.0, within(0.1));
			assertThat(fit.shiftPerCard()).isCloseTo(0.06, within(0.02));
			assertThat(fit.scalePerCard()).isCloseTo(-0.05, within(0.02));
		}

		@Test
		void notPerCard_leavesTheHandSizeOut() {
			LogOddsCalibration fit = HintCalibration.fit(predictionsFrom(PER_CARD, new Random(3), 1, 7), false);

			assertThat(fit.shiftPerCard()).isZero();
			assertThat(fit.scalePerCard()).isZero();
		}

		@Test
		void perCard_withOneHandSize_fitsLikeWithout() {
			List<Prediction> predictions = predictionsFrom(new LogOddsCalibration(0.3, 0.7), new Random(1), 8, 8);

			assertThat(HintCalibration.fit(predictions, true)).isEqualTo(HintCalibration.fit(predictions, false));
		}

		@Test
		void perCard_withAnotherHandSizeOnlyAtCertainChances_fitsLikeWithout() {
			List<Prediction> predictions = new ArrayList<>(
					predictionsFrom(new LogOddsCalibration(0.3, 0.7), new Random(1), 8, 8));
			predictions.add(new Prediction(5, 3, 0, false, 1, true));
			predictions.add(new Prediction(5, 3, 1, false, 0, false));

			assertThat(HintCalibration.fit(predictions, true)).isEqualTo(HintCalibration.fit(predictions, false));
		}

		@Test
		void certainAndImpossibleChances_areLeftOut() {
			List<Prediction> predictions = predictionsFrom(PER_CARD, new Random(1), 1, 7);
			List<Prediction> withExtremes = new ArrayList<>(predictions);
			for (int i = 0; i < 1000; i++) {
				withExtremes.add(new Prediction(5, 1 + i % 7, i, false, 1, false));
				withExtremes.add(new Prediction(5, 1 + i % 7, i, false, 0, true));
			}

			assertThat(HintCalibration.fit(withExtremes, true)).isEqualTo(HintCalibration.fit(predictions, true));
		}

		private static List<Prediction> predictionsFrom(LogOddsCalibration truth, Random random, int minHandSize,
														int maxHandSize) {
			List<Prediction> predictions = new ArrayList<>();
			for (int i = 0; i < 40_000; i++) {
				int handSize = minHandSize + random.nextInt(maxHandSize - minHandSize + 1);
				double chance = 0.05 + 0.9 * random.nextDouble();
				boolean matched = random.nextDouble() < truth.apply(chance, handSize);
				predictions.add(new Prediction(5, handSize, i, false, chance, matched));
			}
			return predictions;
		}
	}

	@Nested
	class ScoreTest {

		@Test
		void matchedAsShown_scoresZero() {
			List<Prediction> predictions =
					List.of(new Prediction(3, 8, 0, false, 0.5, true),
							new Prediction(3, 8, 1, false, 0.5, false));

			assertThat(HintCalibration.score(predictions, Prediction::chance)).isEqualTo(" 0.0 /  0.0");
		}

		@Test
		void gapsOfOppositeSign_cancelInTheMeanButNotInTheCalibrationError() {
			List<Prediction> predictions =
					List.of(new Prediction(3, 8, 0, false, 0.1, true),
							new Prediction(3, 8, 1, false, 0.9, false));

			assertThat(HintCalibration.score(predictions, Prediction::chance)).isEqualTo(" 0.0 / 90.0");
		}
	}

	@Nested
	class ReportTest {

		@Test
		void showsEveryTableSizeItHasPredictionsFor_andTheRefitToPaste() throws Exception {
			List<Prediction> predictions =
					HintCalibration.play(List.of(new HandTable(3, 1), new HandTable(7, 1)), 2, 2);

			String report = HintCalibration.report(predictions);

			assertThat(predictions).hasSize(2 * 2 * 3 + 2 * 2 * 7);
			assertThat(predictions).allMatch(prediction -> prediction.chance() >= 0 && prediction.chance() <= 1);
			// About 16 seats with two allowed predictions pick the second half the time: none does once in 65,000 runs
			assertThat(predictions).anyMatch(Prediction::secondOption);
			assertThat(report)
					.contains("SMALL_HAND_HINT_CALIBRATION_BY_PLAYERS", "3 players", "7 players",
							"3, new LogOddsCalibration(", "7, new LogOddsCalibration(")
					.doesNotContain("LARGE_HAND_HINT_CALIBRATION_BY_PLAYERS", "4 players", "5 players", "6 players");
		}

		@Test
		void handsWithinAndAboveTheFirstCardLimit_areFittedApart() {
			List<Prediction> predictions = new ArrayList<>(evenDealsMatchedAsShownOddOnesHalfTheTime(1));
			predictions.addAll(matchedAsShown(8));

			String report = HintCalibration.report(predictions);

			assertThat(smallHands(report))
					.containsPattern("3, new LogOddsCalibration\\(-?0\\.000, 0\\.447, 0\\.000, 0\\.000\\),");
			assertThat(largeHands(report)).containsPattern("3, new LogOddsCalibration\\(-?0\\.000, 1\\.000\\),")
					.endsWith("Then update the values MctsEngineTest.HintChanceTest and BotSimulatorTest expect.\n");
		}

		@Test
		void refit_isScoredOnDealsItWasNotFittedOn() {
			assertThat(HintCalibration.report(evenDealsMatchedAsShownOddOnesHalfTheTime(1)))
					.containsPattern("3 players, first  option, {4}20 predictions: {2}uncorrected  0\\.0 / 30\\.0   "
							+ "current .{11}   refit  0\\.0 / 30\\.0");
		}

		@Test
		void firstAndSecondOptions_areScoredApart() {
			List<Prediction> predictions = new ArrayList<>();
			for (int deal = 0; deal < 20; deal++) {
				predictions.add(new Prediction(3, 1, deal, false, 0.8, deal % 10 != 9));
				predictions.add(new Prediction(3, 1, deal, true, 0.2, deal % 4 == 1));
			}

			// On odd deals the first option is matched 8 times in 10 as shown, the second 5 times instead of 2
			assertThat(HintCalibration.report(predictions))
					.contains("3 players, first  option,    10 predictions:  uncorrected  0.0 /  0.0",
							"3 players, second option,    10 predictions:  uncorrected 30.0 / 30.0");
		}

		@Test
		void refitToPaste_isFittedOnAllDeals() {
			// Over all deals a shown 20% is matched 35% of the time: logit(0.35) / logit(0.2) = 0.447
			assertThat(HintCalibration.report(evenDealsMatchedAsShownOddOnesHalfTheTime(1)))
					.containsPattern("3, new LogOddsCalibration\\(-?0\\.000, 0\\.447, 0\\.000, 0\\.000\\),");
		}

		@Test
		void handsWithinTheFirstCardLimit_pasteTermsPerCard_largerOnesDoNot() {
			List<Prediction> predictions = new ArrayList<>(matchedAsShown(1));
			predictions.addAll(matchedFlatterThanShown(2));
			predictions.addAll(matchedAsShown(8));
			predictions.addAll(matchedFlatterThanShown(9));

			String report = HintCalibration.report(predictions);

			// Scale 1 with 1 card and logit(0.6) / logit(0.7) = 0.479 with 2: 1.521 - 0.521 per card. Even and odd
			// deals match alike, so the held-out fit scores no gap; without per-card terms it would.
			assertThat(smallHands(report))
					.containsPattern("3, new LogOddsCalibration\\(-?0\\.000, 1\\.521, -?0\\.000, -0\\.521\\),")
					.contains("refit  0.0 /  0.0");
			assertThat(largeHands(report)).containsPattern("3, new LogOddsCalibration\\(-?0\\.000, 0\\.\\d{3}\\),");
		}

		private static String smallHands(String report) {
			return report.substring(report.indexOf("SMALL_HAND"), report.indexOf("LARGE_HAND"));
		}

		private static String largeHands(String report) {
			return report.substring(report.indexOf("LARGE_HAND"));
		}

		private static List<Prediction> evenDealsMatchedAsShownOddOnesHalfTheTime(int handSize) {
			List<Prediction> predictions = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				predictions.add(new Prediction(3, handSize, 2 * i, false, 0.2, i < 2));
				predictions.add(new Prediction(3, handSize, 2 * i, false, 0.8, i < 8));
				predictions.add(new Prediction(3, handSize, 2 * i + 1, false, 0.2, i < 5));
				predictions.add(new Prediction(3, handSize, 2 * i + 1, false, 0.8, i < 5));
			}
			return predictions;
		}

		private static List<Prediction> matchedFlatterThanShown(int handSize) {
			List<Prediction> predictions = new ArrayList<>();
			for (int i = 0; i < 40; i++) {
				predictions.add(new Prediction(3, handSize, i, false, 0.3, i / 2 < 8));
				predictions.add(new Prediction(3, handSize, i, false, 0.7, i / 2 < 12));
			}
			return predictions;
		}

		private static List<Prediction> matchedAsShown(int handSize) {
			List<Prediction> predictions = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				predictions.add(new Prediction(3, handSize, i, false, 0.2, i < 2));
				predictions.add(new Prediction(3, handSize, i, false, 0.8, i < 8));
			}
			return predictions;
		}
	}

	@Nested
	class CalibrationBotTest {

		private static final List<Card> ACE_OF_HEARTS = List.of(getCard(14, Color.HEARTS));

		@Test
		void noSecondOptionShare_predictsTheLikeliest() {
			CalibrationBot bot = new CalibrationBot(0);

			assertThat(predictFirst(bot)).isEqualTo(1);
			assertThat(bot.shownSecondOption).isFalse();
			assertThat(bot.shownChance).isEqualTo(1);
		}

		@Test
		void fullSecondOptionShare_predictsTheSecondLikeliest() {
			CalibrationBot bot = new CalibrationBot(1);

			assertThat(predictFirst(bot)).isZero();
			assertThat(bot.shownSecondOption).isTrue();
			assertThat(bot.shownChance).isZero();
		}

		@Test
		void onlyOneAllowedPrediction_isPredictedWhateverTheShare() {
			CalibrationBot bot = new CalibrationBot(1);

			// The others predicted 1 trick of 1 between them, so the last seat must not predict 0
			assertThat(bot.predict(ACE_OF_HEARTS, 2, 3, 1, new Integer[]{1, 0, null}, unknownCards(), Map.of()))
					.isEqualTo(1);
			assertThat(bot.shownSecondOption).isFalse();
			assertThat(bot.shownChance).isEqualTo(1);
		}

		@Test
		void finished_remembersWhetherThePredictionWasMatched() {
			CalibrationBot matched = new CalibrationBot(0);
			CalibrationBot missed = new CalibrationBot(0);

			matched.finished(true);
			missed.finished(false);

			assertThat(matched.matched).isTrue();
			assertThat(missed.matched).isFalse();
		}

		private static double predictFirst(CalibrationBot bot) {
			return bot.predict(ACE_OF_HEARTS, 0, 3, 1, new Integer[3], unknownCards(), Map.of());
		}

		private static List<Card> unknownCards() {
			List<Card> unknownCards = new CardProvider(3).getShuffledDeckOfCards();
			unknownCards.removeAll(ACE_OF_HEARTS);
			return unknownCards;
		}
	}

	@Nested
	class ArgumentsTest {

		@Test
		void notANumber_isRejectedWithItsName() {
			assertThatThrownBy(() -> HintCalibration.main(new String[]{"many"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("deals per table must be a number, got many");
		}

		@Test
		void noDeals_isRejected() {
			assertThatThrownBy(() -> HintCalibration.main(new String[]{"0"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("deals per table must be at least 1, got 0");
		}

		@Test
		void thirdArgument_isRejected() {
			assertThatThrownBy(() -> HintCalibration.main(new String[]{"10", "2", "9"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("Expected <deals per table> <threads>, got 10 2 9");
		}

		@Test
		void noThreads_isRejected() {
			assertThatThrownBy(() -> HintCalibration.main(new String[]{"10", "0"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("threads must be at least 1, got 0");
		}
	}
}
