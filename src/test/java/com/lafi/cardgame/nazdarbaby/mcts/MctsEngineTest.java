package com.lafi.cardgame.nazdarbaby.mcts;

import static org.assertj.core.api.Assertions.assertThat;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

	private Card getCard(int value, Color color) {
		return deckOfCards.stream()
				.filter(card -> card.getValue() == value && card.getColor() == color)
				.findFirst()
				.get();
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

			double prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			assertThat(Math.round(prediction)).isEqualTo(1);
		}

		@Test
		void weakHand_predictsZero() {
			Card sevenDiamonds = getCard(7, Color.DIAMONDS);
			List<Card> botHand = List.of(sevenDiamonds);

			SimulationState state = createPredictionState(botHand, 0, 0);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			int[] opponentSlots = {0, 1, 1};

			double prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			assertThat(Math.round(prediction)).isZero();
		}

		@Test
		void strongHand_multiCard_leansUp() {
			// Ace of hearts + weak card: best prediction is 1,
			// direction should lean toward 2 (not toward 0)
			Card aceHearts = getCard(14, Color.HEARTS);
			Card sevenDiamonds = getCard(7, Color.DIAMONDS);
			List<Card> botHand = List.of(aceHearts, sevenDiamonds);

			SimulationState state = createPredictionState(botHand, 0, 0);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			int[] opponentSlots = {0, 2, 2};

			double prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			// Best prediction rounds to 1, but leans up — if 1 is forbidden,
			// last-predictor adjustment goes to 2 rather than 0
			assertThat(Math.round(prediction)).isEqualTo(1);
			assertThat(prediction).isGreaterThan(1.0);
		}

		@Test
		void lastPredictor_knownOpponentPredictions() {
			Card aceHearts = getCard(14, Color.HEARTS);
			List<Card> botHand = List.of(aceHearts);

			// Bot is player 2 (last predictor), opponents already predicted
			SimulationState state = createPredictionState(botHand, 2, 2);
			state.setKnownPrediction(0);
			state.setKnownPrediction(1);

			List<Card> unknownCards = new ArrayList<>(deckOfCards);
			unknownCards.removeAll(botHand);
			int[] opponentSlots = {1, 1, 0};

			double prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			assertThat(Math.round(prediction)).isEqualTo(1);
		}

		@Test
		void lastPredictor_usesRemainingTricksContext() {
			// Opponents predicted 0 each (sum=0), 3 tricks total, remaining=3
			// Bot has 3 aces — hand estimate ≈ 3, remaining = 3
			// With context blending, should still predict at least 2
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

			double prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			// Opponents claimed 0 tricks → 3 remaining for bot, hand is very strong
			assertThat(prediction).isGreaterThanOrEqualTo(2.0);
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

			double prediction = engine.predictTakes(state, unknownCards, opponentSlots,
					Map.of(), Map.of());

			// Three aces including trump ace — should predict at least 2
			assertThat(prediction).isGreaterThanOrEqualTo(2.0);
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
			state.setKnownPrediction(0);
			state.setKnownPrediction(1);
			state.setKnownPrediction(2);

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
			state.setKnownPrediction(0);
			state.setKnownPrediction(1);
			state.setKnownPrediction(2);

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
			state.setKnownPrediction(0);
			state.setKnownPrediction(1);
			state.setKnownPrediction(2);

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
			state.setKnownPrediction(0);
			state.setKnownPrediction(1);
			state.setKnownPrediction(2);

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
			state.setKnownPrediction(0);
			state.setKnownPrediction(1);
			state.setKnownPrediction(2);

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
		void predictionMet_prefersLowCardWhenLeading() {
			// Bot predicted 0, has 0 — should lead with weakest card to avoid winning
			Card aceHearts = getCard(14, Color.HEARTS);
			Card sevenDiamonds = getCard(7, Color.DIAMONDS);
			List<Card> botHand = List.of(aceHearts, sevenDiamonds);

			SimulationState state = createPlayingState(botHand, 0,
					new int[]{0, 0, 0}, new int[]{0, 0, 0});
			state.setKnownPrediction(0);
			state.setKnownPrediction(1);
			state.setKnownPrediction(2);

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
			state.setKnownPrediction(0);
			state.setKnownPrediction(1);
			state.setKnownPrediction(2);

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

	private SimulationState createPredictionState(List<Card> botHand, int botIndex,
												  int predictionsDone) {
		List<List<Card>> hands = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			hands.add(i == botIndex ? new ArrayList<>(botHand) : new ArrayList<>());
		}

		return new SimulationState(
				hands,
				new int[]{0, 0, 0},
				new int[]{0, 0, 0},
				new ArrayList<>(),
				SimulationState.Phase.PREDICTING,
				0, botIndex, 0,
				botHand.size(),
				botIndex, predictionsDone
		);
	}
}
