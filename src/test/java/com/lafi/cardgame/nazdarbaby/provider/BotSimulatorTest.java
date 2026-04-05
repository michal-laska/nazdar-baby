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

	private final List<User> bots = List.of(new User("user1"), new User("user2"), new User("user3"));

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
	class GuessExpectedTakesTest {

		@Test
		void othersCanHaveHigherHeart_returnAtMostOne() {
			rememberCards(getHearts(10, 11, 12, 13, 14));
			List<Card> cardPlaceholders = List.of(getHeart(7), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			User bot = bots.getFirst();
			bot.addCard(getHeart(8));

			botSimulator.setActiveUser(bot);
			botSimulator.removeColorsForOtherUsers(bot.getCards());

			double expectedTakes = botSimulator.guessExpectedTakes();
			assertThat(expectedTakes).isBetween(0.0, 1.0);
		}

		@Test
		void othersCanHaveLowerHeart_returnOne() {
			rememberCards(getHearts(10, 11, 12, 13, 14));
			List<Card> cardPlaceholders = List.of(getHeart(7), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			User bot = bots.getFirst();
			bot.addCard(getHeart(9));
			bot.addCard(getCard(7, Color.DIAMONDS));

			botSimulator.setActiveUser(bot);
			botSimulator.removeColorsForOtherUsers(bot.getCards());

			double expectedTakes = botSimulator.guessExpectedTakes();
			// Heart 9 is highest remaining heart (wins), 7 of diamonds is weak (loses)
			assertThat(Math.round(expectedTakes)).isEqualTo(1);
		}

		@Test
		void haveToPlayLastLowerHeart_returnAtMostOne() {
			rememberCards(getHearts(9, 10, 11, 12, 13, 14));
			List<Card> cardPlaceholders = List.of(getHeart(8), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			User bot = bots.getFirst();
			bot.addCard(getHeart(7));

			botSimulator.setActiveUser(bot);
			botSimulator.removeColorsForOtherUsers(bot.getCards());

			double expectedTakes = botSimulator.guessExpectedTakes();
			assertThat(expectedTakes).isBetween(0.0, 1.0);
		}
	}

	@Nested
	class TryBotMoveTest {

		@Test
		void expectedTakes_cannotBeNegative() {
			List<Card> cardPlaceholders = List.of(CARD_PLACEHOLDER, CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);
			User bot = bots.getFirst();
			botSimulator.setActiveUser(bot);
			doReturn(true).when(game).isLastUserWithInvalidExpectedTakes(0);

			botSimulator.tryBotMove();

			assertThat(bot.getExpectedTakes()).isEqualTo(1);
		}

		@Test
		void aceOfHearts_singleCard_forbiddenOne_predictsZero() {
			List<Card> cardPlaceholders = List.of(CARD_PLACEHOLDER, CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);
			User bot = bots.getFirst();
			bot.addCard(getCard(14, Color.HEARTS));
			botSimulator.setActiveUser(bot);
			doReturn(true).when(game).isLastUserWithInvalidExpectedTakes(1);

			botSimulator.tryBotMove();

			// Single card — prediction 1 is forbidden, 0 is the only alternative
			assertThat(bot.getExpectedTakes()).isZero();
		}

		@Test
		void aceOfHearts_twoCards_forbiddenOne_predictsTwo() {
			List<Card> cardPlaceholders = List.of(CARD_PLACEHOLDER, CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);
			User bot = bots.getFirst();
			bot.addCard(getCard(14, Color.HEARTS));
			bot.addCard(getCard(7, Color.DIAMONDS));
			botSimulator.setActiveUser(bot);
			doReturn(true).when(game).isLastUserWithInvalidExpectedTakes(1);

			botSimulator.tryBotMove();

			// Two cards with ace of hearts — prediction 1 is forbidden, should go up to 2 (not down to 0)
			assertThat(bot.getExpectedTakes()).isEqualTo(2);
		}
	}

	private void rememberCards(List<Card> cards) {
		botSimulator.setCardPlaceholders(cards);
		botSimulator.setActiveUser(null);
	}

	private List<Card> getHearts(int... values) {
		return Arrays.stream(values).mapToObj(this::getHeart).toList();
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
