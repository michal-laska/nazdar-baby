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

class RolloutPolicyTest {

	private List<Card> deckOfCards;

	@BeforeEach
	void setUp() {
		CardProvider cardProvider = new CardProvider(3);
		deckOfCards = cardProvider.getShuffledDeckOfCards();
	}

	private Card getCard(int value, Color color) {
		return deckOfCards.stream()
				.filter(card -> card.getValue() == value && card.getColor() == color)
				.findFirst()
				.get();
	}

	/**
	 * Create a single-trick 3-player state in PLAYING phase where player 0 (bot) leads.
	 * Bot expected 1 trick, has 0 so far, with 1 remaining — triggers the "needed > 0 && needed < remaining" branch
	 * only when totalTricks > 1. For single-trick (totalTricks=1), needed == remaining so it goes to selectHighest.
	 * To hit the non-heart guard, we use totalTricks=2 and tricksPlayed=0 so needed(1) < remaining(2).
	 */
	private SimulationState createLeadState(List<Card> botHand, List<Card> p1Hand, List<Card> p2Hand) {
		List<List<Card>> hands = new ArrayList<>();
		hands.add(new ArrayList<>(botHand));
		hands.add(new ArrayList<>(p1Hand));
		hands.add(new ArrayList<>(p2Hand));

		return new SimulationState(
				hands,
				new int[]{1, 0, 0}, // bot expects 1 trick
				new int[]{0, 0, 0},
				new ArrayList<>(),
				SimulationState.Phase.PLAYING,
				0, 0, 0,
				2, // 2 total tricks so needed(1) < remaining(2)
				0, 3 // bot=0, all predictions done
		);
	}

	@Nested
	class SelectLeadCardTest {

		@Test
		void weakNonHeart_vs_strongHeart_leadsHeart() {
			Card nineClubs = getCard(9, Color.CLUBS);
			Card kingHearts = getCard(13, Color.HEARTS);

			SimulationState state = createLeadState(
					List.of(nineClubs, kingHearts),
					List.of(getCard(8, Color.SPADES), getCard(7, Color.DIAMONDS)),
					List.of(getCard(8, Color.DIAMONDS), getCard(7, Color.SPADES))
			);

			RolloutPolicy.rollout(state);

			// Bot should have led K♥ (removed from hand), keeping 9♣
			assertThat(state.getHand(0)).doesNotContain(kingHearts);
		}

		@Test
		void nonHeartKing_vs_heartKing_prefersNonHeart() {
			Card kingClubs = getCard(13, Color.CLUBS);
			Card kingHearts = getCard(13, Color.HEARTS);

			SimulationState state = createLeadState(
					List.of(kingClubs, kingHearts),
					List.of(getCard(8, Color.SPADES), getCard(7, Color.DIAMONDS)),
					List.of(getCard(8, Color.DIAMONDS), getCard(7, Color.SPADES))
			);

			RolloutPolicy.rollout(state);

			// Bot should have led K♣ (strong non-heart King), saving K♥ for trumping
			assertThat(state.getHand(0)).doesNotContain(kingClubs);
		}

		@Test
		void nonHeartAce_vs_heartKing_prefersNonHeart() {
			Card aceClubs = getCard(14, Color.CLUBS);
			Card kingHearts = getCard(13, Color.HEARTS);

			SimulationState state = createLeadState(
					List.of(aceClubs, kingHearts),
					List.of(getCard(8, Color.SPADES), getCard(7, Color.DIAMONDS)),
					List.of(getCard(8, Color.DIAMONDS), getCard(7, Color.SPADES))
			);

			RolloutPolicy.rollout(state);

			// Bot should have led A♣ (strong non-heart Ace), saving K♥
			assertThat(state.getHand(0)).doesNotContain(aceClubs);
		}

		@Test
		void onlyHearts_leadsStrongestHeart() {
			Card sevenHearts = getCard(7, Color.HEARTS);
			Card aceHearts = getCard(14, Color.HEARTS);

			SimulationState state = createLeadState(
					List.of(sevenHearts, aceHearts),
					List.of(getCard(8, Color.SPADES), getCard(7, Color.DIAMONDS)),
					List.of(getCard(8, Color.DIAMONDS), getCard(7, Color.SPADES))
			);

			RolloutPolicy.rollout(state);

			// With only hearts, should lead A♥ (strongest)
			assertThat(state.getHand(0)).doesNotContain(aceHearts);
		}
	}

	@Nested
	class DisruptionTest {

