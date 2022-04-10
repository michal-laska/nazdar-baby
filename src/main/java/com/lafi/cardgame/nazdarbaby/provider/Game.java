package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.exception.EndGameException;
import com.lafi.cardgame.nazdarbaby.points.PointProvider;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Game {

	private final BotSimulator botSimulator = new BotSimulator(this);
	private final UserProvider userProvider;
	private final PointProvider pointProvider;

	private List<User> gameUsers;
	private List<User> setUsers;
	private List<User> matchUsers;

	private CardProvider cardProvider;
	private List<Card> cardPlaceholders;

	private int setNumber;
	private int matchNumber;

	private User activeUser;
	private boolean terminatorFlagsReseted;
	private boolean gameInProgress;
	private boolean everybodyLost;

	Game(UserProvider userProvider, PointProvider pointProvider) {
		this.userProvider = userProvider;
		this.pointProvider = pointProvider;
	}

	public List<User> getSetUsers() {
		return setUsers;
	}

	public List<User> getMatchUsers() {
		return matchUsers;
	}

	public List<Card> getCardPlaceholders() {
		return cardPlaceholders;
	}

	public void setTerminatorFlagsReseted(boolean terminatorFlagsReseted) {
		this.terminatorFlagsReseted = terminatorFlagsReseted;
	}

	public boolean isGameInProgress() {
		return gameInProgress;
	}

	public void setEverybodyLost(boolean everybodyLost) {
		this.everybodyLost = everybodyLost;
	}

	public void setGameInProgress(boolean gameInProgress) {
		this.gameInProgress = gameInProgress;

		if (gameInProgress) {
			gameUsers = userProvider.getPlayingUsers();
			Collections.shuffle(gameUsers);

			cardProvider = new CardProvider(gameUsers.size());
			botSimulator.setDeckOfCardsSize(cardProvider.getDeckOfCardsSize());

			startNewGame();
		} else {
			resetNewGameFlags();
			tryResetTerminatorFlags();
		}

		resetReadyFlags();
	}

	public boolean isActiveUser() {
		User currentUser = userProvider.getCurrentUser();
		return currentUser.equals(activeUser);
	}

	public void changeActiveUser() {
		int activeUserIndex = activeUser == null ? matchUsers.size() : matchUsers.indexOf(activeUser);

		if (activeUserIndex == matchUsers.size()) {
			int winnerIndex = getWinnerIndex();

			Collections.rotate(matchUsers, -winnerIndex);
			resetActiveUser();

			startNewMatch();
		} else if (++activeUserIndex == matchUsers.size()) {
			int winnerIndex = getWinnerIndex();
			User winUser = matchUsers.get(winnerIndex);
			winUser.increaseActualTakes();

			setActiveUser(null);

			calculatePoints();
		} else {
			setActiveUser(matchUsers.get(activeUserIndex));
		}

		tryBotMove();
	}

	public void afterActiveUserSetExpectedTakes() {
		if (isLastUser()) {
			resetActiveUser();
			tryBotMove();
		} else {
			changeActiveUser();
		}
	}

	public boolean isEndOfMatch() {
		return activeUser == null;
	}

	public boolean isEndOfSet() {
		return isEndOfMatch() && matchNumber == matchUsers.get(0).getCards().size();
	}

	public boolean isLastUserWithInvalidExpectedTakes(int expectedTakes) {
		if (!isLastUser()) {
			return false;
		}

		int invalidExpectedTakes = -getMatchCharacter();
		return expectedTakes == invalidExpectedTakes;
	}

	public int getMatchCharacter() {
		int sumOfExpectedTakes = getSumOfExpectedTakes();
		return sumOfExpectedTakes - matchUsers.get(0).getCards().size();
	}

	public boolean setCanStart() {
		int lastIndex = matchUsers.size() - 1;
		return matchUsers.get(lastIndex).getExpectedTakes() != null;
	}

	public int getWinnerIndex() {
		Card winnerCard = null;

		for (Card card : cardPlaceholders) {
			if (winnerCard == null) {
				winnerCard = card;
			} else if (winnerCard.getColor() == card.getColor()) {
				winnerCard = winnerCard.getValue() < card.getValue() ? card : winnerCard;
			} else if (card.getColor() == Color.HEARTS) {
				winnerCard = card;
			}
		}

		return cardPlaceholders.indexOf(winnerCard);
	}

	public void startNewGame() {
		if (tryResetTerminatorFlags()) {
			Collections.rotate(gameUsers, -1);
		}

		setNumber = 0;

		rotateGameUsersAccordingToTerminators();

		setUsers = new ArrayList<>(gameUsers);

		startNewSet();
		startNewMatch();
	}

	public int getSumOfExpectedTakes() {
		return matchUsers.stream()
				.map(User::getExpectedTakes)
				.filter(Objects::nonNull)
				.mapToInt(expectedTakes -> expectedTakes)
				.sum();
	}

	public boolean usersWantNewGame() {
		return gameUsers.stream()
				.filter(user -> !user.isBot())
				.noneMatch(user -> !user.wantNewGame() && !user.isLoggedOut());
	}

	boolean isLastUser() {
		return matchUsers.indexOf(activeUser) + 1 == matchUsers.size();
	}

	private void resetActiveUser() {
		setActiveUser(matchUsers.get(0));
	}

	private void resetReadyFlags() {
		gameUsers.stream()
				.filter(User::isReady)
				.forEach(user -> user.setReady(false));
	}

	private void resetNewGameFlags() {
		gameUsers.stream()
				.filter(User::wantNewGame)
				.forEach(User::resetAction);
	}

	private boolean tryResetTerminatorFlags() {
		boolean noCards = true;

		for (User user : gameUsers) {
			if (user.wasTerminator() == null) {
				noCards = user.getCards().isEmpty();
				user.setTerminator(noCards);

				break;
			}
		}

		if (!noCards && terminatorFlagsReseted) {
			gameUsers.forEach(user -> user.setTerminator(true));
			return false;
		}

		for (User user : gameUsers) {
			if (Boolean.FALSE.equals(user.wasTerminator())) {
				setTerminatorFlagsReseted(false);
				return false;
			}
		}

		gameUsers.forEach(user -> user.setTerminator(false));
		setTerminatorFlagsReseted(true);

		return true;
	}

	private void rotateGameUsersAccordingToTerminators() {
		List<User> gameUsersCopy = new ArrayList<>(gameUsers);
		int rotateDistanceToLastSet = -(getUserCardsCount() - 1);
		Collections.rotate(gameUsersCopy, rotateDistanceToLastSet);

		int lastUserIndex = gameUsersCopy.size() - 1;
		User lastUser = gameUsersCopy.get(lastUserIndex);

		if (Boolean.TRUE.equals(lastUser.wasTerminator())) {
			Collections.rotate(gameUsers, -1);
			rotateGameUsersAccordingToTerminators();
		} else {
			lastUser.setTerminator(null);
		}
	}

	private void initCardPlaceholders() {
		cardPlaceholders = matchUsers.stream()
				.map(user -> CardProvider.CARD_PLACEHOLDER)
				.collect(Collectors.toList());
		botSimulator.setCardPlaceholders(cardPlaceholders);
	}

	private void setActiveUser(User activeUser) {
		this.activeUser = activeUser;
		botSimulator.setActiveUser(activeUser);
	}

	private void setMatchUsers(List<User> matchUsers) {
		this.matchUsers = matchUsers;
		botSimulator.setUsers(matchUsers);
	}

	private void tryBotMove() {
		botSimulator.tryBotMove();
	}

	private void initUserCards() {
		int userCardsCount = getUserCardsCount();

		if (userCardsCount == -1) {
			throw new EndGameException();
		}

		setUsers.forEach(user -> user.getCards().clear());

		List<Card> deckOfCards = cardProvider.getShuffledDeckOfCards();
		for (int i = 0; i < userCardsCount; ++i) {
			for (User user : setUsers) {
				Card card = deckOfCards.remove(0);
				user.addCard(card);
			}
		}

		setUsers.forEach(user -> Collections.sort(user.getCards()));
	}

	private int getUserCardsCount() {
		int deckOfCardsSize = cardProvider.getDeckOfCardsSize();
		int gameUsersSize = gameUsers.size();

		int userCardsCount = deckOfCardsSize / gameUsersSize;
		userCardsCount = Math.min(userCardsCount, 10);
		userCardsCount -= setNumber;

		return userCardsCount;
	}

	private void startNewMatch() {
		initCardPlaceholders();

		if (everybodyLost || matchNumber++ == matchUsers.get(0).getCards().size()) {
			everybodyLost = false;

			startNewSet();
			startNewMatch();

			tryBotMove();
		} else {
			tryBotMove();
		}
	}

	private void startNewSet() {
		if (setNumber > 0) {
			Collections.rotate(setUsers, -1);
		}
		setMatchUsers(new ArrayList<>(setUsers));

		matchNumber = 0;
		resetActiveUser();

		initUserCards();

		++setNumber;

		for (User user : setUsers) {
			user.setExpectedTakes(null);
			user.resetActualTakes();
			user.resetLastAddedPoints();
		}
	}

	private void calculatePoints() {
		if (!isEndOfSet()) {
			return;
		}

		int winCount = 0;
		int loseCount = 0;
		for (User user : setUsers) {
			if (user.isWinner()) {
				++winCount;
			} else {
				++loseCount;
			}
		}

		float winPoints = pointProvider.getWinnerPoints(setUsers.size(), winCount);
		float losePoints = (winCount * winPoints) / -loseCount;

		for (User user : setUsers) {
			float points = user.isWinner() ? winPoints : losePoints;
			user.addPoints(points);
		}
	}
}
