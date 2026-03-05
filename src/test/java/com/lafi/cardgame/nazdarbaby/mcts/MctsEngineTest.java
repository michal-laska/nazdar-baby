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

			assertThat(prediction).isEqualTo(1.0);
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

			assertThat(prediction).isZero();
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

			assertThat(prediction).isEqualTo(1.0);
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
