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
		Card lowerCard = getCard(7, Color.SPADES);
		Card higherCard = getCard(8, Color.SPADES);

		boolean higherThan = lowerCard.isHigherThan(higherCard);

		assertThat(higherThan).isFalse();
	}

	@Test
	void isHigherThan_higherVsLower_returnTrue() {
		Card lowerCard = getCard(7, Color.SPADES);
		Card higherCard = getCard(8, Color.SPADES);

		boolean higherThan = higherCard.isHigherThan(lowerCard);

		assertThat(higherThan).isTrue();
	}

	@Test
	void isHigherThan_spadeVsHeart_returnFalse() {
		Card spade = getCard(14, Color.SPADES);
		Card heart = getCard(7, Color.HEARTS);

		boolean higherThan = spade.isHigherThan(heart);

		assertThat(higherThan).isFalse();
	}

	@Test
	void isHigherThan_heartVsSpade_returnTrue() {
		Card spade = getCard(14, Color.SPADES);
		Card heart = getCard(7, Color.HEARTS);

		boolean higherThan = heart.isHigherThan(spade);

		assertThat(higherThan).isTrue();
	}

	private Card getCard(int value, Color color) {
		return deckOfCards.stream()
				.filter(card -> card.getValue() == value && card.getColor() == color)
				.findFirst()
				.get();
	}
}
