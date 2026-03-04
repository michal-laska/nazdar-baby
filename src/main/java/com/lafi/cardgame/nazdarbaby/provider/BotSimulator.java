package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.mcts.MctsEngine;
import com.lafi.cardgame.nazdarbaby.mcts.SimulationState;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BotSimulator {

	private final Set<Card> playedOutCards = new HashSet<>();
	private final Map<User, Map<User, UserInfo>> botToOtherUsersInfo = new HashMap<>();
	private final Game game;
	private final MctsEngine mctsEngine = new MctsEngine();

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
			var expectedTakes = guessExpectedTakes();
			var expectedTakesRounded = (int) Math.round(expectedTakes);

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
			var activeUserCards = activeUser.getCards();

			var activeUserIndex = getActiveUserIndex();
			var selectedCard = selectCard(activeUserCards);
			cardPlaceholders.set(activeUserIndex, selectedCard);

			var cardIndex = activeUserCards.indexOf(selectedCard);
			activeUserCards.set(cardIndex, CardProvider.CARD_PLACEHOLDER);

			game.changeActiveUser();
		}
	}

	public double guessExpectedTakes() {
		List<Card> cards;
		if (activeUser.isBot()) {
			cards = activeUser.getCards();
		} else {
			var userProvider = game.getUserProvider();
			var currentUser = userProvider.getCurrentUser();

			cards = currentUser.getCards();
		}

		List<Card> nonPlaceholderCards = cards.stream()
				.filter(card -> !card.isPlaceholder())
				.toList();

		SimulationState state = buildPredictionState(nonPlaceholderCards);
		List<Card> unknownCards = computeUnknownCards(nonPlaceholderCards);
		int[] opponentSlots = computeOpponentSlots(nonPlaceholderCards.size());
		Map<Integer, Set<Color>> colorVoids = computeColorVoids();

		return mctsEngine.predictTakes(state, unknownCards, opponentSlots, colorVoids);
	}

	void removeColorsForOtherUsers(List<Card> cards) {
		int numberOfCardsInOneColor = deckOfCardsSize / Color.values().length;
		for (Color color : Color.values()) {
			long knownCardsInOneColorSize = Stream.concat(playedOutCards.stream(), cards.stream())
					.filter(card -> card.getColor() == color)
					.count();
			if (knownCardsInOneColorSize == numberOfCardsInOneColor) {
				Map<User, UserInfo> otherUsersInfo = botToOtherUsersInfo.get(activeUser);
				otherUsersInfo.values().forEach(userInfo -> userInfo.removeColor(color));
			}
		}
	}

	private Card selectCard(List<Card> cards) {
		removeColorsForOtherUsers(cards);

		List<Card> sortedPlayableCards = getSortedPlayableCards(cards);
		int sortedPlayableCardsSize = sortedPlayableCards.size();

		if (sortedPlayableCardsSize == 1) {
			return sortedPlayableCards.getFirst();
		}

		List<Card> nonPlaceholderCards = cards.stream()
				.filter(card -> !card.isPlaceholder())
				.toList();

		SimulationState state = buildPlayingState(nonPlaceholderCards);
		List<Card> unknownCards = computeUnknownCards(nonPlaceholderCards);
		int[] opponentSlots = computeOpponentSlots(nonPlaceholderCards.size());
		Map<Integer, Set<Color>> colorVoids = computeColorVoids();

		Card mctsCard = mctsEngine.selectCard(state, unknownCards, opponentSlots, colorVoids);

		// Fallback if MCTS returns null or an illegal card
		if (mctsCard == null || !sortedPlayableCards.contains(mctsCard)) {
			return sortedPlayableCards.getFirst();
		}
		return mctsCard;
	}

	private List<Card> getSortedPlayableCards(List<Card> cards) {
		Card leadingCard = cardPlaceholders.getFirst();
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
			// Collect void info before cards are forgotten — ensures voids from
			// completed tricks are captured even when placeholders get reset
			collectKnownInfoAboutUsers();

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

						if (userInfo.hasHearts() && card.getColor() != Color.HEARTS) {
							userInfo.removeColor(Color.HEARTS);
						}
					}
				}
			}
		}
	}

	private int getActiveUserIndex() {
		return users.indexOf(activeUser);
	}

	private SimulationState buildPredictionState(List<Card> botCards) {
		int activeUserIndex = getActiveUserIndex();

		List<List<Card>> hands = new ArrayList<>();
		int[] expectedTakesArr = new int[users.size()];
		int[] actualTakesArr = new int[users.size()];

		for (int i = 0; i < users.size(); i++) {
			if (i == activeUserIndex) {
				hands.add(new ArrayList<>(botCards));
			} else {
				// Opponent hand size (same as bot — at prediction start all have same count)
				hands.add(new ArrayList<>());
			}
			User user = users.get(i);
			expectedTakesArr[i] = user.getExpectedTakes() != null ? user.getExpectedTakes() : 0;
			actualTakesArr[i] = user.getActualTakes();
		}

		// Count how many predictions are already done
		int predictionsDone = 0;
		for (User user : users) {
			if (user.getExpectedTakes() != null) {
				predictionsDone++;
			}
		}

		SimulationState state = new SimulationState(
				hands, expectedTakesArr, actualTakesArr,
				new ArrayList<>(),
				SimulationState.Phase.PREDICTING,
				0, // leadPlayerIndex — first player in match
				activeUserIndex,
				0, // tricksPlayed
				botCards.size(), // totalTricks
				activeUserIndex,
				predictionsDone
		);

		// Mark players who have already predicted as known — MCTS won't search over these
		for (int i = 0; i < users.size(); i++) {
			if (users.get(i).getExpectedTakes() != null) {
				state.setKnownPrediction(i);
			}
		}

		return state;
	}

	private SimulationState buildPlayingState(List<Card> botCards) {
		int activeUserIndex = getActiveUserIndex();

		List<List<Card>> hands = new ArrayList<>();
		int[] expectedTakesArr = new int[users.size()];
		int[] actualTakesArr = new int[users.size()];

		for (int i = 0; i < users.size(); i++) {
			if (i == activeUserIndex) {
				hands.add(new ArrayList<>(botCards));
			} else {
				hands.add(new ArrayList<>());
			}
			User user = users.get(i);
			expectedTakesArr[i] = user.getExpectedTakes() != null ? user.getExpectedTakes() : 0;
			actualTakesArr[i] = user.getActualTakes();
		}

		// Current trick: cards already on table (non-placeholder)
		List<Card> currentTrick = new ArrayList<>();
		for (Card card : cardPlaceholders) {
			if (!card.isPlaceholder()) {
				currentTrick.add(card);
			} else {
				break;
			}
		}

		// Lead player is the first player in this trick (index 0 after rotation)
		int leadPlayerIndex = 0;

		// Total completed tricks = sum of all players' actual takes / 1 (each trick has exactly one winner)
		// Simplest: sum all actualTakes — each completed trick increments exactly one player
		int tricksPlayed = 0;
		for (User user : users) {
			tricksPlayed += user.getActualTakes();
		}

		// Total tricks in the set = cards in hand + completed tricks + in-progress trick
		boolean trickInProgress = !cardPlaceholders.getFirst().isPlaceholder();
		int totalTricks = botCards.size() + tricksPlayed + (trickInProgress ? 1 : 0);

		return new SimulationState(
				hands, expectedTakesArr, actualTakesArr,
				currentTrick,
				SimulationState.Phase.PLAYING,
				leadPlayerIndex,
				activeUserIndex,
				tricksPlayed,
				totalTricks,
				activeUserIndex,
				users.size() // all predictions done
		);
	}

	private List<Card> computeUnknownCards(List<Card> botCards) {
		CardProvider cardProvider = game.getCardProvider();
		List<Card> allCards = cardProvider.getShuffledDeckOfCards();

		allCards.removeAll(botCards);
		allCards.removeAll(playedOutCards);

		// Also remove cards currently on the table
		for (Card card : cardPlaceholders) {
			if (!card.isPlaceholder()) {
				allCards.remove(card);
			}
		}

		return allCards;
	}

	private int[] computeOpponentSlots(int botCardCount) {
		int[] slots = new int[users.size()];
		int activeUserIndex = getActiveUserIndex();

		for (int i = 0; i < users.size(); i++) {
			if (i == activeUserIndex) {
				slots[i] = 0; // bot's slot — not dealt by determinizer
			} else {
				long cardsInHand = users.get(i).getCards().stream()
						.filter(card -> !card.isPlaceholder())
						.count();
				// If opponent hand size is unknown (e.g. during prediction),
				// assume same count as bot
				slots[i] = cardsInHand > 0 ? (int) cardsInHand : botCardCount;
			}
		}
		return slots;
	}

	private Map<Integer, Set<Color>> computeColorVoids() {
		Map<Integer, Set<Color>> voids = new HashMap<>();
		int activeUserIndex = getActiveUserIndex();

		Map<User, UserInfo> otherUsersInfo = botToOtherUsersInfo.getOrDefault(activeUser, Map.of());

		for (int i = 0; i < users.size(); i++) {
			if (i == activeUserIndex) {
				continue;
			}
			User user = users.get(i);
			UserInfo info = otherUsersInfo.get(user);
			if (info != null) {
				Set<Color> voided = new HashSet<>();
				for (Color color : Color.values()) {
					if (!info.hasColor(color)) {
						voided.add(color);
					}
				}
				if (!voided.isEmpty()) {
					voids.put(i, voided);
				}
			}
		}

		return voids;
	}

	private static final class UserInfo {

		private final Set<Color> colorsInHand = new HashSet<>(Arrays.asList(Color.values()));

		private boolean hasHearts() {
			return hasColor(Color.HEARTS);
		}

		private boolean hasColor(Color color) {
			return colorsInHand.contains(color);
		}

		private void removeColor(Color color) {
			colorsInHand.remove(color);
		}
	}
}
