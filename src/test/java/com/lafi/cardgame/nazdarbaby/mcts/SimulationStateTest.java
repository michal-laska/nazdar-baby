package com.lafi.cardgame.nazdarbaby.mcts;

import static org.assertj.core.api.Assertions.assertThat;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SimulationStateTest {

	private List<Card> deckOfCards;

	@BeforeEach
	void setUp() {
		CardProvider cardProvider = new CardProvider(3);
		deckOfCards = cardProvider.getShuffledDeckOfCards();
	}

	@Nested
	class PredictionPhaseTest {

		@Test
		void legalActions_containsPredictionRange() {
			SimulationState state = createPredictionState(3);

			List<MctsAction> actions = state.getLegalActions();
			// With 3 cards, can predict 0, 1, 2, or 3
			assertThat(actions).hasSize(4);
			assertThat(actions).allMatch(a -> a instanceof MctsAction.PredictTakes);
		}

		@Test
		void applyPrediction_advancesToNextPlayer() {
			SimulationState state = createPredictionState(3);
			assertThat(state.getCurrentPlayerIndex()).isZero();

			state.applyAction(new MctsAction.PredictTakes(1));
			assertThat(state.getCurrentPlayerIndex()).isEqualTo(1);
		}

		@Test
		void allPredictionsDone_switchesToPlayingPhase() {
			SimulationState state = createPredictionState(2);

			state.applyAction(new MctsAction.PredictTakes(1)); // player 0
			state.applyAction(new MctsAction.PredictTakes(1)); // player 1
			state.applyAction(new MctsAction.PredictTakes(0)); // player 2

			assertThat(state.getPhase()).isEqualTo(SimulationState.Phase.PLAYING);
		}
	}

	@Nested
	class PlayingPhaseTest {

		@Test
		void legalActions_returnsPlayableCards() {
			SimulationState state = createPlayingState();

			List<MctsAction> actions = state.getLegalActions();
			assertThat(actions).isNotEmpty();
			assertThat(actions).allMatch(a -> a instanceof MctsAction.PlayCard);
		}

		@Test
		void playCard_removesFromHand() {
			SimulationState state = createPlayingState();
			int handSizeBefore = state.getHand(0).size();

			MctsAction action = state.getLegalActions().getFirst();
			state.applyAction(action);

			assertThat(state.getHand(0)).hasSize(handSizeBefore - 1);
		}

		@Test
		void trickResolution_incrementsActualTakes() {
			SimulationState state = createPlayingState();

			// Play one card from each player to complete a trick
			for (int i = 0; i < 3; i++) {
				List<MctsAction> actions = state.getLegalActions();
				state.applyAction(actions.getFirst());
			}

			// Someone's actual takes should have increased
			int totalTakes = 0;
			for (int i = 0; i < 3; i++) {
				totalTakes += state.getActualTakes(i);
			}
			assertThat(totalTakes).isEqualTo(1);
		}

		@Test
		void terminal_afterAllTricksPlayed() {
			SimulationState state = createPlayingState();

			// Play all tricks (1 card each = 1 trick for 3 players)
			while (!state.isTerminal()) {
				List<MctsAction> actions = state.getLegalActions();
				if (actions.isEmpty()) {
					break;
				}
				state.applyAction(actions.getFirst());
			}

			assertThat(state.isTerminal()).isTrue();
		}
	}

	@Nested
	class RewardTest {

		@Test
		void perfectPrediction_returnsPositiveReward() {
			// Bot predicted 0, took 0. Other players also predicted 0, took 0.
			// All 3 win → reward = 1.0/3
			SimulationState state = createTerminalState(0, 0);
			assertThat(state.getRewardForBot()).isGreaterThan(0.0);
		}

		@Test
		void soleWinner_returnsBestReward() {
			// Bot predicted 1, took 1 (wins). Others predicted 0, took 0 → also win.
			// 3 winners → reward = 1/3
			SimulationState allWin = createTerminalState(0, 0);
			// Bot predicted 1, took 1 (wins). Others predicted 1, took 0 → they lose.
			SimulationState soleWin = createTerminalStateWithOpponents(1, 1, 1, 0);
			assertThat(soleWin.getRewardForBot()).isGreaterThan(allWin.getRewardForBot());
		}

		@Test
		void offByOne_returnsLowReward() {
			// Bot predicted 1, took 0 → loss. Only 1 loser → reward 0.0
			SimulationState state = createTerminalState(1, 0);
			assertThat(state.getRewardForBot()).isEqualTo(0.0);
		}
	}

	@Nested
	class DeepCopyTest {

		@Test
		void copyIsIndependent() {
			SimulationState original = createPlayingState();
			SimulationState copy = original.deepCopy();

			MctsAction action = copy.getLegalActions().getFirst();
			copy.applyAction(action);

			// Original should be unaffected
			assertThat(original.getHand(0).size()).isNotEqualTo(copy.getHand(0).size());
		}
	}

	private SimulationState createPredictionState(int cardsPerPlayer) {
		List<List<Card>> hands = new ArrayList<>();
		int cardIndex = 0;
		for (int i = 0; i < 3; i++) {
			List<Card> hand = new ArrayList<>();
			for (int j = 0; j < cardsPerPlayer; j++) {
				hand.add(deckOfCards.get(cardIndex++));
			}
			hands.add(hand);
		}

		return new SimulationState(
				hands,
				new int[]{0, 0, 0},
				new int[]{0, 0, 0},
				new ArrayList<>(),
				SimulationState.Phase.PREDICTING,
				0, 0, 0,
				cardsPerPlayer,
				0, 0
		);
	}

	private SimulationState createPlayingState() {
		List<List<Card>> hands = new ArrayList<>();
		int cardIndex = 0;
		for (int i = 0; i < 3; i++) {
			List<Card> hand = new ArrayList<>();
			hand.add(deckOfCards.get(cardIndex++));
			hands.add(hand);
		}

		return new SimulationState(
				hands,
				new int[]{1, 0, 0},
				new int[]{0, 0, 0},
				new ArrayList<>(),
				SimulationState.Phase.PLAYING,
				0, 0, 0,
				1, // 1 trick total
				0, 3 // bot=0, all predictions done
		);
	}

	private SimulationState createTerminalState(int expectedTakes, int actualTakes) {
		List<List<Card>> hands = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			hands.add(new ArrayList<>());
		}

		return new SimulationState(
				hands,
				new int[]{expectedTakes, 0, 0},
				new int[]{actualTakes, 0, 0},
				new ArrayList<>(),
				SimulationState.Phase.PLAYING,
				0, 0,
				1, // 1 trick played
				1, // 1 trick total
				0, 3
		);
	}

	private SimulationState createTerminalStateWithOpponents(int botExpected, int botActual,
															 int opponentExpected, int opponentActual) {
		List<List<Card>> hands = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			hands.add(new ArrayList<>());
		}

		return new SimulationState(
				hands,
				new int[]{botExpected, opponentExpected, opponentExpected},
				new int[]{botActual, opponentActual, opponentActual},
				new ArrayList<>(),
				SimulationState.Phase.PLAYING,
				0, 0,
				1,
				1,
				0, 3
		);
	}
}
