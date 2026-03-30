package com.lafi.cardgame.nazdarbaby.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CardProvider {

    public static final Card CARD_PLACEHOLDER = Card.createCardPlaceholder();

    private static final Set<Card> BIG_DECK_OF_CARDS;
    private static final Set<Card> SMALL_DECK_OF_CARDS;

    private final Set<Card> deck;

    static {
        var big = new HashSet<Card>(52);

        for (var value : List.of("2", "3", "4", "5", "6", "7", "8", "9", "10", Card.JACK, Card.QUEEN, Card.KING, Card.ACE)) {
            for (var color : Color.values()) {
                var card = new Card(value, color);
                big.add(card);
            }
        }

        BIG_DECK_OF_CARDS = Set.copyOf(big);
        SMALL_DECK_OF_CARDS = BIG_DECK_OF_CARDS.stream()
                .filter(card -> card.getValue() >= 7)
                .collect(Collectors.toSet());
    }

    public CardProvider(int playerCount) {
        deck = playerCount > 3 ? BIG_DECK_OF_CARDS : SMALL_DECK_OF_CARDS;
    }

    public List<Card> getShuffledDeckOfCards() {
        var cards = new ArrayList<>(deck);
        Collections.shuffle(cards);
        return cards;
    }

    public int getDeckOfCardsSize() {
        return deck.size();
    }
}
