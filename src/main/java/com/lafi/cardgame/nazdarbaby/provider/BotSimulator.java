package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.ArrayList;
import java.util.Arrays;
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

	//TODO do it better
	private static final int HIGHEST_CARD_VALUE = 14;

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

			int userIndex = users.indexOf(activeUser);
			Card selectedCard = selectCard(activeUserCards);
			cardPlaceholders.set(userIndex, selectedCard);

			int cardIndex = activeUserCards.indexOf(selectedCard);
			activeUserCards.set(cardIndex, CardProvider.CARD_PLACEHOLDER);

			game.changeActiveUser();
		}
	}

	private double guessExpectedTakes(User user) {
		int numberOfCardsInOneColor = getNumberOfCardsInOneColor();
		double magicNumber = HIGHEST_CARD_VALUE - ((double) numberOfCardsInOneColor / users.size()) + 1;

		List<Card> cards = user.getCards();
		boolean othersWithoutHearts = areOthersWithoutHearts(user);

		double guess = 0;
		for (Card card : cards) {
			int cardValue = card.getValue();
			double diff = magicNumber - cardValue;

			if (cardValue > magicNumber) {
				++guess;
			} else if (card.getColor() == Color.HEARTS) {
				if (othersWithoutHearts || isHighestRemainingHeart(cards, card)) {
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

	private boolean areOthersWithoutHearts(User user) {
		Map<User, UserInfo> otherUsersInfo = botToOtherUsersInfo.get(user);
		return otherUsersInfo.values().stream().noneMatch(userInfo -> userInfo.hasColor(Color.HEARTS));
	}

	boolean isHighestRemainingHeart(List<Card> cards, Card theCard) {
		if (theCard.getColor() != Color.HEARTS) {
			return false;
		}

		Card winningCard = getWinningCard();
		if (winningCard.isHigherThan(theCard)) {
			return false;
		}

		List<Card> higherKnownHearts = getKnownCardsInOneColorStream(cards, Color.HEARTS)
				.filter(card -> card.getValue() > theCard.getValue())
				.toList();
		return higherKnownHearts.size() == HIGHEST_CARD_VALUE - theCard.getValue();
	}

	private Stream<Card> getKnownCardsInOneColorStream(List<Card> cards, Color color) {
		return Stream.concat(playedOutCards.stream(), cards.stream()).filter(card -> card.getColor() == color);
	}

	private Card selectCard(List<Card> cards) {
		tryToRemoveColorsForOtherUsers(cards);

		List<Card> sortedPlayableCards = getSortedPlayableCards(cards);
		int sortedPlayableCardsSize = sortedPlayableCards.size();

		if (sortedPlayableCardsSize == 1) {
			return sortedPlayableCards.get(0);
		}

		if (noGapsInOneColor(sortedPlayableCards)) {
			int randomIndex = random.nextInt(sortedPlayableCardsSize);
			return sortedPlayableCards.get(randomIndex);
		}

		double takesGuessed = guessExpectedTakes(activeUser);
		int takesNeeded = activeUser.getExpectedTakes() - activeUser.getActualTakes();

		if (takesGuessed > takesNeeded) {
			return selectLowCard(sortedPlayableCards);
		}
		return selectHighCard(sortedPlayableCards);
	}

	private void tryToRemoveColorsForOtherUsers(List<Card> cards) {
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

		return new ArrayList<>(playableCardStream.sorted().toList());
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
		Card lowestCard = getLowestCard(sortedPlayableCards);
		Card highestCard = getHighestCard(sortedPlayableCards);

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
				return getHighestCard(sortedPlayableCards);
			}
			return lowestCard;
		} else if (lowestCard.getColor() == leadingCard.getColor()) {
			if (winningCard.getColor() == Color.HEARTS) {
				return getHighestCard(sortedPlayableCards);
			}
			return selectLowCard(sortedPlayableCards, winningCard);
		}
		return getHighestCard(sortedPlayableCards);
	}

	private Card selectLowCard(List<Card> sortedPlayableCards, Card winningCard) {
		Optional<Card> lowerCard = getLowerCard(sortedPlayableCards, winningCard);
		if (lowerCard.isPresent()) {
			return lowerCard.get();
		}
		if (game.isLastUser()) {
			return getHighestCard(sortedPlayableCards);
		}
		return getHigherCard(sortedPlayableCards, winningCard).get();
	}

	private Card selectHighCard(List<Card> sortedPlayableCards) {
		Card highestCard = getHighestCard(sortedPlayableCards);
		Card leadingCard = getLeadingCard();
		Card winningCard = getWinningCard();

		if (winningCard.isPlaceholder()) {
			return highestCard;
		}

		if (highestCard.getColor() == Color.HEARTS) {
			if (winningCard.getColor() == Color.HEARTS) {
				return selectHighCard(sortedPlayableCards, winningCard);
			}
			return getLowestCard(sortedPlayableCards);
		} else if (highestCard.getColor() == leadingCard.getColor()) {
			if (winningCard.getColor() == Color.HEARTS) {
				return getLowestCard(sortedPlayableCards);
			}
			return selectHighCard(sortedPlayableCards, winningCard);
		}
		return getLowestCard(sortedPlayableCards);
	}

	private Card selectHighCard(List<Card> sortedPlayableCards, Card winningCard) {
		Optional<Card> higherCard = getHigherCard(sortedPlayableCards, winningCard);
		if (higherCard.isPresent()) {
			if (game.isLastUser()) {
				return higherCard.get();
			}
			return getHighestCard(sortedPlayableCards);
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
		return cards.get(0);
	}

	private Card getHighestCard(List<Card> cards) {
		return cards.get(cards.size() - 1);
	}

	private Optional<Card> getLowerCard(List<Card> sortedCards, Card theCard) {
		List<Card> lowerCards = sortedCards.stream()
				.filter(card -> card.getValue() < theCard.getValue())
				.toList();

		if (lowerCards.isEmpty()) {
			return Optional.empty();
		}

		Card card = getHighestCard(lowerCards);
		return Optional.of(card);
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
