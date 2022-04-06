package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

class BotSimulator {

	private final Set<Card> playedOutCards = new HashSet<>();
	private final Random random = new Random();
	private final Game game;

	private List<Card> cardPlaceholders;
	private User activeUser;
	private List<User> matchUsers;
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

	void setMatchUsers(List<User> matchUsers) {
		this.matchUsers = matchUsers;
		playedOutCards.clear();
	}

	void setDeckOfCardsSize(int deckOfCardsSize) {
		this.deckOfCardsSize = deckOfCardsSize;
	}

	void tryBotMove() {
		if (!activeUser.isBot()) {
			return;
		}

		if (activeUser.getExpectedTakes() == null) {
			double expectedTakes = guessExpectedTakes();
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

			List<Card> cards = activeUser.getCards();
			Card selectedCard = selectBotCard(cards);

			cardPlaceholders.set(matchUserIndex, selectedCard);

			int cardIndex = cards.indexOf(selectedCard);
			cards.set(cardIndex, CardProvider.CARD_PLACEHOLDER);

			game.changeActiveUser();
		}
	}

	private double guessExpectedTakes() {
		int highestCardValue = 14; //TODO do it better
		int oneColorCardsSize = deckOfCardsSize / Color.values().length;
		double magicNumber = highestCardValue - ((double) oneColorCardsSize / matchUsers.size()) + 1;

		double guess = 0;
		for (Card card : activeUser.getCards()) {
			int cardValue = card.getValue();
			double diff = magicNumber - cardValue;

			if (cardValue > magicNumber) {
				++guess;
			} else if (diff < 1) {
				guess += diff;
			}
		}

		return guess;
	}

	private Card selectBotCard(List<Card> cards) {
		Card leadingCard = cardPlaceholders.get(0);
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

		List<Card> playableCards = playableCardStream.toList();
		int playableCardsSize = playableCards.size();

		if (playableCardsSize == 1) {
			return playableCards.get(0);
		}

		if (noGapsInOneColor(playableCards)) {
			int randomIndex = random.nextInt(playableCardsSize);
			return playableCards.get(randomIndex);
		}

		//TODO implement logic
		int randomIndex = random.nextInt(playableCardsSize);
		return playableCards.get(randomIndex);
	}

	private void rememberCardsFromTable() {
		if (cardPlaceholders != null) {
			playedOutCards.addAll(cardPlaceholders);
			playedOutCards.remove(CardProvider.CARD_PLACEHOLDER);
		}
	}

	private boolean noGapsInOneColor(List<Card> playableCards) {
		Card firstPlayableCard = playableCards.get(0);

		boolean playableCardsInOneColor = playableCards.stream().allMatch(card -> card.getColor() == firstPlayableCard.getColor());
		if (!playableCardsInOneColor) {
			return false;
		}

		List<Card> playedOutCardsInSameColor = playedOutCards.stream()
				.filter(card -> card.getColor() == firstPlayableCard.getColor())
				.toList();

		List<Card> knownCardsInOneColor = new ArrayList<>(playableCards);
		knownCardsInOneColor.addAll(playedOutCardsInSameColor);

		Collections.sort(knownCardsInOneColor);
		Card firstCard = knownCardsInOneColor.get(0);

		for (int i = 1; i < knownCardsInOneColor.size(); ++i) {
			Card card = knownCardsInOneColor.get(i);
			if (firstCard.getValue() + i != card.getValue()) {
				return false;
			}
		}
		return true;
	}
}
