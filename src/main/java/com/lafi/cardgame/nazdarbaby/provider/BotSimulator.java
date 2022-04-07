package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
	private final Random random = new Random();
	private final Game game;

	private List<Card> cardPlaceholders;
	private User activeUser;
	private List<User> matchUsers;
	private Map<User, UserInfo> userToUserInfo;
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
		collectKnownInfoAboutUsers();
	}

	void setMatchUsers(List<User> matchUsers) {
		this.matchUsers = matchUsers;
		userToUserInfo = matchUsers.stream().collect(Collectors.toMap(Function.identity(), matchUser -> new UserInfo()));

		playedOutCards.clear();
	}

	void setDeckOfCardsSize(int deckOfCardsSize) {
		this.deckOfCardsSize = deckOfCardsSize;
	}

	void tryBotMove() {
		if (!activeUser.isBot()) {
			return;
		}

		List<Card> activeUserCards = activeUser.getCards();
		if (activeUser.getExpectedTakes() == null) {
			double expectedTakes = guessExpectedTakes(activeUserCards);
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
			int matchUserIndex = matchUsers.indexOf(activeUser);
			Card selectedCard = selectCard(activeUserCards);
			cardPlaceholders.set(matchUserIndex, selectedCard);

			int cardIndex = activeUserCards.indexOf(selectedCard);
			activeUserCards.set(cardIndex, CardProvider.CARD_PLACEHOLDER);

			game.changeActiveUser();
		}
	}

	private double guessExpectedTakes(List<Card> cards) {
		int highestCardValue = 14; //TODO do it better
		int oneColorCardsSize = deckOfCardsSize / Color.values().length;
		double magicNumber = highestCardValue - ((double) oneColorCardsSize / matchUsers.size()) + 1;

		boolean othersWithoutHearts = areOthersWithoutHearts(cards, oneColorCardsSize);

		double guess = 0;
		for (Card card : cards) {
			int cardValue = card.getValue();
			double diff = magicNumber - cardValue;

			if (cardValue > magicNumber) {
				++guess;
			} else if (card.getColor() == Color.HEARTS) {
				if (othersWithoutHearts) {
					++guess;
				} else {
					guess += Math.max(diff, 0.5);
				}
			} else if (diff < 1) {
				guess += diff;
			}
		}

		return guess;
	}

	private boolean areOthersWithoutHearts(List<Card> cards, int oneColorCardsSize) {
		boolean othersWithoutHearts = userToUserInfo.entrySet().stream()
				.filter(entry -> entry.getKey() != activeUser)
				.noneMatch(entry -> entry.getValue().hasColor(Color.HEARTS));
		if (othersWithoutHearts) {
			return true;
		}

		long numberOfKnownHearts = playedOutCards.stream()
				.filter(card -> card.getColor() == Color.HEARTS)
				.count();
		numberOfKnownHearts += cards.stream()
				.filter(card -> card.getColor() == Color.HEARTS)
				.count();
		return numberOfKnownHearts == oneColorCardsSize;
	}

	private Card selectCard(List<Card> cards) {
		List<Card> playableCards = getPlayableCards(cards);
		int playableCardsSize = playableCards.size();

		if (playableCardsSize == 1) {
			return playableCards.get(0);
		}

		Collections.sort(playableCards);

		if (noGapsInOneColor(playableCards)) {
			int randomIndex = random.nextInt(playableCardsSize);
			return playableCards.get(randomIndex);
		}

		List<Card> activeUserCards = activeUser.getCards();
		double takesGuessed = guessExpectedTakes(activeUserCards);
		int takesNeeded = activeUser.getExpectedTakes() - activeUser.getActualTakes();

		if (takesGuessed > takesNeeded) {
			return selectLowCard(playableCards);
		}
		return selectHighCard(playableCards);
	}

	private List<Card> getPlayableCards(List<Card> cards) {
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

		return new ArrayList<>(playableCardStream.toList());
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
				User user = matchUsers.get(i);
				UserInfo userInfo = userToUserInfo.get(user);

				userInfo.removeColor(leadingCard.getColor());
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

		long playedOutCardSizeInOneColor = playedOutCards.stream()
				.filter(card -> card.getColor() == lowestCard.getColor())
				.filter(card -> card.getValue() > lowestCard.getValue())
				.filter(card -> card.getValue() < highestCard.getValue())
				.count();

		return expectedPlayedOutCardSizeInOneColor == playedOutCardSizeInOneColor;
	}

	private Card selectLowCard(List<Card> sortedPlayableCards) {
		Card lowestCard = getLowestCard(sortedPlayableCards);
		Card leadingCard = getLeadingCard();

		int winningCardIndex = game.getWinnerIndex();
		Card winningCard = cardPlaceholders.get(winningCardIndex);

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

		int winningCardIndex = game.getWinnerIndex();
		Card winningCard = cardPlaceholders.get(winningCardIndex);

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
