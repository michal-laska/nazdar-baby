package com.lafi.cardgame.nazdarbaby.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CardTest {

	private List<Card> deckOfCards;

	@BeforeEach
	void setUp() {
		CardProvider cardProvider = new CardProvider(3);
		deckOfCards = cardProvider.getShuffledDeckOfCards();
	}

	@Test
	void isHigherThan_placeholderVsAnyCard_returnFalse() {
		Card cardPlaceholder = CardProvider.CARD_PLACEHOLDER;
		Card anyCard = getCard(7, Color.SPADES);

		boolean higherThan = cardPlaceholder.isHigherThan(anyCard);

		assertThat(higherThan).isFalse();
	}

	@Test
	void isHigherThan_anyCardVsPlaceholder_returnFalse() {
		Card cardPlaceholder = CardProvider.CARD_PLACEHOLDER;
		Card anyCard = getCard(7, Color.SPADES);

		boolean higherThan = anyCard.isHigherThan(cardPlaceholder);

		assertThat(higherThan).isTrue();
	}

	@Test
	void isHigherThan_lowerVsHigher_returnFalse() {
		Card card1 = getCard(7, Color.SPADES);
		Card card2 = getCard(8, Color.SPADES);

		boolean higherThan = card1.isHigherThan(card2);

		assertThat(higherThan).isFalse();
	}

	@Test
	void isHigherThan_higherVsLower_returnFalse() {
		Card card1 = getCard(7, Color.SPADES);
		Card card2 = getCard(8, Color.SPADES);

		boolean higherThan = card2.isHigherThan(card1);

		assertThat(higherThan).isTrue();
	}

	@Test
	void isHigherThan_spadeVsHeart_returnFalse() {
		Card card1 = getCard(14, Color.SPADES);
		Card card2 = getCard(7, Color.HEARTS);

		boolean higherThan = card1.isHigherThan(card2);

		assertThat(higherThan).isFalse();
	}

	@Test
	void isHigherThan_heartVsSpade_returnFalse() {
		Card card1 = getCard(14, Color.SPADES);
		Card card2 = getCard(7, Color.HEARTS);

		boolean higherThan = card2.isHigherThan(card1);

		assertThat(higherThan).isTrue();
	}

	private Card getCard(int value, Color color) {
		return deckOfCards.stream()
				.filter(card -> card.getValue() == value && card.getColor() == color)
				.findFirst()
				.get();
	}
}