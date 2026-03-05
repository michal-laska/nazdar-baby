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

			// Winner doesn't need trick → play low to force unwanted take
			assertThat(state.getHand(2)).doesNotContain(sevenSpades);
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
	class EstimateTakesTest {

		@Test
		void aceOfHearts_estimatesOne() {
			List<Card> hand = List.of(getCard(14, Color.HEARTS));
			assertThat(RolloutPolicy.estimateTakes(hand, 3)).isEqualTo(1);
		}

		@Test
		void lowCards_estimatesZero() {
			List<Card> hand = List.of(
					getCard(7, Color.CLUBS),
					getCard(8, Color.DIAMONDS),
					getCard(9, Color.SPADES)
			);
			assertThat(RolloutPolicy.estimateTakes(hand, 3)).isZero();
		}

		@Test
		void mixedHand_withKingAndLowCards_estimatesOne() {
			List<Card> hand = List.of(
					getCard(7, Color.CLUBS),
					getCard(13, Color.SPADES),
					getCard(9, Color.HEARTS)
			);
			assertThat(RolloutPolicy.estimateTakes(hand, 3)).isEqualTo(1);
		}
	}
}