		@Test
		void leadLow_whenMostOpponentsOnTarget() {
			Card aceSpades = getCard(14, Color.SPADES);
			Card sevenClubs = getCard(7, Color.CLUBS);

			// Bot predicted 2, has 0, only 1 trick remaining → needed(2) > remaining(1) → disrupt
			// Opponents: predicted 0, actual 0 → on target (avoiding tricks)
			SimulationState state = createDisruptionLeadState(
					List.of(aceSpades, sevenClubs),
					new int[]{2, 0, 0}, // expected
					new int[]{0, 0, 0}, // actual
					1 // tricksPlayed (1 remaining out of 2 total)
			);

			RolloutPolicy.rollout(state);

			// Should lead low (7♣) to force unwanted take on opponents who are on target
			assertThat(state.getHand(0)).doesNotContain(sevenClubs);
		}

		@Test
		void leadHigh_whenMostOpponentsNeedTricks() {
			Card aceSpades = getCard(14, Color.SPADES);
			Card sevenClubs = getCard(7, Color.CLUBS);

			// Bot predicted 2, has 0, only 1 trick remaining → disrupt
			// Opponents: predicted 1, actual 0 → need tricks
			SimulationState state = createDisruptionLeadState(
					List.of(aceSpades, sevenClubs),
					new int[]{2, 1, 1}, // expected
					new int[]{0, 0, 0}, // actual
					1 // tricksPlayed
			);

			RolloutPolicy.rollout(state);

			// Should lead high (A♠) to steal from opponents who need tricks
			assertThat(state.getHand(0)).doesNotContain(aceSpades);
		}

		@Test
		void followLow_whenWinnerDoesNotNeedTrick() {
			Card aceSpades = getCard(14, Color.SPADES);
			Card sevenSpades = getCard(7, Color.SPADES);
			Card tenSpades = getCard(10, Color.SPADES);

			// Bot is player 2 (last), predicted 0, actual 1 → needed < 0 → disrupt
			// Player 0 led 10♠, player 1 hasn't played yet — simplified:
			// Bot has spades, trick has 10♠, winner (player 0) predicted 0, actual 0 → doesn't need more
			SimulationState state = createDisruptionFollowState(
					List.of(aceSpades, sevenSpades),
					List.of(tenSpades), // current trick
					new int[]{0, 0, 0}, // expected: winner (player 0) doesn't need tricks
					new int[]{0, 0, 1}, // actual: bot exceeded prediction
					0 // lead player index
			);

			List<MctsAction> actions = state.getLegalActions();
			// Bot must follow spades. Both A♠ and 7♠ are legal.
			assertThat(actions).hasSize(2);

			RolloutPolicy.rollout(state);

			// Winner doesn't need trick → shed safely (lowest loser when not last)
			assertThat(state.getHand(2)).doesNotContain(sevenSpades);
		}

		@Test
		void shedHighLoser_whenWinnerDoesNotNeedTrick_andLast() {
			Card queenSpades = getCard(12, Color.SPADES);
			Card nineSpades = getCard(9, Color.SPADES);
			Card kingSpades = getCard(13, Color.SPADES);
			Card eightSpades = getCard(8, Color.SPADES);

			// Bot is player 2 (last), predicted 0, actual 1 → needed < 0 → disrupt
			// Current trick: K♠, 8♠ → winner is player 0 (K♠), predicted 0 → doesn't need trick
			// totalOpponentsNeeded = 0 < remaining → winner-based fallback
			// Bot has Q♠, 9♠ — both lose to K♠. Being last → shed highest loser (Q♠)
			List<List<Card>> hands = new ArrayList<>();
			hands.add(new ArrayList<>(List.of(getCard(9, Color.DIAMONDS))));
			hands.add(new ArrayList<>(List.of(getCard(8, Color.DIAMONDS))));
			hands.add(new ArrayList<>(List.of(queenSpades, nineSpades)));

			SimulationState state = new SimulationState(
					hands,
					new int[]{0, 0, 0},
					new int[]{0, 0, 1},
					new ArrayList<>(List.of(kingSpades, eightSpades)),
					SimulationState.Phase.PLAYING,
					0, 2, 0,
					1,
					2, 3
			);

			RolloutPolicy.rollout(state);

			// Last to play, winner doesn't need trick → shed highest loser (Q♠)
			assertThat(state.getHand(2)).doesNotContain(queenSpades);
		}

