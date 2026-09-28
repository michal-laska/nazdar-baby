package com.lafi.cardgame.nazdarbaby.card;

import java.util.List;

public final class TestCards {

	private static final List<Card> ALL_CARDS = new CardProvider(4).getShuffledDeckOfCards();

	private TestCards() {
	}

	public static Card getCard(int value, Color color) {
		return ALL_CARDS.stream()
				.filter(card -> card.getValue() == value && card.getColor() == color)
				.findFirst()
				.orElseThrow();
	}
}
