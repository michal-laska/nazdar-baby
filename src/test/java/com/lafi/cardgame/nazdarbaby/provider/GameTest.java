package com.lafi.cardgame.nazdarbaby.provider;

import static com.lafi.cardgame.nazdarbaby.card.TestCards.getCard;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.doReturn;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.point.PointProvider;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameTest {

	private final List<User> bots = List.of(new User("bot1"), new User("bot2"), new User("bot3"));
	private final PointProvider pointProvider = new PointProvider();

	@Mock
	private UserProvider userProvider;

	@Nested
	class CalculatePointsTest {

		@Test
		void endOfSet_losersPayWhatTheWinnersGain() {
			Game game = givenStartedGame();

			int winCount = playSetsUntilSomebodyWins(game);
			// The next set resets predictions, so the winners have to be known before it starts
			Set<User> winners = bots.stream().filter(User::isWinner).collect(Collectors.toSet());
			Map<User, Float> setPoints = startNextSetAndCollectPoints(game);

			float winnerPoints = pointProvider.getWinnerPoints(bots.size(), winCount);
			List<Float> loserPoints = new ArrayList<>();
			for (User bot : bots) {
				if (winners.contains(bot)) {
					assertThat(setPoints.get(bot)).as(bot.getName()).isCloseTo(winnerPoints, within(1e-4f));
				} else {
					loserPoints.add(setPoints.get(bot));
				}
			}

			assertThat(loserPoints).as("every loser pays the same").containsOnly(loserPoints.getFirst());
			assertThat((float) setPoints.values().stream().mapToDouble(Float::doubleValue).sum())
					.as("the set is zero-sum")
					.isCloseTo(0f, within(1e-4f));
		}
	}

	@Nested
	class GetWinningIndexTest {

		@Test
		void highestCardOfTheLedSuit_wins() {
			Game game = givenStartedGame();
			givenTable(game, getCard(13, Color.CLUBS), getCard(7, Color.CLUBS), getCard(14, Color.DIAMONDS));

			assertThat(game.getWinningIndex()).isZero();
		}

		@Test
		void trump_beatsTheLedSuit() {
			Game game = givenStartedGame();
			givenTable(game, getCard(14, Color.SPADES), getCard(13, Color.SPADES), getCard(7, Color.HEARTS));

			assertThat(game.getWinningIndex()).isEqualTo(2);
		}

		@Test
		void seatThatHasNotPlayed_neverWins() {
			Game game = givenStartedGame();
			givenTable(game, getCard(9, Color.SPADES), CardProvider.CARD_PLACEHOLDER, CardProvider.CARD_PLACEHOLDER);

			assertThat(game.getWinningIndex()).isZero();
		}
	}

	private static void givenTable(Game game, Card... cards) {
		List<Card> table = game.getCardPlaceholders();
		for (int i = 0; i < cards.length; i++) {
			table.set(i, cards[i]);
		}
	}

	private Game givenStartedGame() {
		doReturn(new ArrayList<>(bots)).when(userProvider).getPlayingUsers();

		Game game = new Game(userProvider, pointProvider);
		game.setGameInProgress(true);

		return game;
	}

	private int playSetsUntilSomebodyWins(Game game) {
		while (true) {
			while (!game.isEndOfSet()) {
				game.changeActiveUser();
			}

			int winCount = (int) bots.stream().filter(User::isWinner).count();
			if (winCount > 0) {
				return winCount;
			}

			game.changeActiveUser();
		}
	}

	private Map<User, Float> startNextSetAndCollectPoints(Game game) {
		Map<User, Float> pointsBefore = new HashMap<>();
		bots.forEach(bot -> pointsBefore.put(bot, bot.getPoints()));

		game.changeActiveUser();

		Map<User, Float> setPoints = new HashMap<>();
		bots.forEach(bot -> setPoints.put(bot, bot.getPoints() - pointsBefore.get(bot)));
		return setPoints;
	}
}
