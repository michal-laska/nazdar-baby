package com.lafi.cardgame.nazdarbaby.mcts;

import static com.lafi.cardgame.nazdarbaby.card.TestCards.getCard;
import static org.assertj.core.api.Assertions.assertThat;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TrickEvaluatorTest {

	@Nested
	class PlayedCardsTest {

		@Test
		void noCardPlayedYet_isEmpty() {
			List<Card> table = List.of(CardProvider.CARD_PLACEHOLDER, CardProvider.CARD_PLACEHOLDER, CardProvider.CARD_PLACEHOLDER);

			assertThat(TrickEvaluator.playedCards(table)).isEmpty();
		}

		@Test
		void seatsStillToPlay_areDropped() {
			Card sevenOfClubs = getCard(7, Color.CLUBS);
			Card aceOfClubs = getCard(14, Color.CLUBS);
			List<Card> table = List.of(sevenOfClubs, aceOfClubs, CardProvider.CARD_PLACEHOLDER);

			assertThat(TrickEvaluator.playedCards(table)).containsExactly(sevenOfClubs, aceOfClubs);
		}

		@Test
		void completeTrick_isKeptInPlayOrder() {
			List<Card> table = List.of(getCard(9, Color.SPADES), getCard(7, Color.HEARTS), getCard(14, Color.SPADES));

			assertThat(TrickEvaluator.playedCards(table)).containsExactlyElementsOf(table);
		}
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

}
