package com.lafi.cardgame.nazdarbaby.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;

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
		lenient().doReturn(cardProvider).when(game).getCardProvider();
	}

	@Test
	void isHighestRemainingColor_heartsVsSpades_returnFalse() {
		List<Card> cardPlaceholders = List.of(getHeart(7), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(0).when(game).getWinnerIndex();

		Card card = getSpade(14);
		boolean highestRemainingHeart = isHighestRemainingColor(card);

		assertThat(highestRemainingHeart).isFalse();
	}

	@Test
	void isHighestRemainingColor_spadesVsDiamonds_returnFalse() {
		List<Card> cardPlaceholders = List.of(getSpade(7), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(0).when(game).getWinnerIndex();

		Card card = getCard(14, Color.DIAMONDS);
		boolean highestRemainingHeart = isHighestRemainingColor(card);

		assertThat(highestRemainingHeart).isFalse();
	}

	@Test
	void isHighestRemainingColor_jackVsKing_returnFalse() {
		List<Card> cardPlaceholders = List.of(getSpade(11), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(0).when(game).getWinnerIndex();

		Card card = getSpade(13);
		boolean highestRemainingHeart = isHighestRemainingColor(card);

		assertThat(highestRemainingHeart).isFalse();
	}

	@Test
	void isHighestRemainingColor_jackVsAce_returnTrue() {
		List<Card> cardPlaceholders = List.of(getSpade(11), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(0).when(game).getWinnerIndex();

		Card card = getSpade(14);
		boolean highestRemainingHeart = isHighestRemainingColor(card);

		assertThat(highestRemainingHeart).isTrue();
	}

	@Test
	void isHighestRemainingColor_winningCardIsHigher_returnFalse() {
		List<Card> cardPlaceholders = List.of(getSpade(12), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(0).when(game).getWinnerIndex();

		Card card = getSpade(11);
		boolean highestRemainingHeart = isHighestRemainingColor(card);

		assertThat(highestRemainingHeart).isFalse();
	}

	@Test
	void isHighestRemainingColor_notAllHigherCardsAreKnown_returnFalse() {
		List<Card> cardPlaceholders = List.of(getHeart(12), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(0).when(game).getWinnerIndex();

		Card card = getHeart(13);
		boolean highestRemainingHeart = isHighestRemainingColor(card);

		assertThat(highestRemainingHeart).isFalse();
	}

	@Test
	void isHighestRemainingColor_aceOfHearts_returnTrue() {
		List<Card> cardPlaceholders = List.of(getHeart(12), getSpade(14), CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(0).when(game).getWinnerIndex();

		Card card = getHeart(14);
		boolean highestRemainingHeart = isHighestRemainingColor(card);

		assertThat(highestRemainingHeart).isTrue();
	}

	@Test
	void isHighestRemainingColor_twoHighestCardsInHand_returnTrue() {
		List<Card> cardPlaceholders = List.of(getSpade(12), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(0).when(game).getWinnerIndex();

		Card ace = getSpade(14);
		Card king = getSpade(13);
		boolean highestRemainingHeart = botSimulator.isHighestRemainingColor(List.of(king, ace), king);

		assertThat(highestRemainingHeart).isTrue();
	}

	@Test
	void isHighestRemainingColor_highestRemainingCard_returnTrue() {
		List<Card> cardPlaceholders = List.of(CARD_PLACEHOLDER, getHeart(14), getHeart(13));
		botSimulator.setCardPlaceholders(cardPlaceholders);
		botSimulator.setActiveUser(null);
		cardPlaceholders = List.of(getSpade(14), getHeart(11), CARD_PLACEHOLDER);
		botSimulator.setCardPlaceholders(cardPlaceholders);
		doReturn(1).when(game).getWinnerIndex();

		Card card = getHeart(12);
		boolean highestRemainingHeart = isHighestRemainingColor(card);

		assertThat(highestRemainingHeart).isTrue();
	}

	private boolean isHighestRemainingColor(Card card) {
		return botSimulator.isHighestRemainingColor(List.of(card), card);
	}

	private Card getHeart(int value) {
		return getCard(value, Color.HEARTS);
	}

	private Card getSpade(int value) {
		return getCard(value, Color.SPADES);
	}

	private Card getCard(int value, Color color) {
		return deckOfCards.stream()
				.filter(card -> card.getValue() == value && card.getColor() == color)
				.findFirst()
				.get();
	}
}
