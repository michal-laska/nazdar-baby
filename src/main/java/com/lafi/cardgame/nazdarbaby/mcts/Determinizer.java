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
	private static final int ACE_VALUE = 14;
	private static final int KING_VALUE = 13;

	private Determinizer() {
	}

	/**
	 * Deal unknown cards to opponent slots, respecting color voids and prediction plausibility.
	 *
	 * @param unknownCards      cards not visible to the bot
	 * @param opponentSlots     how many cards each player holds (bot slot = 0)
	 * @param colorVoids        per-player set of colors the player is known NOT to have
	 * @param botPlayerIndex    index of the bot player (skipped in dealing)
	 * @param opponentPredictions per-player predicted takes (-1 if unknown/not yet predicted)
	 * @return list of hands indexed by player index; bot hand is empty list
	 */
	static List<List<Card>> sampleOpponentHands(List<Card> unknownCards, int[] opponentSlots,
												Map<Integer, Set<Color>> colorVoids, int botPlayerIndex,
												int[] opponentPredictions) {
		int totalPlayers = opponentSlots.length;

		// Sort opponents by most-constrained-first (most color voids)
		List<Integer> dealOrder = new ArrayList<>();
		for (int i = 0; i < totalPlayers; i++) {
			if (i != botPlayerIndex) {
				dealOrder.add(i);
			}
		}
		dealOrder.sort(Comparator.comparingInt(
				(Integer idx) -> colorVoids.getOrDefault(idx, Set.of()).size()).reversed());

		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			List<Card> shuffled = new ArrayList<>(unknownCards);
			Collections.shuffle(shuffled);

			List<List<Card>> hands = new ArrayList<>(totalPlayers);
			for (int i = 0; i < totalPlayers; i++) {
				hands.add(new ArrayList<>());
			}

			boolean valid = tryDeal(shuffled, hands, opponentSlots, colorVoids, dealOrder);
			if (valid && isPredictionPlausible(hands, opponentPredictions, botPlayerIndex)) {
				return hands;
			}
		}

		// Fallback: unconstrained deal (ignore prediction plausibility)
		List<Card> shuffled = new ArrayList<>(unknownCards);
		Collections.shuffle(shuffled);

		List<List<Card>> hands = new ArrayList<>(totalPlayers);
		for (int i = 0; i < totalPlayers; i++) {
			hands.add(new ArrayList<>());
		}

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

	/**
	 * Overload without predictions for backward compatibility (tests).
	 */
	static List<List<Card>> sampleOpponentHands(List<Card> unknownCards, int[] opponentSlots,
												Map<Integer, Set<Color>> colorVoids, int botPlayerIndex) {
		int[] noPredictions = new int[opponentSlots.length];
		java.util.Arrays.fill(noPredictions, -1);
		return sampleOpponentHands(unknownCards, opponentSlots, colorVoids, botPlayerIndex, noPredictions);
	}

	private static boolean tryDeal(List<Card> shuffled, List<List<Card>> hands,
									int[] opponentSlots, Map<Integer, Set<Color>> colorVoids,
									List<Integer> dealOrder) {
		int cardIndex = 0;

		for (int playerIndex : dealOrder) {
			Set<Color> voids = colorVoids.getOrDefault(playerIndex, Set.of());
			int needed = opponentSlots[playerIndex];

			for (int j = 0; j < needed; j++) {
				boolean placed = false;
				for (int k = cardIndex; k < shuffled.size(); k++) {
					Card card = shuffled.get(k);
					if (!voids.contains(card.getColor())) {
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
	 * Check if dealt hands are roughly consistent with opponent predictions.
	 * Rejects wildly implausible hands (e.g., predicted 0 but holding 3 aces).
	 */
	private static boolean isPredictionPlausible(List<List<Card>> hands, int[] predictions, int botPlayerIndex) {
		for (int i = 0; i < hands.size(); i++) {
			if (i == botPlayerIndex) {
				continue;
			}
			int prediction = predictions[i];
			if (prediction < 0) {
				continue; // Unknown prediction, skip check
			}

			List<Card> hand = hands.get(i);
			if (hand.isEmpty()) {
				continue;
			}

			int strength = estimateHandStrength(hand);

			// Reject if hand strength is wildly inconsistent with prediction
			// Allow generous margin since predictions aren't perfect
			if (prediction == 0 && strength > hand.size()) {
				return false; // Predicted 0 but hand is very strong
			}
			if (prediction >= hand.size() && strength == 0) {
				return false; // Predicted max but hand has no strength
			}
		}
		return true;
	}

	/**
	 * Crude hand strength estimate: count aces, high hearts, kings.
	 */
	private static int estimateHandStrength(List<Card> hand) {
		int strength = 0;
		for (Card card : hand) {
			if (card.getValue() == ACE_VALUE) {
				strength += 2;
			} else if (card.getColor() == Color.HEARTS && card.getValue() >= 10) {
				strength += 2;
			} else if (card.getValue() == KING_VALUE) {
				strength += 1;
			} else if (card.getColor() == Color.HEARTS) {
				strength += 1;
			}
		}
		return strength;
	}
}
