package com.lafi.cardgame.nazdarbaby.mcts;

import static com.lafi.cardgame.nazdarbaby.card.TestCards.getCard;
import static org.assertj.core.api.Assertions.assertThat;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.Arrays;
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

	private static int[] noNeededTakes(int[] opponentSlots) {
		int[] neededTakes = new int[opponentSlots.length];
		Arrays.fill(neededTakes, -1);
		return neededTakes;
	}

	@Test
	void dealsCorrectNumberOfCards() {
		// Bot is player 0, opponents are 1 and 2
		List<Card> unknownCards = deckOfCards.subList(0, 16); // 16 unknown cards
		int[] opponentSlots = {0, 8, 8}; // bot=0, opponent1=8, opponent2=8

		List<List<Card>> hands = Determinizer.sampleOpponentHands(
				unknownCards, opponentSlots, Map.of(), 0, noNeededTakes(opponentSlots), Map.of());

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
				unknownCards, opponentSlots, colorVoids, 0, noNeededTakes(opponentSlots), Map.of());

		assertThat(hands.get(1))
				.noneMatch(card -> card.getColor() == Color.HEARTS);
	}

	@Test
	void allDealtCardsComeFromUnknownCards() {
		List<Card> unknownCards = deckOfCards.subList(0, 16);
		int[] opponentSlots = {0, 8, 8};

		List<List<Card>> hands = Determinizer.sampleOpponentHands(
				unknownCards, opponentSlots, Map.of(), 0, noNeededTakes(opponentSlots), Map.of());

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
					unknownCards, opponentSlots, Map.of(), 0, predictions, Map.of());

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
	void dealsHandsMatchingTheTricksAPlayerStillNeeds() {
		// Opponent 1 needs no more tricks, so it cannot hold both trumps
		List<Card> unknownCards = List.of(getCard(14, Color.HEARTS), getCard(13, Color.HEARTS),
				getCard(7, Color.CLUBS), getCard(8, Color.CLUBS));
		int[] opponentSlots = {0, 2, 2};
		int[] neededTakes = {-1, 0, 2};

		for (int i = 0; i < 50; i++) {
			List<List<Card>> hands = Determinizer.sampleOpponentHands(
					unknownCards, opponentSlots, Map.of(), 0, neededTakes, Map.of());

			assertThat(hands.get(1)).filteredOn(card -> card.getColor() == Color.HEARTS)
					.hasSizeLessThan(2);
		}
	}

	@Test
	void keepsColorVoidsWhenNoHandMatchesTheNeededTricks() {
		// Nobody can hold a hand worth 2 tricks here, so no deal is fully plausible
		List<Card> unknownCards = List.of(
				getCard(7, Color.CLUBS), getCard(8, Color.CLUBS),
				getCard(7, Color.SPADES), getCard(8, Color.SPADES));
		int[] opponentSlots = {0, 2, 2};
		int[] neededTakes = {-1, 2, 2};
		Map<Integer, Set<Color>> colorVoids = Map.of(1, Set.of(Color.CLUBS));

		for (int i = 0; i < 50; i++) {
			List<List<Card>> hands = Determinizer.sampleOpponentHands(
					unknownCards, opponentSlots, colorVoids, 0, neededTakes, Map.of());

			assertThat(hands.get(1)).noneMatch(card -> card.getColor() == Color.CLUBS);
		}
	}

	@Test
	void dealsNoHandTooWeakForTheTricksAPlayerStillNeeds() {
		// Opponent 1 needs both of its tricks, so a clubs-only hand is beyond the tolerance
		List<Card> unknownCards = List.of(getCard(14, Color.HEARTS), getCard(13, Color.HEARTS),
				getCard(7, Color.CLUBS), getCard(8, Color.CLUBS));
		int[] opponentSlots = {0, 2, 2};
		int[] neededTakes = {-1, 2, -1};

		for (int i = 0; i < 50; i++) {
			List<List<Card>> hands = Determinizer.sampleOpponentHands(
					unknownCards, opponentSlots, Map.of(), 0, neededTakes, Map.of());

			assertThat(hands.get(1)).anyMatch(card -> card.getColor() == Color.HEARTS);
		}
	}

	@Test
	void picksTheClosestHandWhenNoneMatchesTheNeededTricks() {
		// No hand is worth 3 tricks; the closest one holds the only winner, the ace of hearts
		Card aceOfHearts = getCard(14, Color.HEARTS);
		List<Card> unknownCards = List.of(aceOfHearts,
				getCard(7, Color.CLUBS), getCard(8, Color.CLUBS), getCard(9, Color.CLUBS),
				getCard(10, Color.CLUBS), getCard(11, Color.CLUBS));
		int[] opponentSlots = {0, 3, 3};
		int[] neededTakes = {-1, 3, -1};

		for (int i = 0; i < 50; i++) {
			List<List<Card>> hands = Determinizer.sampleOpponentHands(
					unknownCards, opponentSlots, Map.of(), 0, neededTakes, Map.of());

			assertThat(hands.get(1)).contains(aceOfHearts);
		}
	}

	@Test
	void doesNotShapeTheHandOfAPlayerWithNoTricksToAimFor() {
		// Opponent 1 has not predicted, so only opponent 2 constrains the deal
		List<Card> unknownCards = List.of(getCard(14, Color.HEARTS), getCard(13, Color.HEARTS),
				getCard(7, Color.CLUBS), getCard(8, Color.CLUBS));
		int[] opponentSlots = {0, 2, 2};
		int[] neededTakes = {-1, -1, 2};

		boolean opponentOneHeldTrump = false;
		for (int i = 0; i < 50 && !opponentOneHeldTrump; i++) {
			List<List<Card>> hands = Determinizer.sampleOpponentHands(
					unknownCards, opponentSlots, Map.of(), 0, neededTakes, Map.of());

			opponentOneHeldTrump = hands.get(1).stream().anyMatch(card -> card.getColor() == Color.HEARTS);
		}

		assertThat(opponentOneHeldTrump).isTrue();
	}

	@Test
	void respectsExcludedCards() {
		List<Card> unknownCards = deckOfCards.subList(0, 16);
		int[] opponentSlots = {0, 8, 8};

		// Find a specific card to exclude for player 1
		Card excludedCard = unknownCards.getFirst();
		Map<Integer, Set<Card>> excludedCards = Map.of(1, Set.of(excludedCard));

		for (int i = 0; i < 50; i++) {
			List<List<Card>> hands = Determinizer.sampleOpponentHands(
					unknownCards, opponentSlots, Map.of(), 0,
					new int[]{-1, -1, -1}, excludedCards);

			assertThat(hands.get(1)).doesNotContain(excludedCard);
		}
	}

	@Test
	void excludedCardCanGoToOtherPlayer() {
		List<Card> unknownCards = deckOfCards.subList(0, 16);
		int[] opponentSlots = {0, 8, 8};

		Card excludedCard = unknownCards.getFirst();
		Map<Integer, Set<Card>> excludedCards = Map.of(1, Set.of(excludedCard));

		boolean player2GotExcludedCard = false;
		for (int i = 0; i < 50; i++) {
			List<List<Card>> hands = Determinizer.sampleOpponentHands(
					unknownCards, opponentSlots, Map.of(), 0,
					new int[]{-1, -1, -1}, excludedCards);

			if (hands.get(2).contains(excludedCard)) {
				player2GotExcludedCard = true;
				break;
			}
		}

		assertThat(player2GotExcludedCard).isTrue();
	}

	@Test
	void noCardDealtTwice() {
		List<Card> unknownCards = deckOfCards.subList(0, 16);
		int[] opponentSlots = {0, 8, 8};

		List<List<Card>> hands = Determinizer.sampleOpponentHands(
				unknownCards, opponentSlots, Map.of(), 0, noNeededTakes(opponentSlots), Map.of());

		List<Card> allDealt = new java.util.ArrayList<>();
		allDealt.addAll(hands.get(1));
		allDealt.addAll(hands.get(2));

		assertThat(allDealt).doesNotHaveDuplicates();
	}
}
