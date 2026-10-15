package com.lafi.cardgame.nazdarbaby.provider;

import static com.lafi.cardgame.nazdarbaby.card.TestCards.getCard;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.mcts.TrickEvaluator;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
	class PredictionChancesForCurrentUserTest {

		@Test
		void othersCanHaveHigherHeart_onlyUnseenCardsCount() {
			givenBotAsking(getHearts(10, 11, 12, 13, 14), getHeart(7), getHeart(8));

			// The 8♥ loses only to the 9♥, the one higher heart among the 25 unseen cards
			assertThat(botSimulator.predictionChancesForCurrentUser().get(1)).isCloseTo(23.0 / 25, within(0.07));
		}

		@Test
		void othersCanHaveLowerHeart_highestHeartLeftAlwaysTakesATrick() {
			givenBotAsking(getHearts(10, 11, 12, 13, 14), getHeart(7), getHeart(9), getCard(7, Color.DIAMONDS));

			Map<Integer, Double> chances = botSimulator.predictionChancesForCurrentUser();
			assertThat(chances.get(0)).isZero();
			assertThat(chances.get(1)).isGreaterThan(chances.get(2));
		}

		@Test
		void everyHigherHeartGone_alwaysTakesTheTrick() {
			givenBotAsking(getHearts(9, 10, 11, 12, 13, 14), getHeart(8), getHeart(7));

			assertThat(botSimulator.predictionChancesForCurrentUser()).containsExactly(entry(0, 0.0), entry(1, 1.0));
		}

		@Test
		void humanOnTheirTurn_simulatesFromTheirOwnSeat() {
			User me = new User("me", takeoverCodes);
			givenHumanAsking(me, List.of(me, new User("other", takeoverCodes), bots.getFirst()), me);
			me.addCard(getCard(14, Color.DIAMONDS));

			// Led, the ace loses only if one of the 2 cards dealt from the 31 unseen is a heart
			assertThat(botSimulator.predictionChancesForCurrentUser().get(1)).isCloseTo(253.0 / 465, within(0.07));
		}

		@Test
		void humanWaitingForTheirTurn_simulatesFromTheirOwnSeat() {
			User activeHuman = new User("active", takeoverCodes);
			User me = new User("me", takeoverCodes);
			givenHumanAsking(me, List.of(activeHuman, me, bots.getFirst()), activeHuman);
			me.addCard(getCard(14, Color.DIAMONDS));

			// Second to play, the ace wins only if a diamond is led and the last player has no heart
			assertThat(botSimulator.predictionChancesForCurrentUser().get(1))
					.isCloseTo(7.0 / 31 * 22 / 30, within(0.07));
		}

		@Test
		void humanWaitingForTheirTurn_simulatesEveryoneHoldingCards() {
			User activeHuman = new User("active", takeoverCodes);
			User me = new User("me", takeoverCodes);
			givenHumanAsking(me, List.of(activeHuman, me, bots.getFirst()), activeHuman);
			me.addCard(getHeart(14));
			me.addCard(getHeart(13));

			// The two top trumps win both tricks whenever every seat holds a full hand
			assertThat(botSimulator.predictionChancesForCurrentUser())
					.containsExactly(entry(0, 0.0), entry(1, 0.0), entry(2, 1.0));
		}

		@Test
		void humanAskingDuringABotsTurn_simulatesTheirOwnCards() {
			User activeBot = bots.getFirst();
			User me = new User("me", takeoverCodes);
			givenHumanAsking(me, List.of(activeBot, me, bots.get(1)), activeBot);
			activeBot.addCard(getCard(8, Color.CLUBS));
			me.addCard(getHeart(7));

			// The lone 7♥ wins unless one of the 2 cards dealt from the 31 unseen is a higher heart
			assertThat(botSimulator.predictionChancesForCurrentUser().get(1)).isCloseTo(276.0 / 465, within(0.07));
		}

		@Test
		void humanPredictingLast_leavesOutTheForbiddenTakes() {
			User me = new User("me", takeoverCodes);
			givenHumanAsking(me, List.of(bots.get(0), bots.get(1), me), me);
			bots.get(0).setExpectedTakes(1);
			bots.get(1).setExpectedTakes(0);
			me.addCard(getHeart(14));
			me.addCard(getCard(7, Color.DIAMONDS));

			assertThat(botSimulator.predictionChancesForCurrentUser()).containsOnlyKeys(0, 2);
		}

		@Test
		void botPredictingMeanwhile_hintReadsAfterItsMove() throws InterruptedException {
			User me = new User("me", takeoverCodes);
			givenHumanAsking(me, List.of(bots.get(0), bots.get(1), me), bots.get(1));
			bots.get(0).setExpectedTakes(1);
			me.addCard(getHeart(14));
			me.addCard(getHeart(13));
			me.addCard(getCard(7, Color.DIAMONDS));

			AtomicReference<Map<Integer, Double>> chances = new AtomicReference<>();
			Thread hint;
			synchronized (game) {
				hint = Thread.ofPlatform().start(() -> chances.set(botSimulator.predictionChancesForCurrentUser()));
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				while (hint.getState() != Thread.State.BLOCKED && hint.isAlive() && System.nanoTime() < deadline) {
					Thread.onSpinWait();
				}
				// Bots predict inside Game's monitor
				bots.get(1).setExpectedTakes(1);
			}
			hint.join();

			// 1 + 1 + 1 would match the 3 tricks
			assertThat(chances.get()).containsOnlyKeys(0, 2, 3);
		}

		@Test
		void predictionMadeWhileReading_countsAsNotYetMade() {
			User me = new User("me", takeoverCodes);
			User predictingNow = spy(new User("bot"));
			givenHumanAsking(me, List.of(bots.get(0), predictingNow, me), predictingNow);
			bots.get(0).setExpectedTakes(1);
			doReturn(null).doReturn(2).when(predictingNow).getExpectedTakes();
			me.addCard(getHeart(14));
			me.addCard(getHeart(13));
			me.addCard(getCard(7, Color.DIAMONDS));

			// Not made yet, so nothing is forbidden; mixing it with a later read would forbid 2
			assertThat(botSimulator.predictionChancesForCurrentUser()).containsOnlyKeys(0, 1, 2, 3);
			verify(predictingNow).getExpectedTakes();
		}

		private void givenBotAsking(List<Card> playedOutCards, Card cardOnTable, Card... hand) {
			rememberCards(playedOutCards);
			botSimulator.setCardPlaceholders(List.of(cardOnTable, CARD_PLACEHOLDER, CARD_PLACEHOLDER));

			User bot = bots.getFirst();
			Arrays.stream(hand).forEach(bot::addCard);

			botSimulator.setActiveUser(bot);
			botSimulator.removeColorsForOtherUsers(bot.getCards());
			givenCurrentUser(bot);
		}

		private void givenHumanAsking(User me, List<User> users, User activeUser) {
			botSimulator.setUsers(users);
			givenEmptyTable();
			botSimulator.setActiveUser(activeUser);
			givenCurrentUser(me);
		}
	}

	@Nested
	class PrepareMoveTest {

		@Test
		void humanToMove_noBotMove() {
			User human = new User("human", takeoverCodes);
			botSimulator.setUsers(List.of(bots.get(0), human, bots.get(1)));
			botSimulator.setActiveUser(human);

			assertThat(botSimulator.prepareMove()).isNull();
		}

		@Test
		void betweenTricks_noBotMove() {
			botSimulator.setActiveUser(null);

			assertThat(botSimulator.prepareMove()).isNull();
		}

		@Test
		void botToMove_predictsForThatBot() {
			User bot = bots.get(1);
			bot.addCard(getCard(14, Color.HEARTS));
			botSimulator.setCardPlaceholders(new CopyOnWriteArrayList<>(List.of(CARD_PLACEHOLDER, CARD_PLACEHOLDER, CARD_PLACEHOLDER)));
			botSimulator.setActiveUser(bot);

			playBotMove(botSimulator);

			assertThat(bots).extracting(User::getExpectedTakes).containsExactly(null, 1, null);
			verify(game).afterActiveUserSetExpectedTakes();
		}
	}

	@Nested
	class PlayBotMoveTest {

		@Test
		void lastToPlay_beatsTheCardAlreadyOnTheTable() {
			// The bot still needs 2 of its 3 tricks and only the queen beats the 10♠ led. Simulating
			// without the table it believes it leads, and plays the 7♠ in 300 of 300 moves.
			List<Card> table = List.of(getCard(10, Color.SPADES), getCard(7, Color.CLUBS));
			List<Card> hand = List.of(getCard(10, Color.DIAMONDS), getCard(12, Color.SPADES), getCard(7, Color.SPADES));

			assertThat(timesOutOf5(getCard(12, Color.SPADES), () -> playLastToTable(table, hand)))
					.isGreaterThanOrEqualTo(4);
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

			BotSimulator simulator = newSimulator(users);
			List<Card> placeholders = new CopyOnWriteArrayList<>(List.of(table.get(0), table.get(1), CARD_PLACEHOLDER));
			simulator.setCardPlaceholders(placeholders);
			simulator.setActiveUser(bot);

			playBotMove(simulator);

			return placeholders.get(2);
		}

		@Test
		void leading_neverExpectsOpponentsToHoldItsOwnCards() {
			// Needing 1 of the last 3 tricks, it leads the 8♣ to lose and keeps both spades. Believing an
			// opponent could hold its own A♠ or K♠, it cashes the A♠ in 95 of 100 moves instead.
			List<List<Card>> hands = List.of(
					List.of(getCard(12, Color.DIAMONDS), getCard(11, Color.CLUBS), getCard(9, Color.SPADES),
							getCard(13, Color.DIAMONDS)),
					List.of(getHeart(9), getCard(8, Color.SPADES), getHeart(7), getHeart(12)),
					List.of(getHeart(13), getCard(8, Color.CLUBS), getCard(14, Color.SPADES),
							getCard(13, Color.SPADES)));

			assertThat(timesOutOf5(getCard(8, Color.CLUBS), () -> leadAfterFirstTrick(hands, 4, 4, 2)))
					.isGreaterThanOrEqualTo(4);
		}

		@Test
		void leading_usesWhatOpponentsShowedTheyLack() {
			// The third player answered the Q♦ with a spade, so holds no diamonds and no hearts. Needing 2 of
			// the last 3 tricks, the bot leads the A♥; ignoring that, it leads the 9♣ in 100 of 100 moves.
			List<List<Card>> hands = List.of(
					List.of(getCard(12, Color.DIAMONDS), getCard(9, Color.DIAMONDS), getHeart(8),
							getCard(7, Color.SPADES)),
					List.of(getHeart(9), getCard(9, Color.CLUBS), getHeart(14), getCard(14, Color.SPADES)),
					List.of(getCard(8, Color.SPADES), getCard(8, Color.CLUBS), getCard(12, Color.SPADES),
							getCard(9, Color.SPADES)));

			assertThat(timesOutOf5(getHeart(14), () -> leadAfterFirstTrick(hands, 1, 3, 4)))
					.isGreaterThanOrEqualTo(4);
		}

		// The first card of every hand makes the first trick, which the bots see; its winner leads the second
		private Card leadAfterFirstTrick(List<List<Card>> hands, int... predictions) {
			List<User> users = new CopyOnWriteArrayList<>();
			for (int i = 0; i < hands.size(); i++) {
				User user = new User("bot" + i);
				hands.get(i).forEach(user::addCard);
				user.setExpectedTakes(predictions[i]);
				users.add(user);
			}

			BotSimulator simulator = newSimulator(users);

			List<Card> firstTrick = new CopyOnWriteArrayList<>();
			for (User user : users) {
				firstTrick.add(user.getCards().getFirst());
				user.getCards().set(0, CARD_PLACEHOLDER);
			}
			simulator.setCardPlaceholders(firstTrick);
			simulator.setActiveUser(null);

			int winnerIndex = TrickEvaluator.getWinningIndex(firstTrick);
			users.get(winnerIndex).increaseActualTakes();
			Collections.rotate(users, -winnerIndex);

			List<Card> secondTrick = new CopyOnWriteArrayList<>(
					List.of(CARD_PLACEHOLDER, CARD_PLACEHOLDER, CARD_PLACEHOLDER));
			simulator.setCardPlaceholders(secondTrick);
			simulator.setActiveUser(users.getFirst());

			playBotMove(simulator);

			return secondTrick.getFirst();
		}

		private BotSimulator newSimulator(List<User> users) {
			BotSimulator simulator = new BotSimulator(game);
			simulator.setUsers(users);
			simulator.setDeckOfCardsSize(deckOfCards.size());
			return simulator;
		}

		private static int timesOutOf5(Card card, Supplier<Card> move) {
			int times = 0;
			for (int i = 0; i < 5; i++) {
				if (move.get().equals(card)) {
					times++;
				}
			}
			return times;
		}

		@Test
		void weakSingleCard_forbiddenZero_predictsOne() {
			givenEmptyTable();
			bots.get(0).setExpectedTakes(1);
			bots.get(1).setExpectedTakes(0);
			User bot = bots.get(2);
			bot.addCard(getCard(7, Color.CLUBS));
			botSimulator.setActiveUser(bot);

			playBotMove(botSimulator);

			// Predicting last for the one trick, 0 is forbidden, so even a lone 7♣ predicts 1
			assertThat(bot.getExpectedTakes()).isEqualTo(1);
		}

		@Test
		void aceOfHearts_singleCard_forbiddenOne_predictsZero() {
			givenEmptyTable();
			User bot = bots.getFirst();
			bot.addCard(getCard(14, Color.HEARTS));
			botSimulator.setActiveUser(bot);
			doReturn(true).when(game).isLastUserWithInvalidExpectedTakes(1);

			playBotMove(botSimulator);

			// Single card — prediction 1 is forbidden, 0 is the only alternative
			assertThat(bot.getExpectedTakes()).isZero();
		}

		@Test
		void aceOfHearts_twoCards_forbiddenOne_predictsTwo() {
			givenEmptyTable();
			User bot = bots.getFirst();
			bot.addCard(getCard(14, Color.HEARTS));
			bot.addCard(getCard(7, Color.DIAMONDS));
			bots.get(1).setExpectedTakes(1);
			bots.get(2).setExpectedTakes(0);
			botSimulator.setActiveUser(bot);

			playBotMove(botSimulator);

			// Predicting last, 1 would make the sum 2 of 2 tricks; the ace of hearts always wins, so 0 is hopeless
			assertThat(bot.getExpectedTakes()).isEqualTo(2);
		}
	}

	private void givenEmptyTable() {
		botSimulator.setCardPlaceholders(List.of(CARD_PLACEHOLDER, CARD_PLACEHOLDER, CARD_PLACEHOLDER));
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


	private static void playBotMove(BotSimulator simulator) {
		simulator.prepareMove().search().run();
	}
}
