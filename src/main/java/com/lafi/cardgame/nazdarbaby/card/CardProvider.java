package com.lafi.cardgame.nazdarbaby.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CardProvider {

	public static final Card CARD_PLACEHOLDER = new CardPlaceholder();

	private static final Set<Card> BIG_DECK_OF_CARDS = new HashSet<>(52);
	private static final Set<Card> SMALL_DECK_OF_CARDS = new HashSet<>(32);

	static {
		loadCards();
	}

	private final List<Card> deckOfCards;

	public CardProvider(int userCount) {
		deckOfCards = userCount > 3 ? new ArrayList<>(BIG_DECK_OF_CARDS) : new ArrayList<>(SMALL_DECK_OF_CARDS);
	}

	public List<Card> getShuffledDeckOfCards() {
		Collections.shuffle(deckOfCards);
		return new ArrayList<>(deckOfCards);
	}

	public int getDeckOfCardsSize() {
		return deckOfCards.size();
	}

	private static synchronized void loadCards() {
		if (!BIG_DECK_OF_CARDS.isEmpty()) {
			return;
		}

		List<String> values = List.of("2", "3", "4", "5", "6", "7", "8", "9", "10", Card.JACK, Card.QUEEN, Card.KING, Card.ACE);

		for (String value : values) {
			for (Color color : Color.values()) {
				Card card = new Card(value, color);

				if (card.getValue() >= 7) {
					SMALL_DECK_OF_CARDS.add(card);
				}
				BIG_DECK_OF_CARDS.add(card);
			}
		}
	}
}
