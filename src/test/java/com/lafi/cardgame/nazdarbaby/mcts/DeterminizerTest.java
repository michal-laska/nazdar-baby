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
	void allDealtCardsComeFfromUnknownCards() {
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
