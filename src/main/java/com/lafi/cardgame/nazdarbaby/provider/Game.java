package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.exception.EndGameException;
import com.lafi.cardgame.nazdarbaby.point.PointProvider;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class Game {

	private final BotSimulator botSimulator = new BotSimulator(this);
	private final UserProvider userProvider;
	private final PointProvider pointProvider;

	private List<User> gameUsers;
	private List<User> setUsers;
	private List<User> trickUsers;

	private CardProvider cardProvider;
	private List<Card> cardPlaceholders;

	private int setNumber;
	private int trickNumber;

	private User activeUser;
	private boolean terminatorFlagsReset;
	private boolean gameInProgress;

	Game(UserProvider userProvider, PointProvider pointProvider) {
		this.userProvider = userProvider;
		this.pointProvider = pointProvider;
	}

	public synchronized List<User> getSetUsers() {
		return setUsers;
	}

	public synchronized List<User> getTrickUsers() {
		return trickUsers;
	}

	public synchronized List<Card> getCardPlaceholders() {
		return cardPlaceholders;
	}

	public synchronized boolean isGameInProgress() {
		return gameInProgress;
	}

	public BotSimulator getBotSimulator() {
		return botSimulator;
	}

	public UserProvider getUserProvider() {
		return userProvider;
	}

	public synchronized void setGameInProgress(boolean gameInProgress) {
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

	public synchronized boolean isActiveUser() {
		User currentUser = userProvider.getCurrentUser();
		return currentUser.equals(activeUser);
	}

	public synchronized void changeActiveUser() {
		int activeUserIndex = activeUser == null ? trickUsers.size() : trickUsers.indexOf(activeUser);

		if (activeUserIndex == trickUsers.size()) {
			int winnerIndex = getWinningIndex();

			Collections.rotate(trickUsers, -winnerIndex);
			resetActiveUser();

			startNewTrick();
		} else if (++activeUserIndex == trickUsers.size()) {
			int winnerIndex = getWinningIndex();
			User winUser = trickUsers.get(winnerIndex);
			winUser.increaseActualTakes();

			setActiveUser(null);

			calculatePoints();
		} else {
			setActiveUser(trickUsers.get(activeUserIndex));
		}

		tryBotMove();
	}

	public synchronized void afterActiveUserSetExpectedTakes() {
		if (isLastUser()) {
			resetActiveUser();
			tryBotMove();
		} else {
			changeActiveUser();
		}
	}

	public synchronized boolean isEndOfTrick() {
		return activeUser == null;
	}

	public synchronized boolean isEndOfSet() {
		return isEndOfTrick() && trickNumber == trickUsers.getFirst().getCards().size();
	}

	public synchronized boolean isLastUserWithInvalidExpectedTakes(int expectedTakes) {
		if (!isLastUser()) {
			return false;
		}

		int invalidExpectedTakes = -getTrickCharacter();
		return expectedTakes == invalidExpectedTakes;
	}

	public synchronized int getTrickCharacter() {
		int sumOfExpectedTakes = getSumOfExpectedTakes();
		return sumOfExpectedTakes - trickUsers.getFirst().getCards().size();
	}

	public synchronized boolean setCanStart() {
		return trickUsers.getLast().getExpectedTakes() != null;
	}

	public synchronized int getWinningIndex() {
		Card winnerCard = getWinningCard();
		return cardPlaceholders.indexOf(winnerCard);
	}

	private Card getWinningCard() {
		Card winningCard = null;

		for (Card card : cardPlaceholders) {
			if (winningCard == null) {
				winningCard = card;
			} else if (winningCard.getColor() == card.getColor()) {
				winningCard = winningCard.getValue() < card.getValue() ? card : winningCard;
			} else if (card.getColor() == Color.HEARTS) {
				winningCard = card;
			}
		}

		return winningCard;
	}

	public synchronized void startNewGame() {
		if (tryResetTerminatorFlags()) {
			Collections.rotate(gameUsers, -1);
		}

		setNumber = 0;

		rotateGameUsersAccordingToTerminators();

		setUsers = new CopyOnWriteArrayList<>(gameUsers);

		startNewSet();
		startNewTrick();
	}

	public synchronized int getSumOfExpectedTakes() {
		return trickUsers.stream()
				.map(User::getExpectedTakes)
				.filter(Objects::nonNull)
				.mapToInt(expectedTakes -> expectedTakes)
				.sum();
	}

	public synchronized boolean usersWantNewGame() {
		return gameUsers.stream()
				.filter(user -> !user.isBot())
				.noneMatch(user -> !user.wantNewGame() && !user.isLoggedOut());
	}

	synchronized boolean isLastUser() {
		return trickUsers.indexOf(activeUser) + 1 == trickUsers.size();
	}

	synchronized CardProvider getCardProvider() {
		return cardProvider;
	}

	private void resetActiveUser() {
		setActiveUser(trickUsers.getFirst());
	}

	private void resetReadyFlags() {
		gameUsers.stream()
				.filter(User::isReady)
				.filter(user -> !user.isBot())
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

		if (!noCards && terminatorFlagsReset) {
			gameUsers.forEach(user -> user.setTerminator(true));
			return false;
		}

		for (User user : gameUsers) {
			if (Boolean.FALSE.equals(user.wasTerminator())) {
				terminatorFlagsReset = false;
				return false;
			}
		}

		gameUsers.forEach(user -> user.setTerminator(false));
		terminatorFlagsReset = true;

		return true;
	}

	private void rotateGameUsersAccordingToTerminators() {
		List<User> gameUsersCopy = new ArrayList<>(gameUsers);
		int rotateDistanceToLastSet = -(getUserCardsCount() - 1);
		Collections.rotate(gameUsersCopy, rotateDistanceToLastSet);

		User lastUser = gameUsersCopy.getLast();

		if (Boolean.TRUE.equals(lastUser.wasTerminator())) {
			Collections.rotate(gameUsers, -1);
			rotateGameUsersAccordingToTerminators();
		} else {
			lastUser.setTerminator(null);
		}
	}

	private void initCardPlaceholders() {
		cardPlaceholders = trickUsers.stream()
				.map(_ -> CardProvider.CARD_PLACEHOLDER)
				.collect(Collectors.toCollection(CopyOnWriteArrayList::new));
		botSimulator.setCardPlaceholders(cardPlaceholders);
	}

	private void setActiveUser(User activeUser) {
		this.activeUser = activeUser;
		botSimulator.setActiveUser(activeUser);
	}

	private void setTrickUsers(List<User> trickUsers) {
		this.trickUsers = trickUsers;
		botSimulator.setUsers(trickUsers);
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
				Card card = deckOfCards.removeFirst();
				user.addCard(card);
			}
		}

		setUsers.forEach(user -> user.getCards().sort(this::sortCardsOnTable));
	}

	private int sortCardsOnTable(Card card1, Card card2) {
		Integer card1Value = card1.getValue() + card1.getColor().getCompareToValue();
		Integer card2Value = card2.getValue() + card2.getColor().getCompareToValue();

		return card1Value.compareTo(card2Value);
	}

	private int getUserCardsCount() {
		int deckOfCardsSize = cardProvider.getDeckOfCardsSize();
		int gameUsersSize = gameUsers.size();

		int userCardsCount = deckOfCardsSize / gameUsersSize;
		userCardsCount = Math.min(userCardsCount, 10);
		userCardsCount -= setNumber;

		return userCardsCount;
	}

	private void startNewTrick() {
		trickUsers.stream()
				.filter(user -> !user.isBot())
				.forEach(User::resetAction);

		initCardPlaceholders();

		if (trickNumber++ == trickUsers.getFirst().getCards().size()) {
			startNewSet();
			startNewTrick();
		}

		tryBotMove();
	}

	private void startNewSet() {
		if (setNumber > 0) {
			Collections.rotate(setUsers, -1);
		}
		setTrickUsers(new CopyOnWriteArrayList<>(setUsers));

		trickNumber = 0;
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
