package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

class BotSimulator {

	private final Set<Card> playedOutCards = new HashSet<>();
	private final Game game;

	private List<Card> cardPlaceholders;
	private User activeUser;
	private List<User> matchUsers;

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

	void tryBotMove() {
		if (!activeUser.isBot()) {
			return;
		}

		if (activeUser.getExpectedTakes() == null) {
			//TODO implement logic
			int expectedTakes = 1;
			if (game.isLastUserWithInvalidExpectedTakes(expectedTakes)) {
				activeUser.setExpectedTakes(expectedTakes - 1);
			} else {
				activeUser.setExpectedTakes(expectedTakes);
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

	private Card selectBotCard(List<Card> cards) {
		Card leadingCard = cardPlaceholders.get(0);
		Color leadingCardColor = leadingCard.getColor();

		Stream<Card> validCardStream = cards.stream();
		if (leadingCard.isPlaceholder()) {
			validCardStream = validCardStream.filter(card -> !card.isPlaceholder());
		} else if (activeUser.hasColor(leadingCardColor)) {
			validCardStream = validCardStream.filter(card -> card.getColor() == leadingCardColor);
		} else if (activeUser.hasColor(Color.HEARTS)) {
			validCardStream = validCardStream.filter(card -> card.getColor() == Color.HEARTS);
		} else {
			validCardStream = validCardStream.filter(card -> !card.isPlaceholder());
		}
		List<Card> validCards = validCardStream.toList();

		int validCardSize = validCards.size();
		if (validCardSize == 1) {
			return validCards.get(0);
		}

		//TODO gaps

		//TODO implement logic
		Random random = new Random();
		int randomIndex = random.nextInt(validCardSize);
		return validCards.get(randomIndex);
	}

	private void rememberCardsFromTable() {
		if (cardPlaceholders != null) {
			playedOutCards.addAll(cardPlaceholders);
			playedOutCards.remove(CardProvider.CARD_PLACEHOLDER);
		}
	}
}
