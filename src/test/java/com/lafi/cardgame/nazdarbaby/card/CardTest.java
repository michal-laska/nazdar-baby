package com.lafi.cardgame.nazdarbaby.card;

import static com.lafi.cardgame.nazdarbaby.card.TestCards.getCard;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CardTest {

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

}
