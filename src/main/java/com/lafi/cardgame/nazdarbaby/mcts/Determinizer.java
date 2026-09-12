package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates plausible opponent hands from unknown cards, respecting color void constraints
 * and opponent prediction consistency.
 */
final class Determinizer {

	private static final int MAX_ATTEMPTS = 50;
	private static final int PREDICTION_TOLERANCE = 1;

	private Determinizer() {
	}

	/**
	 * Deal unknown cards to opponent slots, respecting color voids and prediction plausibility.
	 *
	 * @param unknownCards      cards not visible to the bot
	 * @param opponentSlots     how many cards each player holds (bot slot = 0)
	 * @param colorVoids        per-player set of colors the player is known NOT to have
	 * @param botPlayerIndex    index of the bot player (skipped in dealing)
	 * @param neededTakes       per-player tricks still to win; negative when the prediction cannot
	 *                          constrain the hand (no prediction yet, or already past it)
	 * @return list of hands indexed by player index; bot hand is empty list
	 */
	static List<List<Card>> sampleOpponentHands(List<Card> unknownCards, int[] opponentSlots,
												Map<Integer, Set<Color>> colorVoids, int botPlayerIndex,
												int[] neededTakes,
												Map<Integer, Set<Card>> excludedCards) {
		int totalPlayers = opponentSlots.length;

		// Sort opponents by most-constrained-first (color voids + card exclusions)
		List<Integer> dealOrder = new ArrayList<>();
		for (int i = 0; i < totalPlayers; i++) {
			if (i != botPlayerIndex) {
				dealOrder.add(i);
			}
		}
		dealOrder.sort(Comparator.comparingInt(
				(Integer idx) -> colorVoids.getOrDefault(idx, Set.of()).size()
						+ excludedCards.getOrDefault(idx, Set.of()).size()).reversed());

		List<List<Card>> closestHands = null;
		int lowestMismatch = Integer.MAX_VALUE;

		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			List<Card> shuffled = new ArrayList<>(unknownCards);
			Collections.shuffle(shuffled);

			List<List<Card>> hands = emptyHands(totalPlayers);
			if (!tryDeal(shuffled, hands, opponentSlots, colorVoids, excludedCards, dealOrder)) {
				continue;
			}

			int mismatch = predictionMismatch(hands, neededTakes, botPlayerIndex);
			if (mismatch == 0) {
				return hands;
			}
			if (mismatch < lowestMismatch) {
				lowestMismatch = mismatch;
				closestHands = hands;
			}
		}

		// No perfectly plausible world — the closest one still respects voids and exclusions
		if (closestHands != null) {
			return closestHands;
		}

		// The constraints cannot be satisfied at all: deal without them
		List<Card> shuffled = new ArrayList<>(unknownCards);
		Collections.shuffle(shuffled);

		List<List<Card>> hands = emptyHands(totalPlayers);
		int cardIndex = 0;
		for (int playerIndex : dealOrder) {
			for (int j = 0; j < opponentSlots[playerIndex]; j++) {
				if (cardIndex < shuffled.size()) {
					hands.get(playerIndex).add(shuffled.get(cardIndex++));
				}
			}
		}

		return hands;
	}

	private static List<List<Card>> emptyHands(int totalPlayers) {
		List<List<Card>> hands = new ArrayList<>(totalPlayers);
		for (int i = 0; i < totalPlayers; i++) {
			hands.add(new ArrayList<>());
		}
		return hands;
	}

	/**
	 * Overload without exclusions for backward compatibility (tests).
	 */
	static List<List<Card>> sampleOpponentHands(List<Card> unknownCards, int[] opponentSlots,
												Map<Integer, Set<Color>> colorVoids, int botPlayerIndex,
												int[] neededTakes) {
		return sampleOpponentHands(unknownCards, opponentSlots, colorVoids, botPlayerIndex, neededTakes, Map.of());
	}

	/**
	 * Overload without needed takes and exclusions for backward compatibility (tests).
	 */
	static List<List<Card>> sampleOpponentHands(List<Card> unknownCards, int[] opponentSlots,
												Map<Integer, Set<Color>> colorVoids, int botPlayerIndex) {
		int[] unknownNeededTakes = new int[opponentSlots.length];
		java.util.Arrays.fill(unknownNeededTakes, -1);
		return sampleOpponentHands(unknownCards, opponentSlots, colorVoids, botPlayerIndex, unknownNeededTakes, Map.of());
	}

	private static boolean tryDeal(List<Card> shuffled, List<List<Card>> hands,
									int[] opponentSlots, Map<Integer, Set<Color>> colorVoids,
									Map<Integer, Set<Card>> excludedCards,
									List<Integer> dealOrder) {
		int cardIndex = 0;

		for (int playerIndex : dealOrder) {
			Set<Color> voids = colorVoids.getOrDefault(playerIndex, Set.of());
			Set<Card> excluded = excludedCards.getOrDefault(playerIndex, Set.of());
			int needed = opponentSlots[playerIndex];

			for (int j = 0; j < needed; j++) {
				boolean placed = false;
				for (int k = cardIndex; k < shuffled.size(); k++) {
					Card card = shuffled.get(k);
					if (!voids.contains(card.getColor()) && !excluded.contains(card)) {
						hands.get(playerIndex).add(card);
						// Swap used card to cardIndex position
						shuffled.set(k, shuffled.get(cardIndex));
						shuffled.set(cardIndex, card);
						cardIndex++;
						placed = true;
						break;
					}
				}
				if (!placed) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * How far the dealt hands are from what the players' outstanding predictions suggest.
	 * Zero means every hand is a believable holding for the tricks that player still needs.
	 * Players whose needed takes are negative are skipped: their prediction says nothing
	 * about what they hold, so scoring them would only bias the sample.
	 */
	private static int predictionMismatch(List<List<Card>> hands, int[] neededTakes, int botPlayerIndex) {
		int mismatch = 0;

		for (int i = 0; i < hands.size(); i++) {
			if (i == botPlayerIndex || neededTakes[i] < 0) {
				continue;
			}

			List<Card> hand = hands.get(i);
			if (hand.isEmpty()) {
				continue;
			}

			int estimate = RolloutPolicy.estimateTakes(hand, hands.size(), false);
			mismatch += Math.max(0, Math.abs(estimate - neededTakes[i]) - PREDICTION_TOLERANCE);
		}

		return mismatch;
	}
}
