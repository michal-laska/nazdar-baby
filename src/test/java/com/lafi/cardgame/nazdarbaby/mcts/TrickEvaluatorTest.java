package com.lafi.cardgame.nazdarbaby.mcts;

import static org.assertj.core.api.Assertions.assertThat;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TrickEvaluatorTest {

	private List<Card> deckOfCards;

	@BeforeEach
	void setUp() {
		CardProvider cardProvider = new CardProvider(3);
		deckOfCards = cardProvider.getShuffledDeckOfCards();
	}

	@Nested
	class GetWinningIndexTest {

		@Test
		void firstCardWins_whenHighestInColor() {
			Card aceOfSpades = getCard(14, Color.SPADES);
			Card kingOfSpades = getCard(13, Color.SPADES);
			Card queenOfSpades = getCard(12, Color.SPADES);

			int winnerIndex = TrickEvaluator.getWinningIndex(List.of(aceOfSpades, kingOfSpades, queenOfSpades));
			assertThat(winnerIndex).isZero();
		}

		@Test
		void lastCardWins_whenHighestInColor() {
			Card queenOfSpades = getCard(12, Color.SPADES);
			Card kingOfSpades = getCard(13, Color.SPADES);
			Card aceOfSpades = getCard(14, Color.SPADES);

			int winnerIndex = TrickEvaluator.getWinningIndex(List.of(queenOfSpades, kingOfSpades, aceOfSpades));
			assertThat(winnerIndex).isEqualTo(2);
		}

		@Test
		void heartsTrumps_nonHeartCards() {
			Card aceOfSpades = getCard(14, Color.SPADES);
			Card sevenOfHearts = getCard(7, Color.HEARTS);
			Card kingOfSpades = getCard(13, Color.SPADES);

			int winnerIndex = TrickEvaluator.getWinningIndex(List.of(aceOfSpades, sevenOfHearts, kingOfSpades));
			assertThat(winnerIndex).isEqualTo(1);
		}

		@Test
		void differentNonHeartColor_doesNotBeat_leadingColor() {
			Card aceOfSpades = getCard(14, Color.SPADES);
			Card aceOfDiamonds = getCard(14, Color.DIAMONDS);
			Card kingOfClubs = getCard(13, Color.CLUBS);

			int winnerIndex = TrickEvaluator.getWinningIndex(List.of(aceOfSpades, aceOfDiamonds, kingOfClubs));
			assertThat(winnerIndex).isZero();
		}

		@Test
		void higherHeartBeats_lowerHeart() {
			Card sevenOfHearts = getCard(7, Color.HEARTS);
			Card aceOfSpades = getCard(14, Color.SPADES);
			Card nineOfHearts = getCard(9, Color.HEARTS);

			int winnerIndex = TrickEvaluator.getWinningIndex(List.of(sevenOfHearts, aceOfSpades, nineOfHearts));
			assertThat(winnerIndex).isEqualTo(2);
		}
	}

	@Nested
	class GetLegalPlaysTest {

		@Test
		void allCardsPlayable_whenLeading() {
			Card aceOfSpades = getCard(14, Color.SPADES);
			Card sevenOfHearts = getCard(7, Color.HEARTS);
			List<Card> hand = List.of(aceOfSpades, sevenOfHearts);

			List<Card> legalPlays = TrickEvaluator.getLegalPlays(hand, List.of());
			assertThat(legalPlays).containsExactlyInAnyOrder(aceOfSpades, sevenOfHearts);
		}

		@Test
		void mustFollowSuit() {
			Card aceOfSpades = getCard(14, Color.SPADES);
			Card sevenOfHearts = getCard(7, Color.HEARTS);
			List<Card> hand = List.of(aceOfSpades, sevenOfHearts);

			Card leadCard = getCard(10, Color.SPADES);
			List<Card> legalPlays = TrickEvaluator.getLegalPlays(hand, List.of(leadCard));
			assertThat(legalPlays).containsExactly(aceOfSpades);
		}

		@Test
		void mustPlayHearts_whenCantFollowSuit() {
			Card aceOfSpades = getCard(14, Color.SPADES);
			Card sevenOfHearts = getCard(7, Color.HEARTS);
			List<Card> hand = List.of(aceOfSpades, sevenOfHearts);

			Card leadCard = getCard(10, Color.DIAMONDS);
			List<Card> legalPlays = TrickEvaluator.getLegalPlays(hand, List.of(leadCard));
			assertThat(legalPlays).containsExactly(sevenOfHearts);
		}

		@Test
		void anyCard_whenNoSuitAndNoHearts() {
			Card aceOfSpades = getCard(14, Color.SPADES);
			Card kingOfClubs = getCard(13, Color.CLUBS);
			List<Card> hand = List.of(aceOfSpades, kingOfClubs);

			Card leadCard = getCard(10, Color.DIAMONDS);
			List<Card> legalPlays = TrickEvaluator.getLegalPlays(hand, List.of(leadCard));
			assertThat(legalPlays).containsExactlyInAnyOrder(aceOfSpades, kingOfClubs);
		}
	}

	private Card getCard(int value, Color color) {
		return deckOfCards.stream()
				.filter(card -> card.getValue() == value && card.getColor() == color)
				.findFirst()
				.get();
	}
}
