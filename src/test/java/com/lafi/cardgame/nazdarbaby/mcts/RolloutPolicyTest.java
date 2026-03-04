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
