package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.List;

public final class TrickEvaluator {

	private TrickEvaluator() {
	}

	/**
	 * Returns the index of the winning card in the trick.
	 * Replicates Game.getWinningCard() logic.
	 */
	static int getWinningIndex(List<Card> trick) {
		Card winningCard = null;
		int winningIndex = 0;

		for (int i = 0; i < trick.size(); i++) {
			Card card = trick.get(i);
			if (winningCard == null) {
				winningCard = card;
				winningIndex = i;
			} else if (winningCard.getColor() == card.getColor()) {
				if (card.getValue() > winningCard.getValue()) {
					winningCard = card;
					winningIndex = i;
				}
			} else if (card.getColor() == Color.HEARTS) {
				winningCard = card;
				winningIndex = i;
			}
		}

		return winningIndex;
	}

	/**
	 * Returns legal cards that can be played from hand given the current trick.
	 * Replicates BotSimulator.getSortedPlayableCards() logic.
	 *
	 * @param hand         non-placeholder cards in player's hand
	 * @param currentTrick cards played so far in current trick (may be empty)
	 */
	static List<Card> getLegalPlays(List<Card> hand, List<Card> currentTrick) {
		if (currentTrick.isEmpty()) {
			return hand;
		}

		Color leadingColor = currentTrick.getFirst().getColor();

		boolean hasLeadingColor = hand.stream().anyMatch(card -> card.getColor() == leadingColor);
		if (hasLeadingColor) {
			return hand.stream()
					.filter(card -> card.getColor() == leadingColor)
					.toList();
		}

		boolean hasHearts = hand.stream().anyMatch(card -> card.getColor() == Color.HEARTS);
		if (hasHearts) {
			return hand.stream()
					.filter(card -> card.getColor() == Color.HEARTS)
					.toList();
		}

		return hand;
	}
}
