package com.lafi.cardgame.nazdarbaby.mcts;

import static com.lafi.cardgame.nazdarbaby.card.TestCards.getCard;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.within;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.point.PointProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MctsEngineTest {

	private List<Card> deckOfCards;
	private MctsEngine engine;

	@BeforeEach
	void setUp() {
		CardProvider cardProvider = new CardProvider(3);
		deckOfCards = cardProvider.getShuffledDeckOfCards();
		engine = new MctsEngine();
	}

	@Nested
	class PredictTakesTest {

		@Test
		void aceOfHearts_singleTrick_predictsOne() {
			// Ace of hearts (trump ace) in a 1-trick game — always wins
			Card aceHearts = getCard(14, Color.HEARTS);
			List<Card> botHand = List.of(aceHearts);

			SimulationState state = createPredictionState(botHand, 0, 0);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			int[] opponentSlots = {0, 1, 1};

			int prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			assertThat(prediction).isEqualTo(1);
		}

		@Test
		void weakHand_predictsZero() {
			Card sevenDiamonds = getCard(7, Color.DIAMONDS);
			List<Card> botHand = List.of(sevenDiamonds);

			SimulationState state = createPredictionState(botHand, 0, 0);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			int[] opponentSlots = {0, 1, 1};

			int prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			assertThat(prediction).isZero();
		}

		@Test
		void lastPredictor_knownOpponentPredictions() {
			Card aceHearts = getCard(14, Color.HEARTS);
			List<Card> botHand = List.of(aceHearts);

			// Bot is player 2 (last predictor); the opponents' 1 and 0 leave 1 allowed
			SimulationState state = createPredictionState(botHand, 2, 2);
			state.setFixedPrediction(0, 1);
			state.setFixedPrediction(1, 0);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			int[] opponentSlots = {1, 1, 0};

			int prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			assertThat(prediction).isEqualTo(1);
		}

		@Test
		void lastPredictor_twoForbidden_threeAcesPredictThree() {
			// Opponents predicted 0 and 1 of 3 tricks, so 2 is forbidden; with the A♥, 3 beats 1 (57% vs 13%)
			Card aceHearts = getCard(14, Color.HEARTS);
			Card aceClubs = getCard(14, Color.CLUBS);
			Card aceSpades = getCard(14, Color.SPADES);
			List<Card> botHand = List.of(aceHearts, aceClubs, aceSpades);

			SimulationState state = createPredictionState(botHand, 2, 2);
			state.setFixedPrediction(0, 0);
			state.setFixedPrediction(1, 1);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			int[] opponentSlots = {3, 3, 0};

			int prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			assertThat(prediction).isEqualTo(3);
		}

		@Test
		void lastPredictor_neverPredictsTheForbiddenTakes() {
			// Opponents claimed nothing, so predicting all 3 tricks is forbidden
			Card aceHearts = getCard(14, Color.HEARTS);
			Card aceClubs = getCard(14, Color.CLUBS);
			Card aceSpades = getCard(14, Color.SPADES);
			List<Card> botHand = List.of(aceHearts, aceClubs, aceSpades);

			SimulationState state = createPredictionState(botHand, 2, 2);
			state.setFixedPrediction(0, 0);
			state.setFixedPrediction(1, 0);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			int[] opponentSlots = {3, 3, 0};

			int prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			assertThat(prediction).isEqualTo(2);
		}

		@Test
		void lastPredictor_onlyHopelessPredictionsLeft_predictsTheLower() {
			// The two top trumps take both tricks, and 2 is forbidden after 0 and 0: 0 and 1 both never match
			List<Card> botHand = List.of(getCard(14, Color.HEARTS), getCard(13, Color.HEARTS));

			SimulationState state = createPredictionState(botHand, 2, 2);
			state.setFixedPrediction(0, 0);
			state.setFixedPrediction(1, 0);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			int[] opponentSlots = {2, 2, 0};

			int prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			assertThat(prediction).isZero();
		}

		@Test
		void firstPredictor_opponentPredictionsFixed() {
			// Even as first predictor (no known predictions), should work —
			// opponent predictions are fixed during determinization
			Card aceHearts = getCard(14, Color.HEARTS);
			Card aceClubs = getCard(14, Color.CLUBS);
			Card aceSpades = getCard(14, Color.SPADES);
			List<Card> botHand = List.of(aceHearts, aceClubs, aceSpades);

			SimulationState state = createPredictionState(botHand, 0, 0);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			int[] opponentSlots = {0, 3, 3};

			int prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			// Three aces including trump ace — should predict at least 2
			assertThat(prediction).isGreaterThanOrEqualTo(2);
		}
	}

	@Nested
	class PredictionChancesTest {

		@Test
		void aceOfHearts_singleTrick_alwaysTakesIt() {
			List<Card> botHand = List.of(getCard(14, Color.HEARTS));

			Map<Integer, Double> chances = firstPredictorChances(botHand);

			assertThat(chances).containsExactly(entry(0, 0.0), entry(1, 1.0));
		}

		@Test
		void twoTopTrumps_alwaysTakeBothTricks() {
			List<Card> botHand = List.of(getCard(14, Color.HEARTS), getCard(13, Color.HEARTS));

			Map<Integer, Double> chances = firstPredictorChances(botHand);

			assertThat(chances).containsExactly(entry(0, 0.0), entry(1, 0.0), entry(2, 1.0));
		}

		@Test
		void loneAceOfDiamonds_leading_matchesTheExactOdds() {
			List<Card> botHand = List.of(getCard(14, Color.DIAMONDS));

			Map<Integer, Double> chances = firstPredictorChances(botHand);

			// Led, the ace loses only if one of the 2 cards dealt from the 31 unknown is a heart
			assertThat(chances.get(1)).isCloseTo(253.0 / 465, within(0.07));
			assertThat(chances.get(0) + chances.get(1)).isCloseTo(1.0, within(1e-9));
		}

		@Test
		void chancesMatchRealPlay_notTheTreeSearchOdds() {
			List<Card> botHand = List.of(getCard(11, Color.CLUBS), getCard(13, Color.DIAMONDS),
					getCard(14, Color.DIAMONDS));

			Map<Integer, Double> chances = firstPredictorChances(botHand);

			// Played out by the engine in every seat, this hand matches 0 in 49% of 1500 deals;
			// a tree search over the same deals rated predicting 0 at about 95%
			assertThat(chances.get(0)).isBetween(0.40, 0.60);
			assertThat(chances.get(0)).isGreaterThan(chances.get(1));
		}

		@Test
		void smallHand_opensWithItsBestFirstCard() {
			List<Card> botHand = List.of(getCard(14, Color.HEARTS), getCard(13, Color.HEARTS), getCard(7, Color.HEARTS));

			Map<Integer, Double> chances = firstPredictorChances(botHand);

			// Led first, the 7♥ loses unless none of the 6 cards dealt from the 29 unknown is one of the 5 other hearts;
			// the policy's own opening takes exactly two tricks in under 1% of deals
			assertThat(chances.get(2)).isCloseTo(340424.0 / 475020, within(0.07));
		}

		@Test
		void smallHand_notLeading_choosesItsFirstCardOnItsTurn() {
			List<Card> botHand = List.of(getCard(14, Color.HEARTS), getCard(9, Color.HEARTS));

			Map<Integer, Double> chances = chancesInSeat(botHand, 1);

			// 0.38 averaged over 100 runs; the policy's own first card gives 0.11
			assertThat(chances.get(1)).isCloseTo(0.38, within(0.07));
		}

		@Test
		void smallHand_firstCardThatMustNotBePlayed_isLeftToThePolicy() {
			List<Card> botHand = List.of(getCard(14, Color.HEARTS), getCard(7, Color.DIAMONDS));

			Map<Integer, Double> chances = chancesInSeat(botHand, 2);

			// Ducking with the 7♦ first always takes exactly one trick, but a led heart forces the ace out:
			// 0.79 averaged over 200 runs; playing the 7♦ anyway gives 1.0, counting it as a miss 0.57
			assertThat(chances.get(1)).isCloseTo(0.79, within(0.07));
		}

		@Test
		void sixCards_withFivePlayers_stillChooseTheirFirstCard() {
			List<Card> botHand = List.of(getCard(3, Color.HEARTS), getCard(13, Color.HEARTS), getCard(14, Color.HEARTS),
					getCard(2, Color.SPADES), getCard(3, Color.DIAMONDS), getCard(4, Color.CLUBS));

			Map<Integer, Double> chances = firstPredictorChances(botHand, 5);

			// 0.996 averaged over 60 runs, never below 0.99; the policy's own first card gives 0.19
			assertThat(chances.get(2)).isGreaterThan(0.9);
		}

		@Test
		void sevenCards_withFourPlayers_chooseTheirFirstCard() {
			List<Card> botHand = List.of(getCard(5, Color.HEARTS), getCard(12, Color.HEARTS), getCard(13, Color.HEARTS),
					getCard(4, Color.SPADES), getCard(2, Color.CLUBS), getCard(4, Color.CLUBS), getCard(7, Color.CLUBS));

			Map<Integer, Double> chances = firstPredictorChances(botHand, 4);

			// 0.83 averaged over 60 runs; the policy's own first card gives 0.29
			assertThat(chances.get(2)).isCloseTo(0.83, within(0.07));
		}

		@Test
		void sevenCards_withFivePlayers_leaveTheirFirstCardToThePolicy() {
			List<Card> botHand = List.of(getCard(11, Color.HEARTS), getCard(2, Color.SPADES), getCard(4, Color.SPADES),
					getCard(6, Color.SPADES), getCard(10, Color.SPADES), getCard(2, Color.DIAMONDS), getCard(5, Color.DIAMONDS));

			Map<Integer, Double> chances = firstPredictorChances(botHand, 5);

			// The best first card would take exactly one trick in about 61% of deals, the policy's in 12%
			assertThat(chances.get(1)).isLessThan(0.25);
		}

		@Test
		void eightCards_leaveTheirFirstCardToThePolicy() {
			List<Card> botHand = List.of(getCard(7, Color.HEARTS), getCard(10, Color.HEARTS), getCard(12, Color.HEARTS),
					getCard(13, Color.HEARTS), getCard(14, Color.HEARTS), getCard(7, Color.DIAMONDS), getCard(10, Color.CLUBS),
					getCard(12, Color.CLUBS));

			Map<Integer, Double> chances = firstPredictorChances(botHand);

			// The best first card would take exactly four tricks in about 67% of deals, the policy's never
			assertThat(chances.get(4)).isLessThan(0.10);
		}

		@Test
		void hint_aboveTheFirstCardLimit_withFivePlayers_isRaised() {
			List<Card> botHand = List.of(getCard(14, Color.SPADES), getCard(13, Color.DIAMONDS), getCard(9, Color.HEARTS),
					getCard(12, Color.CLUBS), getCard(7, Color.SPADES), getCard(5, Color.DIAMONDS), getCard(3, Color.CLUBS),
					getCard(2, Color.HEARTS));

			double hintChance = IntStream.range(0, 6)
					.mapToDouble(_ -> chancesInSeat(botHand, 0, 5, engine::hintChances).get(2))
					.average()
					.orElseThrow();

			// Simulated at 0.45 on average over 20 runs, never above 0.48; the 5-player large-hand calibration
			// shows it as 0.55
			assertThat(hintChance).isCloseTo(0.55, within(0.03));
		}

		@Test
		void lastPredictor_leavesOutTheForbiddenTakes() {
			List<Card> botHand = List.of(getCard(14, Color.HEARTS), getCard(7, Color.DIAMONDS));

			SimulationState state = createPredictionState(botHand, 2, 2);
			state.setFixedPrediction(0, 1);
			state.setFixedPrediction(1, 0);

			assertThat(predictionChances(state, botHand, new int[]{2, 2, 0})).containsOnlyKeys(0, 2);
		}

		@Test
		void emptyHand_alwaysTakesNothing() {
			// Between the last set and the next game every hand is empty
			assertThat(firstPredictorChances(List.of())).containsExactly(entry(0, 1.0));
		}

		private Map<Integer, Double> firstPredictorChances(List<Card> botHand) {
			return chancesInSeat(botHand, 0);
		}

		private Map<Integer, Double> firstPredictorChances(List<Card> botHand, int players) {
			return chancesInSeat(botHand, 0, players);
		}

		private Map<Integer, Double> chancesInSeat(List<Card> botHand, int botIndex) {
			return chancesInSeat(botHand, botIndex, 3);
		}

		private Map<Integer, Double> chancesInSeat(List<Card> botHand, int botIndex, int players) {
			return chancesInSeat(botHand, botIndex, players, engine::predictionChances);
		}

		private Map<Integer, Double> chancesInSeat(List<Card> botHand, int botIndex, int players, Chances chances) {
			int[] opponentSlots = new int[players];
			for (int i = 0; i < opponentSlots.length; i++) {
				opponentSlots[i] = i == botIndex ? 0 : botHand.size();
			}
			return predictionChances(createPredictionState(botHand, botIndex, 0, players), botHand, opponentSlots,
					new CardProvider(players).getShuffledDeckOfCards(), chances);
		}

		private Map<Integer, Double> predictionChances(SimulationState state, List<Card> botHand, int[] opponentSlots) {
			return predictionChances(state, botHand, opponentSlots, deckOfCards, engine::predictionChances);
		}

		private Map<Integer, Double> predictionChances(SimulationState state, List<Card> botHand, int[] opponentSlots,
													   List<Card> deck, Chances chances) {
			List<Card> unknownCards = new ArrayList<>(deck);
			unknownCards.removeAll(botHand);

			return chances.of(state, unknownCards, opponentSlots, Map.of(), Map.of());
		}
	}

	@Nested
	class HintChanceTest {

		@Test
		void withinTheFirstCardLimit_unlikelyIsRaisedSlightly() {
			assertThat(MctsEngine.hintChance(0.2, 5, 6)).isCloseTo(0.216, within(0.001));
			assertThat(MctsEngine.hintChance(0.2, 4, 7)).isCloseTo(0.212, within(0.001));
		}

		@Test
		void oneCard_unlikelyIsRaised_nearCertainIsLowered() {
			assertThat(MctsEngine.hintChance(0.2, 7, 1)).isCloseTo(0.243, within(0.001));
			assertThat(MctsEngine.hintChance(0.9, 7, 1)).isCloseTo(0.877, within(0.001));
		}

		@Test
		void aboveTheLimit_withFivePlayers_isRaised() {
			assertThat(MctsEngine.hintChance(0.5, 5, 7)).isCloseTo(0.582, within(0.001));
		}

		@Test
		void aboveTheLimit_withThreePlayers_isLowered() {
			assertThat(MctsEngine.hintChance(0.5, 3, 8)).isCloseTo(0.477, within(0.001));
		}

		@Test
		void aboveTheLimit_unlikelyIsRaised_nearCertainIsLowered() {
			assertThat(MctsEngine.hintChance(0.1, 3, 8)).isCloseTo(0.254, within(0.001));
			assertThat(MctsEngine.hintChance(0.9, 3, 8)).isCloseTo(0.709, within(0.001));
		}

		@Test
		void certainAndImpossible_stayAsTheyAre() {
			assertThat(MctsEngine.hintChance(1.0, 5, 8)).isEqualTo(1.0);
			assertThat(MctsEngine.hintChance(0.0, 5, 8)).isEqualTo(0.0);
			assertThat(MctsEngine.hintChance(1.0, 3, 1)).isEqualTo(1.0);
			assertThat(MctsEngine.hintChance(0.0, 3, 1)).isEqualTo(0.0);
		}

		@Test
		void everyTableSizeTheGameAllows_isCalibrated() {
			for (int players : PointProvider.NUMBER_OF_USERS_TO_WINNER_MAP.keySet()) {
				assertThat(MctsEngine.hintChance(0.5, players, 1)).as(players + " players, 1 card")
						.isBetween(0.0, 1.0);
				assertThat(MctsEngine.hintChance(0.5, players, 10)).as(players + " players, 10 cards")
						.isBetween(0.0, 1.0);
			}
		}

		@Test
		void higherChance_staysHigher() {
			assertThat(MctsEngine.hintChance(0.3, 4, 9)).isLessThan(MctsEngine.hintChance(0.31, 4, 9));
		}
	}

	@Nested
	class AllowedPredictionTest {

		@Test
		void allowed_roundsToNearest() {
			assertThat(MctsEngine.allowedPrediction(1.4, takes -> false)).isEqualTo(1);
			assertThat(MctsEngine.allowedPrediction(1.6, takes -> false)).isEqualTo(2);
		}

		@Test
		void forbidden_guessAboveRounded_stepsUp() {
			assertThat(MctsEngine.allowedPrediction(1.3, takes -> takes == 1)).isEqualTo(2);
		}

		@Test
		void forbidden_guessBelowRounded_stepsDown() {
			assertThat(MctsEngine.allowedPrediction(1.7, takes -> takes == 2)).isEqualTo(1);
		}

		@Test
		void forbidden_wholeGuess_stepsDown() {
			assertThat(MctsEngine.allowedPrediction(2.0, takes -> takes == 2)).isEqualTo(1);
		}

		@Test
		void forbiddenZero_stepsUpRatherThanNegative() {
			assertThat(MctsEngine.allowedPrediction(0.0, takes -> takes == 0)).isEqualTo(1);
		}
	}

	@Nested
	class SelectCardEarlyReturnTest {

		@Test
		void singleLegalCard_returnsImmediately() {
			// Bot must follow clubs but only has one club
			Card sevenClubs = getCard(7, Color.CLUBS);
			Card aceHearts = getCard(14, Color.HEARTS);
			List<Card> botHand = List.of(sevenClubs, aceHearts);

			// Someone led with a club — bot must follow suit
			Card eightClubs = getCard(8, Color.CLUBS);
			SimulationState state = createPlayingStateWithTrick(botHand, 1,
					new int[]{0, 0, 0}, new int[]{0, 0, 0}, List.of(eightClubs), 0);
			givenAllPredictionsKnown(state);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			unknownCards.remove(eightClubs);

			Card selected = engine.selectCard(state, unknownCards, new int[]{2, 0, 2},
					Map.of(), Map.of());

			assertThat(selected).isEqualTo(sevenClubs);
		}

		@Test
		void sequentialSameColor_returnsAny() {
			// Bot has 8♣ and 9♣, must follow clubs — sequential, so equivalent
			Card eightClubs = getCard(8, Color.CLUBS);
			Card nineClubs = getCard(9, Color.CLUBS);
			Card aceHearts = getCard(14, Color.HEARTS);
			List<Card> botHand = List.of(eightClubs, nineClubs, aceHearts);

			Card sevenClubs = getCard(7, Color.CLUBS);
			SimulationState state = createPlayingStateWithTrick(botHand, 1,
					new int[]{0, 0, 0}, new int[]{0, 0, 0}, List.of(sevenClubs), 0);
			givenAllPredictionsKnown(state);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			unknownCards.remove(sevenClubs);

			Card selected = engine.selectCard(state, unknownCards, new int[]{2, 0, 2},
					Map.of(), Map.of());

			assertThat(selected).isIn(eightClubs, nineClubs);
		}

		@Test
		void gapWithPlayedCard_equivalent() {
			// Bot has 8♣ and 10♣, must follow clubs. 9♣ already played → equivalent
			Card eightClubs = getCard(8, Color.CLUBS);
			Card tenClubs = getCard(10, Color.CLUBS);
			Card aceHearts = getCard(14, Color.HEARTS);
			List<Card> botHand = List.of(eightClubs, tenClubs, aceHearts);

			Card sevenClubs = getCard(7, Color.CLUBS);
			Card nineClubs = getCard(9, Color.CLUBS);
			SimulationState state = createPlayingStateWithTrick(botHand, 1,
					new int[]{0, 0, 0}, new int[]{0, 0, 0}, List.of(sevenClubs), 0);
			givenAllPredictionsKnown(state);

			// 9♣ already played — not in unknownCards
			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			unknownCards.remove(sevenClubs);
			unknownCards.remove(nineClubs);

			Card selected = engine.selectCard(state, unknownCards, new int[]{2, 0, 2},
					Map.of(), Map.of());

			assertThat(selected).isIn(eightClubs, tenClubs);
		}

		@Test
		void gapWithUnknownCard_notEquivalent() {
			// Bot has 8♣ and 10♣, must follow clubs. 9♣ still in opponent hands → not equivalent
			Card eightClubs = getCard(8, Color.CLUBS);
			Card tenClubs = getCard(10, Color.CLUBS);
			Card aceHearts = getCard(14, Color.HEARTS);
			List<Card> botHand = List.of(eightClubs, tenClubs, aceHearts);

			Card sevenClubs = getCard(7, Color.CLUBS);
			SimulationState state = createPlayingStateWithTrick(botHand, 1,
					new int[]{0, 0, 0}, new int[]{0, 0, 0}, List.of(sevenClubs), 0);
			givenAllPredictionsKnown(state);

			// 9♣ still unknown — opponents could hold it
			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			unknownCards.remove(sevenClubs);

			Card selected = engine.selectCard(state, unknownCards, new int[]{2, 0, 2},
					Map.of(), Map.of());

			// MCTS runs full simulations, result is valid either way
			assertThat(selected).isIn(eightClubs, tenClubs);
		}

		@Test
		void differentColors_notEquivalent() {
			// Bot leads with 8♣ and 8♦ — different colors, not equivalent
			Card eightClubs = getCard(8, Color.CLUBS);
			Card eightDiamonds = getCard(8, Color.DIAMONDS);
			List<Card> botHand = List.of(eightClubs, eightDiamonds);

			SimulationState state = createPlayingState(botHand, 0,
					new int[]{0, 0, 0}, new int[]{0, 0, 0});
			givenAllPredictionsKnown(state);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);

			Card selected = engine.selectCard(state, unknownCards, new int[]{0, 2, 2},
					Map.of(), Map.of());

			// Full MCTS runs — both are valid
			assertThat(selected).isIn(eightClubs, eightDiamonds);
		}
	}

	@Nested
	class SelectCardTest {

		@Test
		void sampledOpponentHandsMatchTheTricksOpponentsStillNeed() {
			// Opponent 1 needs all 3 of its tricks, so every sampled world hands it J♥, the one trump above
			// the bot's 9♥, and the bot leads its own suit instead (300 of 300 searches per suit; 13–21%
			// without the constraint, hence a majority). Nobody else holds that suit, so its two cards tie
			// and the lowest must win; rotating the suits makes a tie left to hash order fail at least once.
			List<Color> plainSuits = List.of(Color.CLUBS, Color.SPADES, Color.DIAMONDS);
			for (int rotation = 0; rotation < plainSuits.size(); rotation++) {
				Color botSuit = plainSuits.get(rotation);
				Color otherSuit = plainSuits.get((rotation + 1) % 3);
				Color thirdSuit = plainSuits.get((rotation + 2) % 3);

				List<Card> botHand = List.of(getCard(9, botSuit), getCard(9, Color.HEARTS), getCard(7, botSuit));
				List<Card> unknownCards = List.of(getCard(7, otherSuit), getCard(9, thirdSuit), getCard(14, thirdSuit),
						getCard(13, otherSuit), getCard(11, Color.HEARTS), getCard(11, otherSuit));
				int[] opponentSlots = {0, 3, 3};

				SimulationState state = createPlayingState(botHand, 0, new int[]{3, 3, 1}, new int[]{0, 0, 0});
				givenAllPredictionsKnown(state);

				int lowestLeads = 0;
				for (int i = 0; i < 10; i++) {
					Card card = engine.selectCard(state, unknownCards, opponentSlots, Map.of(), Map.of());
					if (card.equals(getCard(7, botSuit))) {
						lowestLeads++;
					}
				}

				assertThat(lowestLeads).as("bot suit %s", botSuit).isGreaterThanOrEqualTo(8);
			}
		}

		@Test
		void predictionMet_prefersLowCardWhenLeading() {
			// Bot predicted 0, has 0 — should lead with weakest card to avoid winning
			Card aceHearts = getCard(14, Color.HEARTS);
			Card sevenDiamonds = getCard(7, Color.DIAMONDS);
			List<Card> botHand = List.of(aceHearts, sevenDiamonds);

			SimulationState state = createPlayingState(botHand, 0,
					new int[]{0, 0, 0}, new int[]{0, 0, 0});
			givenAllPredictionsKnown(state);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			int[] opponentSlots = {0, 2, 2};

			Card selected = engine.selectCard(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			// A♥ guarantees a win — bot should prefer 7♦ to avoid winning
			assertThat(selected).isEqualTo(sevenDiamonds);
		}

		@Test
		void knownPredictions_usedByDeterminizer() {
			// Bot predicted 1, already won 1 — must avoid winning more
			// Opponents predicted 0 — with knownPrediction set, determinizer
			// should deal them weaker hands, improving simulation quality
			Card aceHearts = getCard(14, Color.HEARTS);
			Card sevenDiamonds = getCard(7, Color.DIAMONDS);
			List<Card> botHand = List.of(aceHearts, sevenDiamonds);

			SimulationState state = createPlayingState(botHand, 0,
					new int[]{1, 0, 0}, new int[]{1, 0, 0});
			givenAllPredictionsKnown(state);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			int[] opponentSlots = {0, 2, 2};

			Card selected = engine.selectCard(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			// Bot already met prediction — should lead low to avoid winning
			assertThat(selected).isEqualTo(sevenDiamonds);
		}
	}

	private SimulationState createPlayingStateWithTrick(List<Card> botHand, int botIndex,
													 int[] expectedTakes, int[] actualTakes,
													 List<Card> currentTrick, int leadPlayerIndex) {
		List<List<Card>> hands = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			hands.add(i == botIndex ? new ArrayList<>(botHand) : new ArrayList<>());
		}

		return new SimulationState(
				hands,
				expectedTakes,
				actualTakes,
				new ArrayList<>(currentTrick),
				SimulationState.Phase.PLAYING,
				leadPlayerIndex, botIndex, 0,
				botHand.size(),
				botIndex, 3
		);
	}

	private SimulationState createPlayingState(List<Card> botHand, int botIndex,
											   int[] expectedTakes, int[] actualTakes) {
		List<List<Card>> hands = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			hands.add(i == botIndex ? new ArrayList<>(botHand) : new ArrayList<>());
		}

		return new SimulationState(
				hands,
				expectedTakes,
				actualTakes,
				new ArrayList<>(),
				SimulationState.Phase.PLAYING,
				botIndex, botIndex, 0,
				botHand.size(),
				botIndex, 3
		);
	}

	private static void givenAllPredictionsKnown(SimulationState state) {
		for (int i = 0; i < state.getTotalPlayers(); i++) {
			state.setKnownPrediction(i);
		}
	}

	private SimulationState createPredictionState(List<Card> botHand, int botIndex,
												  int predictionsDone) {
		return createPredictionState(botHand, botIndex, predictionsDone, 3);
	}

	private SimulationState createPredictionState(List<Card> botHand, int botIndex,
												  int predictionsDone, int players) {
		List<List<Card>> hands = new ArrayList<>();
		for (int i = 0; i < players; i++) {
			hands.add(i == botIndex ? new ArrayList<>(botHand) : new ArrayList<>());
		}

		return new SimulationState(
				hands,
				new int[players],
				new int[players],
				new ArrayList<>(),
				SimulationState.Phase.PREDICTING,
				0, botIndex, 0,
				botHand.size(),
				botIndex, predictionsDone
		);
	}

	private interface Chances {

		Map<Integer, Double> of(SimulationState state, List<Card> unknownCards, int[] opponentSlots,
								Map<Integer, Set<Color>> colorVoids, Map<Integer, Set<Card>> excludedCards);
	}
}
