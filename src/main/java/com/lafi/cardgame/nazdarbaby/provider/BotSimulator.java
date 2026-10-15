package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.mcts.MctsEngine;
import com.lafi.cardgame.nazdarbaby.mcts.SimulationState;
import com.lafi.cardgame.nazdarbaby.mcts.TrickEvaluator;
import com.lafi.cardgame.nazdarbaby.user.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotSimulator {

	private static final Logger LOGGER = LoggerFactory.getLogger(BotSimulator.class);

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
						.collect(Collectors.toMap(Function.identity(), _ -> new UserInfo()));
				botToOtherUsersInfo.put(theUser, otherUsersInfo);
			}
		}
	}

	void setDeckOfCardsSize(int deckOfCardsSize) {
		this.deckOfCardsSize = deckOfCardsSize;
	}

	/**
	 * The active bot's next move, or null when no bot is to move. Called under Game's monitor.
	 */
	BotMove prepareMove() {
		collectKnownInfoAboutUsers();

		if (activeUser == null || !activeUser.isBot()) {
			return null;
		}

		User bot = activeUser;
		if (bot.getExpectedTakes() == null) {
			SearchInputs inputs = predictionInputs(bot);
			return new BotMove(() -> {
				int guess = inputs.run(mctsEngine::predictTakes);
				return () -> predict(bot, guess);
			}, () -> predict(bot, 0));
		}

		return prepareCardPlay(bot);
	}

	public Map<Integer, Double> predictionChancesForCurrentUser() {
		SearchInputs inputs;
		synchronized (game) {
			inputs = predictionInputs(game.getUserProvider().getCurrentUser());
		}

		return inputs.run(mctsEngine::predictionChances);
	}

	private SearchInputs predictionInputs(User predictor) {
		List<Card> cardsInHand = predictor.getCardsInHand();
		return searchInputs(buildPredictionState(cardsInHand, predictor), cardsInHand, predictor);
	}

	private void predict(User bot, int guess) {
		bot.setExpectedTakes(MctsEngine.allowedPrediction(guess, game::isLastUserWithInvalidExpectedTakes));
		game.afterActiveUserSetExpectedTakes();
	}

	private void playCard(User bot, Card card) {
		List<Card> cards = bot.getCards();
		cardPlaceholders.set(users.indexOf(bot), card);
		cards.set(cards.indexOf(card), CardProvider.CARD_PLACEHOLDER);

		game.changeActiveUser();
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

	private BotMove prepareCardPlay(User bot) {
		removeColorsForOtherUsers(bot.getCards());

		List<Card> cardsInHand = bot.getCardsInHand();

		List<Card> sortedPlayableCards = TrickEvaluator.getLegalPlays(cardsInHand, TrickEvaluator.playedCards(cardPlaceholders)).stream()
				.sorted()
				.toList();
		Runnable playFirstLegalCard = () -> playCard(bot, sortedPlayableCards.getFirst());

		if (sortedPlayableCards.size() == 1) {
			return new BotMove(() -> playFirstLegalCard, playFirstLegalCard);
		}

		SearchInputs inputs = searchInputs(buildPlayingState(cardsInHand), cardsInHand, bot);
		return new BotMove(() -> {
			Card mctsCard = inputs.run(mctsEngine::selectCard);

			// Fallback if MCTS returns null or an illegal card
			if (mctsCard == null || !sortedPlayableCards.contains(mctsCard)) {
				return playFirstLegalCard;
			}
			return () -> playCard(bot, mctsCard);
		}, playFirstLegalCard);
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

		// Infer card value caps from completed tricks not yet processed
		boolean trickComplete = cardPlaceholders.stream().noneMatch(Card::isPlaceholder);
		if (trickComplete && !playedOutCards.contains(leadingCard)) {
			inferCardValueCaps();
		}
	}

	/**
	 * When the last player in a trick follows the winning card's suit but plays
	 * a lower value while needing tricks, they almost certainly don't have higher
	 * cards of that suit — being last, there's no strategic reason to hold back.
	 */
	private void inferCardValueCaps() {
		int winnerIndex = TrickEvaluator.getWinningIndex(cardPlaceholders);
		Card winningCard = cardPlaceholders.get(winnerIndex);

		int lastIndex = cardPlaceholders.size() - 1;
		if (lastIndex == winnerIndex) {
			return; // Last player won — no inference
		}

		Card lastCard = cardPlaceholders.get(lastIndex);
		if (lastCard.getColor() != winningCard.getColor()) {
			return; // Different suits — can't infer value bounds
		}

		User lastUser = users.get(lastIndex);
		Integer expectedTakes = lastUser.getExpectedTakes();
		if (expectedTakes == null) {
			return;
		}
		int needed = expectedTakes - lastUser.getActualTakes();
		if (needed <= 0) {
			return; // Doesn't need more tricks — may have played low deliberately
		}

		int remainingTricks = lastUser.getCardsInHand().size();
		if (needed != remainingTricks) {
			return; // Can afford to skip (needed < remaining) or already lost (needed > remaining)
		}

		// Last player needed tricks, played same suit as winner but lower value
		// → they don't have cards of that suit above the winning card's value
		Color capColor = winningCard.getColor();
		int capValue = winningCard.getValue();

		for (Map<User, UserInfo> otherUsersInfo : botToOtherUsersInfo.values()) {
			UserInfo userInfo = otherUsersInfo.get(lastUser);
			if (userInfo != null) {
				userInfo.capCardValue(capColor, capValue);
			}
		}
	}

	private int getActiveUserIndex() {
		return users.indexOf(activeUser);
	}

	private SimulationState buildPredictionState(List<Card> ownCards, User predictor) {
		int predictorIndex = users.indexOf(predictor);
		var base = buildBaseState(ownCards, predictorIndex);

		// Count how many predictions are already done
		int predictionsDone = 0;
		for (boolean predicted : base.predicted) {
			if (predicted) {
				predictionsDone++;
			}
		}

		SimulationState state = new SimulationState(
				base.hands, base.expectedTakes, base.actualTakes,
				new ArrayList<>(),
				SimulationState.Phase.PREDICTING,
				0, // leadPlayerIndex — first player in trick
				predictorIndex,
				0, // tricksPlayed
				ownCards.size(), // totalTricks
				predictorIndex,
				predictionsDone
		);

		// Mark players who have already predicted as known — MCTS won't search over these
		for (int i = 0; i < users.size(); i++) {
			if (base.predicted[i]) {
				state.setKnownPrediction(i);
			}
		}

		return state;
	}

	private SimulationState buildPlayingState(List<Card> botCards) {
		int activeUserIndex = getActiveUserIndex();
		var base = buildBaseState(botCards, activeUserIndex);

		List<Card> currentTrick = TrickEvaluator.playedCards(cardPlaceholders);

		int tricksPlayed = 0;
		for (User user : users) {
			tricksPlayed += user.getActualTakes();
		}

		int totalTricks = botCards.size() + tricksPlayed;

		SimulationState state = new SimulationState(
				base.hands, base.expectedTakes, base.actualTakes,
				currentTrick,
				SimulationState.Phase.PLAYING,
				0, // leadPlayerIndex
				activeUserIndex,
				tricksPlayed,
				totalTricks,
				activeUserIndex,
				users.size() // all predictions done
		);

		// All predictions are known during play — mark them so the determinizer
		// uses prediction plausibility filtering when sampling opponent hands
		for (int i = 0; i < users.size(); i++) {
			state.setKnownPrediction(i);
		}

		return state;
	}

	private BaseState buildBaseState(List<Card> ownCards, int ownIndex) {
		List<List<Card>> hands = new ArrayList<>();
		int[] expectedTakes = new int[users.size()];
		int[] actualTakes = new int[users.size()];
		boolean[] predicted = new boolean[users.size()];

		for (int i = 0; i < users.size(); i++) {
			hands.add(i == ownIndex ? new ArrayList<>(ownCards) : new ArrayList<>());
			User user = users.get(i);
			// Read once: a human sets their prediction outside Game's monitor
			Integer prediction = user.getExpectedTakes();
			predicted[i] = prediction != null;
			expectedTakes[i] = predicted[i] ? prediction : 0;
			actualTakes[i] = user.getActualTakes();
		}

		return new BaseState(hands, expectedTakes, actualTakes, predicted);
	}

	private record BaseState(List<List<Card>> hands, int[] expectedTakes, int[] actualTakes, boolean[] predicted) {
	}

	private List<Card> computeUnknownCards(List<Card> ownCards) {
		CardProvider cardProvider = game.getCardProvider();
		List<Card> allCards = cardProvider.getShuffledDeckOfCards();

		allCards.removeAll(ownCards);
		allCards.removeAll(playedOutCards);

		// Also remove cards currently on the table
		for (Card card : cardPlaceholders) {
			if (!card.isPlaceholder()) {
				allCards.remove(card);
			}
		}

		return allCards;
	}

	private int[] computeOpponentSlots(int ownCardCount, User self) {
		int[] slots = new int[users.size()];
		int selfIndex = users.indexOf(self);

		for (int i = 0; i < users.size(); i++) {
			if (i == selfIndex) {
				slots[i] = 0; // own slot — not dealt by determinizer
			} else {
				int cardsInHand = users.get(i).getCardsInHand().size();
				// If opponent hand size is unknown (e.g. during prediction),
				// assume same count as own hand
				slots[i] = cardsInHand > 0 ? cardsInHand : ownCardCount;
			}
		}
		return slots;
	}

	private Map<Integer, Set<Card>> computeExcludedCards(List<Card> unknownCards, User self) {
		Map<Integer, Set<Card>> excluded = new HashMap<>();
		int selfIndex = users.indexOf(self);

		Map<User, UserInfo> otherUsersInfo = botToOtherUsersInfo.getOrDefault(self, Map.of());

		for (int i = 0; i < users.size(); i++) {
			if (i == selfIndex) {
				continue;
			}
			User user = users.get(i);
			UserInfo info = otherUsersInfo.get(user);
			if (info == null) {
				continue;
			}

			Map<Color, Integer> caps = info.getCardValueCaps();
			if (caps.isEmpty()) {
				continue;
			}

			Set<Card> playerExcluded = new HashSet<>();
			for (Card card : unknownCards) {
				Integer cap = caps.get(card.getColor());
				if (cap != null && card.getValue() > cap) {
					playerExcluded.add(card);
				}
			}

			if (!playerExcluded.isEmpty()) {
				excluded.put(i, playerExcluded);
			}
		}

		return excluded;
	}

	private Map<Integer, Set<Color>> computeColorVoids(User self) {
		Map<Integer, Set<Color>> voids = new HashMap<>();
		int selfIndex = users.indexOf(self);

		Map<User, UserInfo> otherUsersInfo = botToOtherUsersInfo.getOrDefault(self, Map.of());

		for (int i = 0; i < users.size(); i++) {
			if (i == selfIndex) {
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

	private SearchInputs searchInputs(SimulationState state, List<Card> cardsInHand, User self) {
		List<Card> unknownCards = computeUnknownCards(cardsInHand);
		return new SearchInputs(state, unknownCards, computeOpponentSlots(cardsInHand.size(), self),
				computeColorVoids(self), computeExcludedCards(unknownCards, self));
	}

	/**
	 * @param fallback a legal move that needs no search, applied when the search fails
	 */
	record BotMove(Supplier<Runnable> searcher, Runnable fallback) {

		/**
		 * Runs without Game's monitor and returns what to apply under it.
		 */
		Runnable search() {
			try {
				return searcher.get();
			} catch (RuntimeException e) {
				LOGGER.error("Bot move search failed, playing the fallback move", e);
				return fallback;
			}
		}
	}

	private interface Search<T> {

		T run(SimulationState state, List<Card> unknownCards, int[] opponentSlots,
			  Map<Integer, Set<Color>> colorVoids, Map<Integer, Set<Card>> excludedCards);
	}

	private record SearchInputs(SimulationState state, List<Card> unknownCards, int[] opponentSlots,
								Map<Integer, Set<Color>> colorVoids, Map<Integer, Set<Card>> excludedCards) {

		<T> T run(Search<T> search) {
			return search.run(state, unknownCards, opponentSlots, colorVoids, excludedCards);
		}
	}

	private static final class UserInfo {

		private final Set<Color> colorsInHand = new HashSet<>(Arrays.asList(Color.values()));
		// Upper bound on card value per color: player has no cards of this color with value > bound
		private final Map<Color, Integer> cardValueCaps = new HashMap<>();

		private boolean hasHearts() {
			return hasColor(Color.HEARTS);
		}

		private boolean hasColor(Color color) {
			return colorsInHand.contains(color);
		}

		private void removeColor(Color color) {
			colorsInHand.remove(color);
		}

		private void capCardValue(Color color, int maxValue) {
			cardValueCaps.merge(color, maxValue, Math::min);
		}

		private Map<Color, Integer> getCardValueCaps() {
			return cardValueCaps;
		}
	}
}
