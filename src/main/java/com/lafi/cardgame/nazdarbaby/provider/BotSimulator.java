package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class BotSimulator {

	private final Set<Card> playedOutCards = new HashSet<>();
	private final Map<User, Map<User, UserInfo>> botToOtherUsersInfo = new HashMap<>();
	private final Random random = new Random();
	private final Game game;

	private List<Card> cardPlaceholders;
	private User activeUser;
	private List<User> users;
	private int deckOfCardsSize;

	BotSimulator(Game game) {
		this.game = game;
	}

	void setCardPlaceholders(List<Card> cardPlaceholders) {
		this.cardPlaceholders = cardPlaceholders;
	}

	void setActiveUser(User activeUser) {
		this.activeUser = activeUser;
		rememberCardsFromTable();
	}

	void setUsers(List<User> users) {
		this.users = users;

		playedOutCards.clear();
		botToOtherUsersInfo.clear();

		for (User theUser : users) {
			if (theUser.isBot()) {
				Map<User, UserInfo> otherUsersInfo = users.stream()
						.filter(user -> !user.equals(theUser))
						.collect(Collectors.toMap(Function.identity(), user -> new UserInfo()));
				botToOtherUsersInfo.put(theUser, otherUsersInfo);
			}
		}
	}

	void setDeckOfCardsSize(int deckOfCardsSize) {
		this.deckOfCardsSize = deckOfCardsSize;
	}

	void tryBotMove() {
		collectKnownInfoAboutUsers();

		if (activeUser == null || !activeUser.isBot()) {
			return;
		}

		if (activeUser.getExpectedTakes() == null) {
			double expectedTakes = guessExpectedTakes(activeUser);
			int expectedTakesRounded = (int) Math.round(expectedTakes);

			if (game.isLastUserWithInvalidExpectedTakes(expectedTakesRounded)) {
				if (expectedTakes > expectedTakesRounded) {
					activeUser.setExpectedTakes(expectedTakesRounded + 1);
				} else {
					activeUser.setExpectedTakes(expectedTakesRounded - 1);
				}
			} else {
				activeUser.setExpectedTakes(expectedTakesRounded);
			}

			game.afterActiveUserSetExpectedTakes();
		} else {
			List<Card> activeUserCards = activeUser.getCards();

			int activeUserIndex = getActiveUserIndex();
			Card selectedCard = selectCard(activeUserCards);
			cardPlaceholders.set(activeUserIndex, selectedCard);

			int cardIndex = activeUserCards.indexOf(selectedCard);
			activeUserCards.set(cardIndex, CardProvider.CARD_PLACEHOLDER);

			game.changeActiveUser();
		}
	}

	boolean isHighestRemainingCardInColor(List<Card> cards, Card theCard) {
		Card winningCard = getWinningCard();
		if (winningCard.isHigherThan(theCard)) {
			return false;
		}

		long higherKnownCardsInOneColorSize = getKnownCardsInOneColorStream(cards, theCard.getColor())
				.filter(card -> card.getValue() > theCard.getValue())
				.count();
		int highestCardValue = getHighestCardValue();

		return higherKnownCardsInOneColorSize == highestCardValue - theCard.getValue();
	}

	private boolean isLowestRemainingColor(List<Card> cards, Card theCard) {
		long lowerKnownCardsInOneColorSize = getKnownCardsInOneColorStream(cards, theCard.getColor())
				.filter(card -> card.getValue() < theCard.getValue())
				.count();

		CardProvider cardProvider = game.getCardProvider();
		int lowestCardValue = cardProvider.getLowestCardValue();

		return lowerKnownCardsInOneColorSize == theCard.getValue() - lowestCardValue;
	}

	double guessExpectedTakes(User user) {
		int highestCardValue = getHighestCardValue();
		int numberOfCardsInOneColor = getNumberOfCardsInOneColor();
		double magicNumber = highestCardValue - ((double) numberOfCardsInOneColor / users.size()) + 1;

		List<Card> cards = user.getCards();

		double guess = 0;
		for (Card card : cards) {
			int cardValue = card.getValue();
			double diff = magicNumber - cardValue;

			if (cardValue > magicNumber) {
				++guess;
			} else if (card.getColor() == Color.HEARTS) {
				if (areOthersWithoutHearts(user, card) || isHighestRemainingCardInColor(cards, card)) {
					++guess;
				} else if (diff < 1) {
					guess += Math.max(diff, 0.5);
				} else {
					guess += 0.5;
				}
			} else if (diff < 1) {
				guess += diff;
			}
		}

		return guess;
	}

	private int getNumberOfCardsInOneColor() {
		return deckOfCardsSize / Color.values().length;
	}

	boolean areOthersWithoutHearts(User user, Card theCard) {
		Card winningCard = getWinningCard();
		if (winningCard.isHigherThan(theCard)) {
			return false;
		}

		Map<User, UserInfo> otherUsersInfo = botToOtherUsersInfo.get(user);
		return otherUsersInfo.values().stream().noneMatch(userInfo -> userInfo.hasColor(Color.HEARTS));
	}

	private Stream<Card> getKnownCardsInOneColorStream(List<Card> cards, Color color) {
		return Stream.concat(playedOutCards.stream(), cards.stream()).filter(card -> card.getColor() == color);
	}

	private Card selectCard(List<Card> cards) {
		removeColorsForOtherUsers(cards);

		List<Card> sortedPlayableCards = getSortedPlayableCards(cards);
		int sortedPlayableCardsSize = sortedPlayableCards.size();

		if (sortedPlayableCardsSize == 1) {
			return sortedPlayableCards.get(0);
		}

		if (noGapsInOneColor(sortedPlayableCards)) {
			int index = random.nextInt(sortedPlayableCardsSize);
			return sortedPlayableCards.get(index);
		}

		double takesGuessed = guessExpectedTakes(activeUser);
		int takesNeeded = activeUser.getExpectedTakes() - activeUser.getActualTakes();

		if (takesGuessed > takesNeeded) {
			return selectLowCard(sortedPlayableCards);
		}
		return selectHighCard(sortedPlayableCards);
	}

	void removeColorsForOtherUsers(List<Card> cards) {
		int numberOfCardsInOneColor = getNumberOfCardsInOneColor();
		for (Color color : Color.values()) {
			long knownCardsInOneColorSize = getKnownCardsInOneColorStream(cards, color).count();
			if (knownCardsInOneColorSize == numberOfCardsInOneColor) {
				Map<User, UserInfo> otherUsersInfo = botToOtherUsersInfo.get(activeUser);
				otherUsersInfo.values().forEach(userInfo -> userInfo.removeColor(color));
			}
		}
	}

	private List<Card> getSortedPlayableCards(List<Card> cards) {
		Card leadingCard = getLeadingCard();
		Color leadingCardColor = leadingCard.getColor();

		Stream<Card> playableCardStream = cards.stream();
		if (leadingCard.isPlaceholder()) {
			playableCardStream = playableCardStream.filter(card -> !card.isPlaceholder());
		} else if (activeUser.hasColor(leadingCardColor)) {
			playableCardStream = playableCardStream.filter(card -> card.getColor() == leadingCardColor);
		} else if (activeUser.hasColor(Color.HEARTS)) {
			playableCardStream = playableCardStream.filter(card -> card.getColor() == Color.HEARTS);
		} else {
			playableCardStream = playableCardStream.filter(card -> !card.isPlaceholder());
		}

		List<Card> sortedPlayableCards = playableCardStream
				.sorted()
				.toList();
		return new ArrayList<>(sortedPlayableCards);
	}

	private void rememberCardsFromTable() {
		if (cardPlaceholders != null) {
			playedOutCards.addAll(cardPlaceholders);
			playedOutCards.remove(CardProvider.CARD_PLACEHOLDER);
		}
	}

	private void collectKnownInfoAboutUsers() {
		if (cardPlaceholders == null) {
			return;
		}

		Card leadingCard = cardPlaceholders.get(0);
		if (leadingCard.isPlaceholder()) {
			return;
		}

		for (int i = 1; i < cardPlaceholders.size(); ++i) {
			Card card = cardPlaceholders.get(i);
			if (card.isPlaceholder()) {
				break;
			}

			if (card.getColor() != leadingCard.getColor()) {
				User affectedUser = users.get(i);

				for (Map<User, UserInfo> otherUsersInfo : botToOtherUsersInfo.values()) {
					UserInfo userInfo = otherUsersInfo.get(affectedUser);
					if (userInfo != null) {
						userInfo.removeColor(leadingCard.getColor());
					}
				}
			}
		}
	}

	private boolean noGapsInOneColor(List<Card> sortedPlayableCards) {
		Card lowestCard = sortedPlayableCards.get(0);
		Card highestCard = sortedPlayableCards.get(sortedPlayableCards.size() - 1);

		boolean allPlayableCardsInOneColor = sortedPlayableCards.stream().allMatch(card -> card.getColor() == lowestCard.getColor());
		if (!allPlayableCardsInOneColor) {
			return false;
		}

		int expectedPlayedOutCardSizeInOneColor = highestCard.getValue() - lowestCard.getValue() - sortedPlayableCards.size() + 1;

		if (expectedPlayedOutCardSizeInOneColor == 0) {
			return true;
		}

		long playedOutCardInOneColorSize = playedOutCards.stream()
				.filter(card -> card.getColor() == lowestCard.getColor())
				.filter(card -> card.getValue() > lowestCard.getValue())
				.filter(card -> card.getValue() < highestCard.getValue())
				.count();

		return expectedPlayedOutCardSizeInOneColor == playedOutCardInOneColorSize;
	}

	private Card selectLowCard(List<Card> sortedPlayableCards) {
		Card lowestCard = getLowestCard(sortedPlayableCards);
		Card leadingCard = getLeadingCard();
		Card winningCard = getWinningCard();

		if (winningCard.isPlaceholder()) {
			return lowestCard;
		}

		if (lowestCard.getColor() == Color.HEARTS) {
			if (winningCard.getColor() == Color.HEARTS) {
				return selectLowCard(sortedPlayableCards, winningCard);
			}
			if (game.isLastUser()) {
				return getHighestCard(sortedPlayableCards, false);
			}
			return lowestCard;
		} else if (lowestCard.getColor() == leadingCard.getColor()) {
			if (winningCard.getColor() == Color.HEARTS) {
				return getHighestCard(sortedPlayableCards, false);
			}
			return selectLowCard(sortedPlayableCards, winningCard);
		}
		return getHighestCard(sortedPlayableCards, false);
	}

	private Card selectLowCard(List<Card> sortedPlayableCards, Card winningCard) {
		Card lowerCard = getLowerCard(sortedPlayableCards, winningCard);
		if (lowerCard != null) {
			return lowerCard;
		}
		if (game.isLastUser()) {
			return getHighestCard(sortedPlayableCards, false);
		}
		return getHigherCard(sortedPlayableCards, winningCard).get();
	}

	private Card selectHighCard(List<Card> sortedPlayableCards) {
		Card winningCard = getWinningCard();
		Card highestCard = getHighestCard(sortedPlayableCards, true);

		if (winningCard.isPlaceholder()) {
			return highestCard;
		}

		Card leadingCard = getLeadingCard();

		if (highestCard.getColor() == Color.HEARTS) {
			if (winningCard.getColor() == Color.HEARTS) {
				return selectHighCard(sortedPlayableCards, winningCard, highestCard);
			}
			return getLowestCard(sortedPlayableCards);
		} else if (highestCard.getColor() == leadingCard.getColor()) {
			if (winningCard.getColor() == Color.HEARTS) {
				return getLowestCard(sortedPlayableCards);
			}
			return selectHighCard(sortedPlayableCards, winningCard, highestCard);
		}
		return getLowestCard(sortedPlayableCards);
	}

	private Card selectHighCard(List<Card> sortedPlayableCards, Card winningCard, Card highestCard) {
		Optional<Card> higherCard = getHigherCard(sortedPlayableCards, winningCard);
		if (higherCard.isPresent()) {
			if (game.isLastUser()) {
				return higherCard.get();
			}
			return highestCard;
		}
		return getLowestCard(sortedPlayableCards);
	}

	private Card getWinningCard() {
		int winningCardIndex = game.getWinnerIndex();
		return cardPlaceholders.get(winningCardIndex);
	}

	private Card getLeadingCard() {
		return cardPlaceholders.get(0);
	}

	private Card getLowestCard(List<Card> cards) {
		List<Card> lowestRemainingCards = cards.stream()
				.filter(card -> isLowestRemainingColor(cards, card))
				.toList();

		if (lowestRemainingCards.isEmpty()) {
			Card lowestCardInHand = cards.get(0);
			List<Card> lowestCardsInHands = cards.stream()
					.filter(card -> card.getValue() == lowestCardInHand.getValue())
					.toList();

			return getLowestCardToLose(lowestCardsInHands);
		}
		return getLowestCardToLose(lowestRemainingCards);
	}

	private Card getHighestCard(List<Card> cards, boolean toWin) {
		if (toWin) {
			List<Card> highestRemainingCards = cards.stream()
					.filter(card -> isHighestRemainingCardInColor(cards, card))
					.toList();

			if (highestRemainingCards.isEmpty()) {
				Card highestCardInHand = cards.get(cards.size() - 1);
				List<Card> highestCardsInHands = cards.stream()
						.filter(card -> card.getValue() == highestCardInHand.getValue())
						.toList();

				return getHighestCardToWin(highestCardsInHands);
			}
			return getHighestCardToWin(highestRemainingCards);
		}
		return getRidOfCard(cards);
	}

	private Card getRidOfCard(List<Card> cards) {
		List<Card> cardsToGetRidOf = cards.stream()
				.filter(card -> !isLowestRemainingColor(cards, card))
				.toList();

		if (cardsToGetRidOf.isEmpty()) {
			Card card = getRidOfColor(cards, cards);
			if (card == null) {
				return cards.get(cards.size() - 1);
			}
			return card;
		}

		Card card = getRidOfColor(cardsToGetRidOf, cards);
		if (card == null) {
			return cards.get(cards.size() - 1);
		}
		return card;
	}

	private Card getRidOfColor(List<Card> cardsToGetRidOf, List<Card> cardsInHand) {
		List<Card> revertedCardsToGetRidOf = new ArrayList<>(cardsToGetRidOf);
		Collections.reverse(revertedCardsToGetRidOf);

		for (Card theCard : revertedCardsToGetRidOf) {
			long cardsInColorCount = cardsInHand.stream().filter(card -> card.getColor() == theCard.getColor()).count();

			if (cardsInColorCount == 1) {
				return theCard;
			}
		}
		return null;
	}

	private Card getHighestCardToWin(List<Card> cards) {
		if (cards.size() == 1) {
			return cards.get(0);
		}

		List<Card> possibleWinnerCards = cards.stream()
				.filter(card -> isPossibleColorToWin(card.getColor()))
				.toList();

		if (possibleWinnerCards.isEmpty()) {
			return getMostProbableCardToWin(cards);
		}
		return getMostProbableCardToWin(possibleWinnerCards);
	}

	private Card getLowestCardToLose(List<Card> cards) {
		if (cards.size() == 1) {
			return cards.get(0);
		}

		List<Card> possibleLoserCards = cards.stream()
				.filter(card -> isPossibleColorToLose(card.getColor()))
				.toList();

		if (possibleLoserCards.isEmpty()) {
			return getMostProbableCardToWin(cards);
		}
		return getMostProbableCardToWin(possibleLoserCards);
	}

	private boolean isPossibleColorToWin(Color color) {
		int activeUserIndex = getActiveUserIndex();
		Map<User, UserInfo> otherUsersInfo = botToOtherUsersInfo.get(activeUser);

		for (int i = activeUserIndex + 1; i < users.size(); ++i) {
			User user = users.get(i);
			UserInfo userInfo = otherUsersInfo.get(user);

			if (!userInfo.hasColor(color) && userInfo.hasColor(Color.HEARTS)) {
				return false;
			}
		}
		return true;
	}

	private boolean isPossibleColorToLose(Color color) {
		int activeUserIndex = getActiveUserIndex();
		Map<User, UserInfo> otherUsersInfo = botToOtherUsersInfo.get(activeUser);

		for (int i = activeUserIndex + 1; i < users.size(); ++i) {
			User user = users.get(i);
			UserInfo userInfo = otherUsersInfo.get(user);

			if (userInfo.hasColor(color) || userInfo.hasColor(Color.HEARTS)) {
				return true;
			}
		}
		return false;
	}

	private Card getMostProbableCardToWin(List<Card> cards) {
		if (cards.size() == 1) {
			return cards.get(0);
		}

		long lowestKnownCardsInOneColorSize = getHighestCardValue();
		List<Card> mostProbableCardsToWin = new ArrayList<>();

		List<Card> activeUserCards = activeUser.getCards();
		for (Card card : cards) {
			long knownCardsInOneColorSize = getKnownCardsInOneColorStream(activeUserCards, card.getColor()).count();
			if (knownCardsInOneColorSize < lowestKnownCardsInOneColorSize) {
				lowestKnownCardsInOneColorSize = knownCardsInOneColorSize;

				mostProbableCardsToWin.clear();
				mostProbableCardsToWin.add(card);
			} else if (knownCardsInOneColorSize == lowestKnownCardsInOneColorSize) {
				mostProbableCardsToWin.add(card);
			}
		}

		int index = random.nextInt(mostProbableCardsToWin.size());
		return mostProbableCardsToWin.get(index);
	}

	private int getHighestCardValue() {
		CardProvider cardProvider = game.getCardProvider();
		return cardProvider.getHighestCardValue();
	}

	private int getActiveUserIndex() {
		return users.indexOf(activeUser);
	}

	private Card getLowerCard(List<Card> sortedCards, Card theCard) {
		List<Card> lowerCards = sortedCards.stream()
				.filter(card -> card.getValue() < theCard.getValue())
				.toList();

		if (lowerCards.isEmpty()) {
			return null;
		}
		return lowerCards.get(lowerCards.size() - 1);
	}

	private Optional<Card> getHigherCard(List<Card> sortedCards, Card theCard) {
		return sortedCards.stream()
				.filter(card -> card.getValue() > theCard.getValue())
				.findFirst();
	}

	private static final class UserInfo {

		private final Set<Color> colorsInHand = new HashSet<>(Arrays.asList(Color.values()));

		private boolean hasColor(Color color) {
			return colorsInHand.contains(color);
		}

		private void removeColor(Color color) {
			colorsInHand.remove(color);
		}
	}
}