		@Test
		void stealTrick_whenOpponentsNeedAllRemainingTricks() {
			Card aceSpades = getCard(14, Color.SPADES);
			Card sevenSpades = getCard(7, Color.SPADES);
			Card tenSpades = getCard(10, Color.SPADES);
			Card kingSpades = getCard(13, Color.SPADES);

			// Bot is player 2 (last), predicted 0, actual 1 → needed < 0 → disrupt
			// Player 0 predicted 2, actual 0 → needs 2; Player 1 predicted 0, actual 0 → needs 0
			// totalOpponentsNeeded = 2 = remaining(2) → steal to guarantee someone fails
			// Current trick: 10♠, K♠ → winner is player 1 (K♠) who doesn't need tricks
			// Without global check: would play to lose. With check: plays to win (A♠)
			List<List<Card>> hands = new ArrayList<>();
			hands.add(new ArrayList<>(List.of(getCard(9, Color.DIAMONDS), getCard(8, Color.CLUBS))));
			hands.add(new ArrayList<>(List.of(getCard(8, Color.DIAMONDS), getCard(9, Color.CLUBS))));
			hands.add(new ArrayList<>(List.of(aceSpades, sevenSpades)));

			SimulationState state = new SimulationState(
					hands,
					new int[]{2, 0, 0},
					new int[]{0, 0, 1},
					new ArrayList<>(List.of(tenSpades, kingSpades)),
					SimulationState.Phase.PLAYING,
					0, 2, 1,
					3,
					2, 3
			);

			RolloutPolicy.rollout(state);

			// Should steal with A♠ — opponents collectively need all remaining tricks
			assertThat(state.getHand(2)).doesNotContain(aceSpades);
		}

		/**
		 * Create a state where player 0 (bot) leads and is in disruption mode.
		 */
		private SimulationState createDisruptionLeadState(List<Card> botHand,
														  int[] expected, int[] actual,
														  int tricksPlayed) {
			List<List<Card>> hands = new ArrayList<>();
			hands.add(new ArrayList<>(botHand));
			hands.add(new ArrayList<>(List.of(getCard(8, Color.SPADES), getCard(9, Color.DIAMONDS))));
			hands.add(new ArrayList<>(List.of(getCard(8, Color.DIAMONDS), getCard(9, Color.SPADES))));

			return new SimulationState(
					hands, expected, actual,
					new ArrayList<>(),
					SimulationState.Phase.PLAYING,
					0, 0, tricksPlayed,
					tricksPlayed + botHand.size(), // total tricks
					0, 3
			);
		}

		/**
		 * Create a state where player 2 (bot) follows and is in disruption mode.
		 */
		private SimulationState createDisruptionFollowState(List<Card> botHand,
															List<Card> currentTrick,
															int[] expected, int[] actual,
															int leadPlayerIndex) {
			List<List<Card>> hands = new ArrayList<>();
			hands.add(new ArrayList<>(List.of(getCard(9, Color.DIAMONDS))));
			hands.add(new ArrayList<>(List.of(getCard(8, Color.DIAMONDS))));
			hands.add(new ArrayList<>(botHand));

			return new SimulationState(
					hands, expected, actual,
					new ArrayList<>(currentTrick),
					SimulationState.Phase.PLAYING,
					leadPlayerIndex, 2, 0,
					1, // 1 total trick
					2, 3 // bot=player 2
			);
		}
	}

	@Nested
	class EstimateInContextTest {

		@Test
		void lastPredictor_handStrengthDominatesOverRemaining() {
			// Hand estimate = 5 (strong hand), opponents claimed all 6 tricks → remaining = 0
			// 70/30 blend: 0.7*5 + 0.3*0 = 3.5 → 4
			// 50/50 would give: 0.5*5 + 0.5*0 = 2.5 → 3
			// So predict=4 should score higher than predict=3, proving hand dominates
			Card aceHearts = getCard(14, Color.HEARTS);
			Card aceSpades = getCard(14, Color.SPADES);
			Card aceDiamonds = getCard(14, Color.DIAMONDS);
			Card kingHearts = getCard(13, Color.HEARTS);
			Card kingSpades = getCard(13, Color.SPADES);
			Card queenHearts = getCard(12, Color.HEARTS);

			List<List<Card>> hands = new ArrayList<>();
			hands.add(new ArrayList<>());
			hands.add(new ArrayList<>());
			hands.add(new ArrayList<>(List.of(aceHearts, aceSpades, aceDiamonds,
					kingHearts, kingSpades, queenHearts)));

			SimulationState state = new SimulationState(
					hands,
					new int[]{3, 3, 0}, // opponents claimed all 6 tricks
					new int[]{0, 0, 0},
					new ArrayList<>(),
					SimulationState.Phase.PREDICTING,
					0, 2, 0,
					6, // totalTricks
					2, // botPlayerIndex
					2  // predictionsDone (both opponents predicted)
			);
			state.setKnownPrediction(0);
			state.setKnownPrediction(1);

			double heuristic4 = RolloutPolicy.heuristicValue(state, new MctsAction.PredictTakes(4));
			double heuristic3 = RolloutPolicy.heuristicValue(state, new MctsAction.PredictTakes(3));

			// With 70/30 (hand-favoring), estimate=4, so predict=4 scores higher
			// With 50/50, estimate would be 3, and this assertion would fail
			assertThat(heuristic4).isGreaterThan(heuristic3);
		}

