package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.user.User;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

class BotSimulator {

	private final Set<Card> playedOutCards = new HashSet<>();
	private final Map<User, Map<User, UserInfo>> botToOtherUsersInfo = new HashMap<>();
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
			double expectedTakes = guessExpectedTakes();
			int expectedTakesRounded = (int) Math.round(expectedTakes);

			if (game.isLastUserWithInvalidExpectedTakes(expectedTakesRounded)) {
				if (expectedTakes > expectedTakesRounded || expectedTakesRounded == 0) {
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
		if (isLowerThanWinningCard(theCard)) {
			return false;
		}

		long higherKnownCardsInOneColorSize = getKnownCardsInOneColorStream(cards, theCard.getColor())
				.filter(card -> card.getValue() > theCard.getValue())
				.count();
		int highestCardValue = getHighestCardValue();

		return higherKnownCardsInOneColorSize == highestCardValue - theCard.getValue();
	}

	private boolean isLowerThanWinningCard(Card card) {
		Card winningCard = getWinningCard();
		return winningCard.isHigherThan(card);
	}

	private boolean isLowestRemainingCardInColor(List<Card> cards, Card theCard) {
		long lowerKnownCardsInOneColorSize = getKnownCardsInOneColorStream(cards, theCard.getColor())
				.filter(card -> card.getValue() < theCard.getValue())
				.count();

		CardProvider cardProvider = game.getCardProvider();
		int lowestCardValue = cardProvider.getLowestCardValue();

		return lowerKnownCardsInOneColorSize == theCard.getValue() - lowestCardValue;
	}

	double guessExpectedTakes() {
		int highestCardValue = getHighestCardValue();
		int numberOfCardsInOneColor = getNumberOfCardsInOneColor();
		double magicNumber = highestCardValue - ((double) numberOfCardsInOneColor / users.size()) + 1;

		List<Card> cards = activeUser.getCards();

		double guess = 0;
		for (Card card : cards) {
			int cardValue = card.getValue();
			double diff = magicNumber - cardValue;

			if (isLowerThanWinningCard(card)) {
				continue;
			}

			if (cardValue > magicNumber || isPossibleColorToWin(card) && isHighestRemainingCardInColor(cards, card)) {
				++guess;
			} else if (card.getColor() == Color.HEARTS) {
				if (areFollowersWithoutHearts(card) || isHighestRemainingCardInColor(cards, card)) {
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

	boolean areFollowersWithoutHearts(Card card) {
		if (isLowerThanWinningCard(card)) {
			return false;
		}

		Map<User, UserInfo> followersInfo = getFollowersInfo();
		return followersInfo.values().stream().noneMatch(userInfo -> userInfo.hasColor(Color.HEARTS));
	}

	private Map<User, UserInfo> getFollowersInfo() {
		int activeUserIndex = getActiveUserIndex();
		List<User> predecessors = users.subList(0, activeUserIndex);

		Map<User, UserInfo> followersInfo = botToOtherUsersInfo.get(activeUser);
		predecessors.forEach(followersInfo::remove);

		return followersInfo;
	}

	private Stream<Card> getKnownCardsInOneColorStream(List<Card> cards, Color color) {
		return Stream.concat(playedOutCards.stream(), cards.stream()).filter(card -> card.getColor() == color);
	}

	private Card selectCard(List<Card> cards) {
		removeColorsForOtherUsers(cards);

		List<Card> sortedPlayableCards = getSortedPlayableCards(cards);
		int sortedPlayableCardsSize = sortedPlayableCards.size();

		if (sortedPlayableCardsSize == 1) {
			return sortedPlayableCards.getFirst();
		}

		if (noGapsInOneColor(sortedPlayableCards)) {
			UniformRandomProvider uniformRandomProvider = RandomSource.XO_RO_SHI_RO_128_PP.create();
			int index = uniformRandomProvider.nextInt(0, sortedPlayableCardsSize);
			return sortedPlayableCards.get(index);
		}

		double takesGuessed = guessExpectedTakes();
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

		return playableCardStream
				.sorted()
				.collect(Collectors.toList());
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

		Card leadingCard = cardPlaceholders.getFirst();
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

						if (userInfo.hasColor(Color.HEARTS) && card.getColor() != Color.HEARTS) {
							userInfo.removeColor(Color.HEARTS);
						}
					}
				}
			}
		}
	}

	private boolean noGapsInOneColor(List<Card> sortedPlayableCards) {
		Card lowestCard = sortedPlayableCards.getFirst();
		Card highestCard = sortedPlayableCards.getLast();

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
            List<Card> cardsWhichShouldBeTakenByHeartsInNextMatch = getCardsWhichShouldBeTakenByHeartsInNextMatch(sortedPlayableCards);
            if (!cardsWhichShouldBeTakenByHeartsInNextMatch.isEmpty()) {
                return getHighestCard(cardsWhichShouldBeTakenByHeartsInNextMatch, false);
            }

            Set<Color> myColors = sortedPlayableCards.stream()
                    .map(Card::getColor)
                    .collect(Collectors.toSet());
			Map<User, UserInfo> followersInfo = getFollowersInfo();

			Set<Color> goodColors = new HashSet<>();
			for (Color color : myColors) {
				boolean allUsersHaveMyColor = followersInfo.values().stream()
						.allMatch(userInfo -> userInfo.hasColor(color) || userInfo.hasColor(Color.HEARTS));
				if (allUsersHaveMyColor) {
					goodColors.add(color);
				}
			}

			Set<Color> betterColors = new HashSet<>();
			for (Color color : goodColors) {
				boolean someUserHaveHearts = followersInfo.values().stream()
						.anyMatch(userInfo -> !userInfo.hasColor(color) && userInfo.hasColor(Color.HEARTS));
				if (someUserHaveHearts) {
					betterColors.add(color);
				}
			}

			if (!betterColors.isEmpty()) {
				List<Card> betterCards = sortedPlayableCards.stream()
						.filter(card -> betterColors.contains(card.getColor()))
						.toList();
				return getHighestCard(betterCards, false);
			}

            if (!goodColors.isEmpty()) {
                CardProvider cardProvider = game.getCardProvider();
                List<Card> remainingCards = cardProvider.getShuffledDeckOfCards();
                remainingCards.removeAll(playedOutCards);
                remainingCards.removeAll(sortedPlayableCards);

                List<Card> goodCards = sortedPlayableCards.stream()
                        .filter(card -> goodColors.contains(card.getColor()))
                        .toList();

                Map<Card, Long> cardToLowerCounter = new HashMap<>();
                for (Card myCard : goodCards) {
                    long lowerCounter = remainingCards.stream()
                            .filter(remainingCard -> remainingCard.getColor() == myCard.getColor() && remainingCard.getValue() < myCard.getValue())
                            .count();
                    cardToLowerCounter.put(myCard, lowerCounter);
                }

                Optional<Long> minCounter = cardToLowerCounter.values().stream().min(Comparator.naturalOrder());
                if (minCounter.isPresent()) {
                    List<Card> minLowerCards = cardToLowerCounter.entrySet().stream()
                            .filter(entry -> entry.getValue().equals(minCounter.get()))
                            .map(Map.Entry::getKey)
                            .toList();

                    Map<Card, Long> cardToHigherCounter = new HashMap<>();
                    for (Card myCard : minLowerCards) {
                        long higherCounter = remainingCards.stream()
                                .filter(remainingCard -> remainingCard.getColor() == myCard.getColor() && remainingCard.getValue() > myCard.getValue())
                                .count();
                        cardToHigherCounter.put(myCard, higherCounter);
                    }

                    Optional<Long> maxCounter = cardToHigherCounter.values().stream().max(Comparator.naturalOrder());
                    if (maxCounter.isPresent()) {
                        List<Card> minLowerMaxHigherCards = cardToHigherCounter.entrySet().stream()
                                .filter(entry -> entry.getValue().equals(maxCounter.get()))
                                .map(Map.Entry::getKey)
                                .toList();
                        return getHighestCard(minLowerMaxHigherCards, false);
                    }
                }
			}

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

    private List<Card> getCardsWhichShouldBeTakenByHeartsInNextMatch(List<Card> sortedPlayableCards) {
        CardProvider cardProvider = game.getCardProvider();

        List<Card> remainingCards = cardProvider.getShuffledDeckOfCards();
        remainingCards.removeAll(playedOutCards);
        remainingCards.removeAll(sortedPlayableCards);

        Set<Color> myColorsExceptHearts = sortedPlayableCards.stream()
                .map(Card::getColor)
                .filter(color -> color != Color.HEARTS)
                .collect(Collectors.toSet());

        Map<Color, Long> myColorToRemainingCounter = remainingCards.stream()
                .filter(card -> myColorsExceptHearts.contains(card.getColor()))
                .collect(groupingBy(Card::getColor, counting()));

        Map<Color, Long> myPlayableColorToRemainingCounter = myColorToRemainingCounter.entrySet().stream()
                .filter(entry -> users.size() - entry.getValue() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        //TODO filter otherUsersInfo: color or hearts

        if (myPlayableColorToRemainingCounter.isEmpty()) {
            return List.of();
        }

        Set<Color> myPlayableColors = new HashSet<>(myPlayableColorToRemainingCounter.keySet());

        return sortedPlayableCards.stream()
                .filter(card -> myPlayableColors.contains(card.getColor()))
                .toList();
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
		int winningCardIndex = game.getWinningIndex();
		return cardPlaceholders.get(winningCardIndex);
	}

	private Card getLeadingCard() {
		return cardPlaceholders.getFirst();
	}

	private Card getLowestCard(List<Card> cards) {
		List<Card> lowestRemainingCards = cards.stream()
				.filter(card -> isLowestRemainingCardInColor(cards, card))
				.toList();

		if (lowestRemainingCards.isEmpty()) {
			Card lowestCardInHand = cards.getFirst();
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
				Card highestCardInHand = cards.getLast();
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
				.filter(card -> !isLowestRemainingCardInColor(cards, card))
				.toList();

		if (cardsToGetRidOf.isEmpty()) {
			Card card = getRidOfColor(cards, cards);
			if (card == null) {
				return cards.getLast();
			}
			return card;
		}

		Card card = getRidOfColor(cardsToGetRidOf, cards);
		if (card == null) {
			return cards.getLast();
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
			return cards.getFirst();
		}

		List<Card> possibleWinnerCards = cards.stream()
				.filter(this::isPossibleColorToWin)
				.toList();

		if (possibleWinnerCards.isEmpty()) {
			return getMostProbableCardToWin(cards);
		}
		return getMostProbableCardToWin(possibleWinnerCards);
	}

	private Card getLowestCardToLose(List<Card> cards) {
		if (cards.size() == 1) {
			return cards.getFirst();
		}

		List<Card> possibleLoserCards = cards.stream()
				.filter(card -> isPossibleColorToLose(card.getColor()))
				.toList();

		if (possibleLoserCards.isEmpty()) {
			return getMostProbableCardToWin(cards);
		}
		return getMostProbableCardToWin(possibleLoserCards);
	}

	private boolean isPossibleColorToWin(Card card) {
		Map<User, UserInfo> followersInfo = getFollowersInfo();

		for (UserInfo userInfo : followersInfo.values()) {
			if (card.getColor() != Color.HEARTS && !userInfo.hasColor(card.getColor()) && userInfo.hasColor(Color.HEARTS)) {
				return false;
			}
		}
		return true;
	}

	private boolean isPossibleColorToLose(Color color) {
		Map<User, UserInfo> followersInfo = getFollowersInfo();

		for (UserInfo userInfo : followersInfo.values()) {
			if (userInfo.hasColor(color) || userInfo.hasColor(Color.HEARTS)) {
				return true;
			}
		}
		return false;
	}

	private Card getMostProbableCardToWin(List<Card> cards) {
		if (cards.size() == 1) {
			return cards.getFirst();
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

		UniformRandomProvider uniformRandomProvider = RandomSource.XO_RO_SHI_RO_128_PP.create();
		int index = uniformRandomProvider.nextInt(0, mostProbableCardsToWin.size());
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
		return lowerCards.getLast();
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
