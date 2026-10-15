package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.ArrayList;
import java.util.List;

public final class TrickEvaluator {

	private TrickEvaluator() {
	}

	/**
	 * The cards on a table that have been played, without the placeholders of the seats still to play.
	 */
	public static List<Card> playedCards(List<Card> table) {
		List<Card> playedCards = new ArrayList<>();
		for (Card card : table) {
			if (card.isPlaceholder()) {
				break;
			}
			playedCards.add(card);
		}
		return playedCards;
	}

	/**
	 * Returns the index of the winning card in the trick. Seats still to play, the placeholders
	 * after the cards on the table, never win.
	 */
	public static int getWinningIndex(List<Card> trick) {
		Card winningCard = null;
		int winningIndex = 0;

		for (int i = 0; i < trick.size(); i++) {
			Card card = trick.get(i);
			if (winningCard == null || beats(card, winningCard)) {
				winningCard = card;
				winningIndex = i;
			}
		}

		return winningIndex;
	}

	/**
	 * Whether a card played after the trick's winning card takes the trick from it.
	 */
	static boolean beats(Card card, Card winningCard) {
		if (card.getColor() == winningCard.getColor()) {
			return card.getValue() > winningCard.getValue();
		}
		return card.getColor() == Color.HEARTS;
	}

	/**
	 * Returns legal cards that can be played from hand given the current trick.
	 *
	 * @param hand         non-placeholder cards in player's hand
	 * @param currentTrick cards played so far in current trick (may be empty)
	 */
	public static List<Card> getLegalPlays(List<Card> hand, List<Card> currentTrick) {
		Color legalColor = getLegalColor(hand, currentTrick);
		if (legalColor == null) {
			return hand;
		}

		List<Card> legalPlays = new ArrayList<>(hand.size());
		for (Card card : hand) {
			if (card.getColor() == legalColor) {
				legalPlays.add(card);
			}
		}
		return legalPlays;
	}

	/**
	 * The color the player has to play, or null when any card in hand may be played.
	 */
	static Color getLegalColor(List<Card> hand, List<Card> currentTrick) {
		if (currentTrick.isEmpty()) {
			return null;
		}

		Color leadingColor = currentTrick.getFirst().getColor();
		boolean hasHearts = false;
		for (Card card : hand) {
			if (card.getColor() == leadingColor) {
				return leadingColor;
			}
			if (card.getColor() == Color.HEARTS) {
				hasHearts = true;
			}
		}
		return hasHearts ? Color.HEARTS : null;
	}
}