		@Test
		void firstPredictor_usesOnlyHandStrength() {
			// When not the last predictor, context blending should NOT activate
			Card aceHearts = getCard(14, Color.HEARTS);
			Card sevenDiamonds = getCard(7, Color.DIAMONDS);

			List<List<Card>> hands = new ArrayList<>();
			hands.add(new ArrayList<>(List.of(aceHearts, sevenDiamonds)));
			hands.add(new ArrayList<>());
			hands.add(new ArrayList<>());

			SimulationState state = new SimulationState(
					hands,
					new int[]{0, 0, 0},
					new int[]{0, 0, 0},
					new ArrayList<>(),
					SimulationState.Phase.PREDICTING,
					0, 0, 0,
					2,
					0, // botPlayerIndex
					0  // predictionsDone = 0 (first predictor)
			);

			// Hand estimate = 1 (ace of hearts). predict=1 should score highest.
			double heuristic1 = RolloutPolicy.heuristicValue(state, new MctsAction.PredictTakes(1));
			double heuristic0 = RolloutPolicy.heuristicValue(state, new MctsAction.PredictTakes(0));
			double heuristic2 = RolloutPolicy.heuristicValue(state, new MctsAction.PredictTakes(2));

			assertThat(heuristic1).isGreaterThan(heuristic0);
			assertThat(heuristic1).isGreaterThan(heuristic2);
		}
	}

	@Nested
	class EstimateTakesTest {

		@Test
		void aceOfHearts_estimatesOne() {
			List<Card> hand = List.of(getCard(14, Color.HEARTS));
			assertThat(RolloutPolicy.estimateTakes(hand, 3, false)).isEqualTo(1);
		}

		@Test
		void lowCards_estimatesZero() {
			List<Card> hand = List.of(
					getCard(7, Color.CLUBS),
					getCard(8, Color.DIAMONDS),
					getCard(9, Color.SPADES)
			);
			assertThat(RolloutPolicy.estimateTakes(hand, 3, false)).isZero();
		}

		@Test
		void mixedHand_withKingAndLowCards_estimatesOne() {
			List<Card> hand = List.of(
					getCard(7, Color.CLUBS),
					getCard(13, Color.SPADES),
					getCard(9, Color.HEARTS)
			);
			assertThat(RolloutPolicy.estimateTakes(hand, 3, false)).isEqualTo(1);
		}

		@Test
		void voidBonus_higherWhenFollowing() {
			// Hand with hearts + void in another suit — void is more valuable when following
			List<Card> hand = List.of(
					getCard(9, Color.HEARTS),
					getCard(10, Color.HEARTS),
					getCard(7, Color.CLUBS)
			);
			int asFollower = RolloutPolicy.estimateTakes(hand, 3, false);
			int asLeader = RolloutPolicy.estimateTakes(hand, 3, true);

			assertThat(asFollower).isGreaterThanOrEqualTo(asLeader);
		}

		@Test
		void nonHeartAce_strongerWhenLeading() {
			// Non-heart ace gets a boost when leading (you choose when to play it)
			// Use enough players so the base value is low enough for the +0.1 to matter
			List<Card> hand = List.of(
					getCard(14, Color.SPADES),
					getCard(7, Color.CLUBS),
					getCard(8, Color.DIAMONDS)
			);
			int asLeader = RolloutPolicy.estimateTakes(hand, 5, true);
			int asFollower = RolloutPolicy.estimateTakes(hand, 5, false);

			assertThat(asLeader).isGreaterThanOrEqualTo(asFollower);
		}
	}
}
