package com.lafi.cardgame.nazdarbaby.mcts;

import static org.assertj.core.api.Assertions.assertThat;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeterminizerTest {

	private List<Card> deckOfCards;

	@BeforeEach
	void setUp() {
		CardProvider cardProvider = new CardProvider(3);
		deckOfCards = cardProvider.getShuffledDeckOfCards();
	}

	@Test
	void dealsCorrectNumberOfCards() {
		// Bot is player 0, opponents are 1 and 2
		List<Card> unknownCards = deckOfCards.subList(0, 16); // 16 unknown cards
		int[] opponentSlots = {0, 8, 8}; // bot=0, opponent1=8, opponent2=8

		List<List<Card>> hands = Determinizer.sampleOpponentHands(
				unknownCards, opponentSlots, Map.of(), 0);

		assertThat(hands.get(0)).isEmpty();
		assertThat(hands.get(1)).hasSize(8);
		assertThat(hands.get(2)).hasSize(8);
	}

	@Test
	void respectsColorVoids() {
		List<Card> unknownCards = deckOfCards.subList(0, 16);
		int[] opponentSlots = {0, 8, 8};

		// Player 1 cannot have hearts
		Map<Integer, Set<Color>> colorVoids = Map.of(1, Set.of(Color.HEARTS));

		List<List<Card>> hands = Determinizer.sampleOpponentHands(
				unknownCards, opponentSlots, colorVoids, 0);

		assertThat(hands.get(1))
				.noneMatch(card -> card.getColor() == Color.HEARTS);
	}

	@Test
	void allDealtCardsComeFromUnknownCards() {
		List<Card> unknownCards = deckOfCards.subList(0, 16);
		int[] opponentSlots = {0, 8, 8};

		List<List<Card>> hands = Determinizer.sampleOpponentHands(
				unknownCards, opponentSlots, Map.of(), 0);

		for (int i = 0; i < 3; i++) {
			for (Card card : hands.get(i)) {
				assertThat(unknownCards).contains(card);
			}
		}
	}

	@Test
	void unknownPrediction_doesNotFilterStrongHands() {
		// Build a pool of only strong cards (aces and kings)
		List<Card> strongCards = deckOfCards.stream()
				.filter(card -> card.getValue() >= 13)
				.toList();

		// Need at least 4 cards for 2 opponents with 2 each
		assertThat(strongCards).hasSizeGreaterThanOrEqualTo(4);
		List<Card> unknownCards = strongCards.subList(0, 4);

		int[] opponentSlots = {0, 2, 2}; // bot=0, opponent1=2, opponent2=2

		// prediction=-1 means unknown: plausibility check should be skipped entirely
		int[] predictions = {-1, -1, -1};

		// Run many times to ensure strong hands are never rejected
		for (int i = 0; i < 50; i++) {
			List<List<Card>> hands = Determinizer.sampleOpponentHands(
					unknownCards, opponentSlots, Map.of(), 0, predictions);

			assertThat(hands.get(0)).isEmpty();
			assertThat(hands.get(1)).hasSize(2);
			assertThat(hands.get(2)).hasSize(2);

			// All dealt cards should be from our strong pool
			for (int p = 1; p <= 2; p++) {
				for (Card card : hands.get(p)) {
					assertThat(card.getValue()).isGreaterThanOrEqualTo(13);
				}
			}
		}
	}

	@Test
	void noCardDealtTwice() {
		List<Card> unknownCards = deckOfCards.subList(0, 16);
		int[] opponentSlots = {0, 8, 8};

		List<List<Card>> hands = Determinizer.sampleOpponentHands(
				unknownCards, opponentSlots, Map.of(), 0);

		List<Card> allDealt = new java.util.ArrayList<>();
		allDealt.addAll(hands.get(1));
		allDealt.addAll(hands.get(2));

		assertThat(allDealt).doesNotHaveDuplicates();
	}
}
