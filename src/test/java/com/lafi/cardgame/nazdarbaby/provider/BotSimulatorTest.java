package com.lafi.cardgame.nazdarbaby.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BotSimulatorTest {

	private static final Card CARD_PLACEHOLDER = CardProvider.CARD_PLACEHOLDER;

	private final CardProvider cardProvider = new CardProvider(3);
	private final List<Card> deckOfCards = cardProvider.getShuffledDeckOfCards();

	@Mock
	private Game game;
	private BotSimulator botSimulator;

	@BeforeEach
	void setUp() {
		botSimulator = new BotSimulator(game);
	}

	@Test
	void isHighestRemainingHeart_differentColor_returnFalse() {
		Card card = getCard(11, Color.SPADES);
		boolean highestRemainingHeart = isHighestRemainingHeart(card);

		assertThat(highestRemainingHeart).isFalse();
	}

	@Test
	void isHighestRemainingHeart_winningCardIsHigher_returnFalse() {
		List<Card> cardPlaceholders = List.of(getHeart(12), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(0).when(game).getWinnerIndex();

		Card card = getHeart(11);
		boolean highestRemainingHeart = isHighestRemainingHeart(card);

		assertThat(highestRemainingHeart).isFalse();
	}

	@Test
	void isHighestRemainingHeart_notAllHigherHeartsAreKnown_returnFalse() {
		List<Card> cardPlaceholders = List.of(getHeart(12), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(0).when(game).getWinnerIndex();

		Card card = getHeart(13);
		boolean highestRemainingHeart = isHighestRemainingHeart(card);

		assertThat(highestRemainingHeart).isFalse();
	}

	@Test
	void isHighestRemainingHeart_aceOfHearts_returnTrue() {
		List<Card> cardPlaceholders = List.of(getHeart(12), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(0).when(game).getWinnerIndex();

		Card card = getHeart(14);
		boolean highestRemainingHeart = isHighestRemainingHeart(card);

		assertThat(highestRemainingHeart).isTrue();
	}

	@Test
	void isHighestRemainingHeart_twoHighestHeartsInHand_returnTrue() {
		List<Card> cardPlaceholders = List.of(getHeart(12), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(0).when(game).getWinnerIndex();

		Card ace = getHeart(14);
		Card king = getHeart(13);
		boolean highestRemainingHeart = botSimulator.isHighestRemainingHeart(List.of(king, ace), king);

		assertThat(highestRemainingHeart).isTrue();
	}

	@Test
	void isHighestRemainingHeart_highestRemainingHeart_returnTrue() {
		List<Card> cardPlaceholders = List.of(CARD_PLACEHOLDER, getHeart(14), getHeart(13));
		botSimulator.setCardPlaceholders(cardPlaceholders);
		botSimulator.setActiveUser(null);
		cardPlaceholders = List.of(getCard(14, Color.DIAMONDS), getHeart(11), CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(1).when(game).getWinnerIndex();

		Card card = getHeart(12);
		boolean highestRemainingHeart = isHighestRemainingHeart(card);

		assertThat(highestRemainingHeart).isTrue();
	}

	private boolean isHighestRemainingHeart(Card card) {
		return botSimulator.isHighestRemainingHeart(List.of(card), card);
	}

	private Card getHeart(int value) {
		return getCard(value, Color.HEARTS);
	}

	private Card getCard(int value, Color color) {
		return deckOfCards.stream()
				.filter(card -> card.getValue() == value && card.getColor() == color)
				.findFirst()
				.get();
	}
}
