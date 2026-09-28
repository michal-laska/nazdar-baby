package com.lafi.cardgame.nazdarbaby.provider;

import static com.lafi.cardgame.nazdarbaby.card.TestCards.getCard;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

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
	private final Set<Integer> takeoverCodes = new HashSet<>();

	@Mock
	private Game game;
	@Mock
	private UserProvider userProvider;
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
	class GuessExpectedTakesForCurrentUserTest {

		@Test
		void othersCanHaveHigherHeart_returnAtMostOne() {
			rememberCards(getHearts(10, 11, 12, 13, 14));
			List<Card> cardPlaceholders = List.of(getHeart(7), CARD_PLACEHOLDER, CARD_PLACEHOLDER);
			botSimulator.setCardPlaceholders(cardPlaceholders);

			User bot = bots.getFirst();
			bot.addCard(getHeart(8));

			botSimulator.setActiveUser(bot);
			botSimulator.removeColorsForOtherUsers(bot.getCards());
			givenCurrentUser(bot);

			double expectedTakes = botSimulator.guessExpectedTakesForCurrentUser();
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
			givenCurrentUser(bot);

			double expectedTakes = botSimulator.guessExpectedTakesForCurrentUser();
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
			givenCurrentUser(bot);

			double expectedTakes = botSimulator.guessExpectedTakesForCurrentUser();
			assertThat(expectedTakes).isBetween(0.0, 1.0);
		}

		@Test
		void humanOnTheirTurn_guessesFromTheirOwnSeat() {
			User me = new User("me", takeoverCodes);
			givenHumanAsking(me, List.of(me, new User("other", takeoverCodes), bots.getFirst()), me);
			me.addCard(getHeart(7));

			// A lone low trump is not worth a trick for the player who leads (seat 0)
			assertThat(botSimulator.guessExpectedTakesForCurrentUser()).isZero();
		}

		@Test
		void humanWaitingForTheirTurn_guessesFromTheirOwnSeat() {
			User activeHuman = new User("active", takeoverCodes);
			User me = new User("me", takeoverCodes);
			givenHumanAsking(me, List.of(activeHuman, me, bots.getFirst()), activeHuman);
			me.addCard(getHeart(7));

			// Not leading, the same lone trump can trump a suit the player is void in
			assertThat(botSimulator.guessExpectedTakesForCurrentUser()).isEqualTo(1.0);
		}

		@Test
		void humanWaitingForTheirTurn_simulatesEveryoneHoldingCards() {
			User activeHuman = new User("active", takeoverCodes);
			User me = new User("me", takeoverCodes);
			givenHumanAsking(me, List.of(activeHuman, me, bots.getFirst()), activeHuman);
			me.addCard(getHeart(14));
			me.addCard(getHeart(13));

			// The two top trumps win both tricks whenever every seat holds a full hand
			assertThat(botSimulator.guessExpectedTakesForCurrentUser()).isEqualTo(2.0);
		}

		@Test
		void humanAskingDuringABotsTurn_guessesFromTheirOwnCards() {
			User activeBot = bots.getFirst();
			User me = new User("me", takeoverCodes);
			givenHumanAsking(me, List.of(activeBot, me, bots.get(1)), activeBot);
			activeBot.addCard(getCard(8, Color.CLUBS));
			me.addCard(getHeart(7));

			assertThat(botSimulator.guessExpectedTakesForCurrentUser()).isEqualTo(1.0);
		}

		private void givenHumanAsking(User me, List<User> users, User activeUser) {
			botSimulator.setUsers(users);
			botSimulator.setCardPlaceholders(List.of(CARD_PLACEHOLDER, CARD_PLACEHOLDER, CARD_PLACEHOLDER));
			botSimulator.setActiveUser(activeUser);
			givenCurrentUser(me);
		}
	}

	@Nested
	class TryBotMoveTest {

		@Test
		void lastToPlay_beatsTheCardAlreadyOnTheTable() {
			// The bot still needs 2 of its 3 tricks and only the queen beats the 10♠ led. Simulating
			// without the table it believes it leads, and plays the 7♠ in 300 of 300 moves.
			List<Card> table = List.of(getCard(10, Color.SPADES), getCard(7, Color.CLUBS));
			List<Card> hand = List.of(getCard(10, Color.DIAMONDS), getCard(12, Color.SPADES), getCard(7, Color.SPADES));

			int queenPlays = 0;
			for (int i = 0; i < 5; i++) {
				if (playLastToTable(table, hand).equals(getCard(12, Color.SPADES))) {
					queenPlays++;
				}
			}

			assertThat(queenPlays).isGreaterThanOrEqualTo(4);
		}

		private Card playLastToTable(List<Card> table, List<Card> hand) {
			List<User> users = List.of(new User("opponent1"), new User("opponent2"), new User("bot"));
			List<Card> otherCards = new ArrayList<>(deckOfCards);
			otherCards.removeAll(table);
			otherCards.removeAll(hand);
			for (int i = 0; i < 2; i++) {
				users.get(i).setExpectedTakes(3);
				users.get(i).addCard(CARD_PLACEHOLDER);
				users.get(i).addCard(otherCards.get(2 * i));
				users.get(i).addCard(otherCards.get(2 * i + 1));
			}
			User bot = users.get(2);
			bot.setExpectedTakes(2);
			hand.forEach(bot::addCard);

			BotSimulator simulator = new BotSimulator(game);
			simulator.setUsers(users);
			simulator.setDeckOfCardsSize(deckOfCards.size());
			List<Card> placeholders = new CopyOnWriteArrayList<>(List.of(table.get(0), table.get(1), CARD_PLACEHOLDER));
			simulator.setCardPlaceholders(placeholders);
			simulator.setActiveUser(bot);

			simulator.tryBotMove();

			return placeholders.get(2);
		}

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
			bots.get(1).setExpectedTakes(1);
			bots.get(2).setExpectedTakes(0);
			botSimulator.setActiveUser(bot);

			botSimulator.tryBotMove();

			// Predicting last, 1 would make the sum 2 of 2 tricks; the ace of hearts always wins, so 0 is hopeless
			assertThat(bot.getExpectedTakes()).isEqualTo(2);
		}
	}

	private void givenCurrentUser(User user) {
		doReturn(userProvider).when(game).getUserProvider();
		doReturn(user).when(userProvider).getCurrentUser();
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

}
