package com.lafi.cardgame.nazdarbaby.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BotSimulatorTest {

	private static final Card CARD_PLACEHOLDER = CardProvider.CARD_PLACEHOLDER;

	private final List<User> bots = List.of(new User("user1", true), new User("user2", true), new User("user3", true));

	@Mock
	private Game game;
	private List<Card> deckOfCards;
	private BotSimulator botSimulator;

	@BeforeEach
	void setUp() {
		CardProvider cardProvider = new CardProvider(bots.size());
		deckOfCards = cardProvider.getShuffledDeckOfCards();

		botSimulator = new BotSimulator(game);
		botSimulator.setUsers(bots);
		botSimulator.setDeckOfCardsSize(deckOfCards.size());

		lenient().doReturn(cardProvider).when(game).getCardProvider();
	}

	@Nested
	class IsHighestRemainingColorTest {

		@Test
		void heartsVsSpades_returnFalse() {
			List<Card> cardPlaceholders = List.of(getHeart(7), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			Card card = getSpade(14);
			boolean highestRemainingHeart = isHighestRemainingColor(card);

			assertThat(highestRemainingHeart).isFalse();
		}

		@Test
		void spadesVsDiamonds_returnFalse() {
			List<Card> cardPlaceholders = List.of(getSpade(7), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			Card card = getCard(14, Color.DIAMONDS);
			boolean highestRemainingHeart = isHighestRemainingColor(card);

			assertThat(highestRemainingHeart).isFalse();
		}

		@Test
		void jackVsKing_returnFalse() {
			List<Card> cardPlaceholders = List.of(getSpade(11), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			Card card = getSpade(13);
			boolean highestRemainingHeart = isHighestRemainingColor(card);

			assertThat(highestRemainingHeart).isFalse();
		}

		@Test
		void jackVsAce_returnTrue() {
			List<Card> cardPlaceholders = List.of(getSpade(11), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			Card card = getSpade(14);
			boolean highestRemainingHeart = isHighestRemainingColor(card);

			assertThat(highestRemainingHeart).isTrue();
		}

		@Test
		void winningCardIsHigher_returnFalse() {
			List<Card> cardPlaceholders = List.of(getSpade(12), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			Card card = getSpade(11);
			boolean highestRemainingHeart = isHighestRemainingColor(card);

			assertThat(highestRemainingHeart).isFalse();
		}

		@Test
		void notAllHigherCardsAreKnown_returnFalse() {
			List<Card> cardPlaceholders = List.of(getHeart(12), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			Card card = getHeart(13);
			boolean highestRemainingHeart = isHighestRemainingColor(card);

			assertThat(highestRemainingHeart).isFalse();
		}

		@Test
		void aceOfHearts_returnTrue() {
			List<Card> cardPlaceholders = List.of(getHeart(12), getSpade(14), CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			Card card = getHeart(14);
			boolean highestRemainingHeart = isHighestRemainingColor(card);

			assertThat(highestRemainingHeart).isTrue();
		}

		@Test
		void twoHighestCardsInHand_returnTrue() {
			List<Card> cardPlaceholders = List.of(getSpade(12), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			Card ace = getSpade(14);
			Card king = getSpade(13);
			boolean highestRemainingHeart = botSimulator.isHighestRemainingColor(List.of(king, ace), king);

			assertThat(highestRemainingHeart).isTrue();
		}

		@Test
		void highestRemainingCard_returnTrue() {
			rememberCards(getHearts(13, 14));
			List<Card> cardPlaceholders = List.of(getSpade(14), getHeart(11), CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);
			doReturn(1).when(game).getWinnerIndex();

			Card card = getHeart(12);
			boolean highestRemainingHeart = isHighestRemainingColor(card);

			assertThat(highestRemainingHeart).isTrue();
		}
	}

	@Nested
	class GuessExpectedTakesTest {

		@Test
		void othersCanHaveHigherHeart_returnHalf() {
			rememberCards(getHearts(10, 11, 12, 13, 14));
			List<Card> cardPlaceholders = List.of(getHeart(7), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			User bot = bots.get(0);
			bot.addCard(getHeart(8));

			botSimulator.setActiveUser(bot);
			botSimulator.removeColorsForOtherUsers(bot.getCards());

			double expectedTakes = botSimulator.guessExpectedTakes(bot);
			assertThat(expectedTakes).isEqualTo(0.5);
		}

		@Test
		void othersCanHaveLowerHeart_returnOne() {
			rememberCards(getHearts(10, 11, 12, 13, 14));
			List<Card> cardPlaceholders = List.of(getHeart(7), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			User bot = bots.get(0);
			bot.addCard(getHeart(9));

			botSimulator.setActiveUser(bot);
			botSimulator.removeColorsForOtherUsers(bot.getCards());

			double expectedTakes = botSimulator.guessExpectedTakes(bot);
			assertThat(expectedTakes).isEqualTo(1);
		}

		@Test
		void haveToPlayLastLowerHeart_returnZero() {
			rememberCards(getHearts(9, 10, 11, 12, 13, 14));
			List<Card> cardPlaceholders = List.of(getHeart(8), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			User bot = bots.get(0);
			bot.addCard(getHeart(7));

			botSimulator.setActiveUser(bot);
			botSimulator.removeColorsForOtherUsers(bot.getCards());

			double expectedTakes = botSimulator.guessExpectedTakes(bot);
			assertThat(expectedTakes).isEqualTo(0.5);
		}
	}

	@Nested
	class AreOthersWithoutHeartsTest {

		@Test
		void higherHeartOnTheTable_returnFalse() {
			rememberCards(getHearts(9, 10, 11, 12, 13, 14));
			List<Card> cardPlaceholders = List.of(getHeart(8), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			User bot = bots.get(0);
			Card card = getHeart(7);
			bot.addCard(card);

			botSimulator.setActiveUser(bot);
			botSimulator.removeColorsForOtherUsers(bot.getCards());

			boolean areOthersWithoutHearts = botSimulator.areOthersWithoutHearts(bot, card);
			assertThat(areOthersWithoutHearts).isFalse();
		}

		@Test
		void othersCanHaveHearts_returnFalse() {
			rememberCards(getHearts(10, 11, 12, 13, 14));
			List<Card> cardPlaceholders = List.of(getSpade(8), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			User bot = bots.get(0);
			Card card = getHeart(7);
			bot.addCard(card);

			botSimulator.setActiveUser(bot);
			botSimulator.removeColorsForOtherUsers(bot.getCards());

			boolean areOthersWithoutHearts = botSimulator.areOthersWithoutHearts(bot, card);
			assertThat(areOthersWithoutHearts).isFalse();
		}

		@Test
		void othersWithoutHearts_returnTrue() {
			rememberCards(getHearts(9, 10, 11, 12, 13, 14));
			List<Card> cardPlaceholders = List.of(getHeart(7), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			User bot = bots.get(0);
			Card card = getHeart(8);
			bot.addCard(card);

			botSimulator.setActiveUser(bot);
			botSimulator.removeColorsForOtherUsers(bot.getCards());

			boolean areOthersWithoutHearts = botSimulator.areOthersWithoutHearts(bot, card);
			assertThat(areOthersWithoutHearts).isTrue();
		}
	}

	private void rememberCards(List<Card> cards) {
		botSimulator.setCardPlaceholders(cards);
		botSimulator.setActiveUser(null);
	}

	private boolean isHighestRemainingColor(Card card) {
		return botSimulator.isHighestRemainingColor(List.of(card), card);
	}

	private List<Card> getHearts(int... values) {
		return Arrays.stream(values).mapToObj(this::getHeart).toList();
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
