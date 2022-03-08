package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.exception.EndGameException;
import com.lafi.cardgame.nazdarbaby.points.Points;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Game {

	private static final Map<UserProvider, Game> USER_PROVIDER_TO_GAME = new HashMap<>();

	private List<User> gameUsers;
	private List<User> setUsers;
	private List<User> matchUsers;

	private CardProvider cardProvider;
	private List<Card> cardPlaceholders;

	private int setNumber;
	private int matchNumber;

	private User activeUser;
	private Card winCard;
	private boolean terminatorFlagsReseted;
	private boolean gameInProgress;

	private final UserProvider userProvider;

	private Game(UserProvider userProvider) {
		this.userProvider = userProvider;
	}

	static Game get(UserProvider userProvider) {
		return USER_PROVIDER_TO_GAME.computeIfAbsent(userProvider, s -> new Game(userProvider));
	}

	public void delete() {
		USER_PROVIDER_TO_GAME.remove(userProvider);
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

	public boolean terminatorFlagsReseted() {
		return terminatorFlagsReseted;
	}

	public void setTerminatorFlagsReseted(boolean terminatorFlagsReseted) {
		this.terminatorFlagsReseted = terminatorFlagsReseted;
	}

	public boolean isGameInProgress() {
		return gameInProgress;
	}

	public void setGameInProgress(boolean gameInProgress) {
		this.gameInProgress = gameInProgress;

		if (gameInProgress) {
			gameUsers = userProvider.getPlayingUsers();
			Collections.shuffle(gameUsers);

			cardProvider = new CardProvider(gameUsers.size());

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
		int indexOfActiveUser = activeUser == null ? matchUsers.size() : matchUsers.indexOf(activeUser);

		if (indexOfActiveUser == matchUsers.size()) {
			int indexOfHighestCardUser = getIndexOfHighestCardUser();

			Collections.rotate(matchUsers, -indexOfHighestCardUser);
			resetActiveUser();

			startNewMatch();
		} else if (++indexOfActiveUser == matchUsers.size()) {
			User highestCardUser = getHighestCardUser();
			highestCardUser.increaseActualTakes();

			activeUser = null;

			calculatePoints();
		} else {
			activeUser = matchUsers.get(indexOfActiveUser);
		}
	}

	public void resetActiveUser() {
		activeUser = matchUsers.get(0);
	}

	public boolean isEndOfMatch() {
		return activeUser == null;
	}

	public boolean isEndOfSet() {
		return isEndOfMatch() && matchNumber == matchUsers.get(0).getCards().size();
	}

	public boolean isLastUser() {
		return matchUsers.indexOf(activeUser) + 1 == matchUsers.size();
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

	public void setWinCard(Card card) {
		if (winCard == null) {
			winCard = card;
		} else if (winCard.getColor() == card.getColor()) {
			winCard = winCard.getValue() < card.getValue() ? card : winCard;
		} else if (card.getColor() == Color.HEARTS) {
			winCard = card;
		}
	}

	public int getIndexOfHighestCardUser() {
		User highestCardUser = getHighestCardUser();
		return matchUsers.indexOf(highestCardUser);
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
		return gameUsers.stream().noneMatch(user -> !user.wantNewGame() && !user.isLoggedOut());
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

		if (!noCards && terminatorFlagsReseted()) {
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

	private User getHighestCardUser() {
		for (User user : matchUsers) {
			if (user.getLastPlayedCard().equals(winCard)) {
				return user;
			}
		}
		throw new IllegalStateException("Nobody has a win card = " + winCard);
	}

	private void rotateGameUsersAccordingToTerminators() {
		List<User> gameUsersCopy = new ArrayList<>(gameUsers);
		int rotateDistanceToLastSet = -(getUserCardsCount() - 1);
		Collections.rotate(gameUsersCopy, rotateDistanceToLastSet);

		int indexOfLastUser = gameUsersCopy.size() - 1;
		User lastUser = gameUsersCopy.get(indexOfLastUser);

		if (Boolean.TRUE.equals(lastUser.wasTerminator())) {
			Collections.rotate(gameUsers, -1);
			rotateGameUsersAccordingToTerminators();
		} else {
			lastUser.setTerminator(null);
		}
	}

	private void initCardPlaceholders() {
		cardPlaceholders = matchUsers.stream()
				.map(user -> CardProvider.getCardPlaceholder())
				.collect(Collectors.toList());
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
		winCard = null;

		matchUsers.forEach(user -> user.setLastPlayedCard(null));

		if (matchNumber++ == matchUsers.get(0).getCards().size()) {
			startNewSet();
			startNewMatch();
		}
	}

	private void startNewSet() {
		if (setNumber > 0) {
			Collections.rotate(setUsers, -1);
		}
		matchUsers = new ArrayList<>(setUsers);

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

		float winPoints = Points.getWinPoints(setUsers.size(), winCount);
		float losePoints = (winCount * winPoints) / -loseCount;

		for (User user : setUsers) {
			float points = user.isWinner() ? winPoints : losePoints;
			user.addPoints(points);
		}
	}
}
