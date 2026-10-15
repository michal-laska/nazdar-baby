package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * Monte Carlo search over determinized deals: a tree search (Information Set MCTS) picks the card to play,
 * flat playouts give each prediction's chance.
 */
public final class MctsEngine {

	public static final Comparator<Map.Entry<Integer, Double>> LIKELIEST_FIRST =
			Map.Entry.<Integer, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey());

	private static final int ITERATIONS_PER_CARD = 2000;
	private static final int MIN_DETERMINIZATIONS = 40;
	private static final int DETERMINIZATIONS_PER_CARD = 12;
	private static final int DETERMINIZATIONS_PER_OPPONENT = 20;
	private static final double EXPLORATION_CONSTANT = 1.2;
	// About 2 points of noise in the chances; the card search's budget made a prediction cost as much as a card move
	private static final int PREDICTION_CHANCE_WORLDS = 1000;
	// A playout per card and world: at 0.1 CPU a prediction stays under 3 s with 6 cards, or 7 cards with up to 4 players;
	// 7 cards took 3.2 s with 5 players and 4.2 s with 7
	private static final int MAX_HAND_TO_CHOOSE_FIRST_CARD = 6;
	private static final int MAX_HAND_TO_CHOOSE_FIRST_CARD_WITH_FEW_PLAYERS = 7;
	private static final int MAX_FEW_PLAYERS = 4;
	// ./gradlew hintCalibration re-fits both calibrations.
	// Within the first-card limit the hint's second option was matched about 2 points more often than shown, up to 6
	// with 6 or 7 cards
	private static final Map<Integer, LogOddsCalibration> SMALL_HAND_HINT_CALIBRATION_BY_PLAYERS = Map.of(
			3, new LogOddsCalibration(-0.055, 0.916),
			4, new LogOddsCalibration(-0.002, 0.945),
			5, new LogOddsCalibration(0.037, 0.955),
			6, new LogOddsCalibration(0.088, 0.894),
			7, new LogOddsCalibration(0.061, 0.865));
	// Above the limit the policy plays the first card and the hint's chances are too flat: with bots in every seat a
	// shown 15% was matched about 30% of the time and, with 3 players, a shown 84% about 64%
	private static final Map<Integer, LogOddsCalibration> LARGE_HAND_HINT_CALIBRATION_BY_PLAYERS = Map.of(
			3, new LogOddsCalibration(-0.092, 0.448),
			4, new LogOddsCalibration(0.172, 0.715),
			5, new LogOddsCalibration(0.332, 0.678),
			6, new LogOddsCalibration(0.344, 0.671),
			7, new LogOddsCalibration(0.367, 0.676));
	private static final Comparator<Card> LOWEST_FIRST = Comparator.comparingInt(Card::getValue).thenComparing(Card::getColor);

	public MctsEngine() {
	}

	private int computeIterations(int handSize, int opponents) {
		return ITERATIONS_PER_CARD * handSize * opponents;
	}

	private int computeDeterminizations(int handSize, int opponents) {
		return Math.max(MIN_DETERMINIZATIONS,
				DETERMINIZATIONS_PER_CARD * handSize + DETERMINIZATIONS_PER_OPPONENT * opponents);
	}

	/**
	 * Select the best card to play using MCTS with determinization.
	 */
	public Card selectCard(SimulationState baseState, List<Card> unknownCards,
						   int[] opponentSlots, Map<Integer, Set<Color>> colorVoids,
						   Map<Integer, Set<Card>> excludedCards) {
		// Skip simulations when all legal cards are equivalent
		List<Card> legalCards = baseState.getLegalActions().stream()
				.filter(MctsAction.PlayCard.class::isInstance)
				.map(a -> ((MctsAction.PlayCard) a).card())
				.sorted()
				.toList();
		if (allEquivalent(legalCards, unknownCards)) {
			return legalCards.getFirst();
		}

		int handSize = baseState.getHand(baseState.getBotPlayerIndex()).size();
		int opponents = baseState.getTotalPlayers() - 1;
		int iterations = computeIterations(handSize, opponents);
		int determinizations = computeDeterminizations(handSize, opponents);
		Map<Card, double[]> cardStats = new HashMap<>();
		int iterationsPerWorld = iterations / determinizations;

		for (int d = 0; d < determinizations; d++) {
			SimulationState state = createDeterminizedState(baseState, unknownCards, opponentSlots, colorVoids, excludedCards);

			MctsNode root = new MctsNode(null, null, RolloutPolicy.prioritizeActions(state, state.getLegalActions()));

			for (int i = 0; i < iterationsPerWorld; i++) {
				runIteration(root, state.deepCopy());
			}

			aggregateCardResults(root, cardStats);
		}

		return bestCard(cardStats);
	}

	/**
	 * The prediction most likely to be matched, the lower one on equal chances.
	 */
	public int predictTakes(SimulationState baseState, List<Card> unknownCards,
							int[] opponentSlots, Map<Integer, Set<Color>> colorVoids,
							Map<Integer, Set<Card>> excludedCards) {
		return predictionChances(baseState, unknownCards, opponentSlots, colorVoids, excludedCards).entrySet().stream()
				.min(LIKELIEST_FIRST)
				.orElseThrow()
				.getKey();
	}

	/**
	 * Chance of matching each allowed prediction. Every world is played out by the rollout policy
	 * alone: a tree search sees the other hands in each world and overrates every prediction.
	 * A small hand plays its first card for the best chance instead of the policy's.
	 */
	public Map<Integer, Double> predictionChances(SimulationState baseState, List<Card> unknownCards,
												  int[] opponentSlots, Map<Integer, Set<Color>> colorVoids,
												  Map<Integer, Set<Card>> excludedCards) {
		baseState.setPredictionMode(true);

		List<MctsAction> predictions = baseState.getLegalActions();
		List<Card> hand = baseState.getHand(baseState.getBotPlayerIndex());
		boolean choosesFirstCard = hand.size() > 1 && hand.size() <= maxHandToChooseFirstCard(baseState.getTotalPlayers());

		Map<Integer, double[]> hits = new TreeMap<>();
		for (int w = 0; w < PREDICTION_CHANCE_WORLDS; w++) {
			SimulationState world = createDeterminizedState(baseState, unknownCards, opponentSlots, colorVoids, excludedCards);

			for (MctsAction prediction : predictions) {
				SimulationState state = world.deepCopy();
				state.applyAction(prediction);
				double[] worldHits = choosesFirstCard
						? hitsByFirstCard(state, hand)
						: new double[]{RolloutPolicy.rollout(state)};
				hits.merge(((MctsAction.PredictTakes) prediction).takes(), worldHits, MctsEngine::addUp);
			}
		}

		Map<Integer, Double> chances = new TreeMap<>();
		hits.forEach((takes, hitsPerFirstCard) ->
				chances.put(takes, Arrays.stream(hitsPerFirstCard).max().orElseThrow() / PREDICTION_CHANCE_WORLDS));
		return chances;
	}

	/**
	 * The chances as the hint shows them: moved toward how often bots in every seat match them.
	 */
	public Map<Integer, Double> hintChances(SimulationState baseState, List<Card> unknownCards,
											int[] opponentSlots, Map<Integer, Set<Color>> colorVoids,
											Map<Integer, Set<Card>> excludedCards) {
		int players = baseState.getTotalPlayers();
		int handSize = baseState.getHand(baseState.getBotPlayerIndex()).size();

		Map<Integer, Double> chances = predictionChances(baseState, unknownCards, opponentSlots, colorVoids, excludedCards);
		chances.replaceAll((takes, chance) -> hintChance(chance, players, handSize));
		return chances;
	}

	static double hintChance(double chance, int players, int handSize) {
		Map<Integer, LogOddsCalibration> calibrations = exceedsFirstCardLimit(players, handSize)
				? LARGE_HAND_HINT_CALIBRATION_BY_PLAYERS
				: SMALL_HAND_HINT_CALIBRATION_BY_PLAYERS;
		return calibrations.get(players).apply(chance);
	}

	record LogOddsCalibration(double shift, double scale) {

		double apply(double chance) {
			return 1 / (1 + Math.exp(-(shift + scale * Math.log(chance / (1 - chance)))));
		}
	}

	/**
	 * Round a guess to a prediction the game allows; older engines seated by the bot arena predict fractions.
	 */
	public static int allowedPrediction(double guess, IntPredicate isForbidden) {
		int takes = (int) Math.round(guess);
		if (!isForbidden.test(takes)) {
			return takes;
		}

		return guess > takes || takes == 0 ? takes + 1 : takes - 1;
	}

	static boolean exceedsFirstCardLimit(int players, int handSize) {
		return handSize > maxHandToChooseFirstCard(players);
	}

	private static int maxHandToChooseFirstCard(int players) {
		return players <= MAX_FEW_PLAYERS ? MAX_HAND_TO_CHOOSE_FIRST_CARD_WITH_FEW_PLAYERS : MAX_HAND_TO_CHOOSE_FIRST_CARD;
	}

	/**
	 * A playout for each first card of the bot; one it may not play in this world is left to the policy.
	 */
	private static double[] hitsByFirstCard(SimulationState state, List<Card> hand) {
		RolloutPolicy.playUntilTurnOf(state, state.getBotPlayerIndex());

		double[] hits = new double[hand.size()];
		for (int c = 0; c < hand.size(); c++) {
			SimulationState withFirstCard = state.deepCopy();
			MctsAction firstCard = new MctsAction.PlayCard(hand.get(c));
			if (withFirstCard.getLegalActions().contains(firstCard)) {
				withFirstCard.applyAction(firstCard);
			}
			hits[c] = RolloutPolicy.rollout(withFirstCard);
		}
		return hits;
	}

	private static double[] addUp(double[] sums, double[] values) {
		Arrays.setAll(sums, i -> sums[i] + values[i]);
		return sums;
	}

	private SimulationState createDeterminizedState(SimulationState baseState, List<Card> unknownCards,
													int[] opponentSlots, Map<Integer, Set<Color>> colorVoids,
													Map<Integer, Set<Card>> excludedCards) {
		SimulationState state = baseState.deepCopy();
		int botIndex = state.getBotPlayerIndex();

		int[] neededTakes = state.getOpponentNeededTakes();

		List<List<Card>> sampledHands = Determinizer.sampleOpponentHands(
				unknownCards, opponentSlots, colorVoids, botIndex, neededTakes, excludedCards);

		// Replace opponent hands in the state copy
		for (int i = 0; i < state.getTotalPlayers(); i++) {
			if (i != botIndex) {
				List<Card> hand = state.getHand(i);
				hand.clear();
				hand.addAll(sampledHands.get(i));
			}
		}

		// Every simulated opponent needs a prediction to play towards; a heuristic on its dealt hand gives it one
		if (state.getPhase() == SimulationState.Phase.PREDICTING) {
			for (int i = 0; i < state.getTotalPlayers(); i++) {
				if (i != botIndex && !state.isKnownPrediction(i)) {
					boolean isLeader = i == state.getLeadPlayerIndex();
					int estimate = RolloutPolicy.estimateTakes(state.getHand(i), state.getTotalPlayers(), isLeader);
					state.setFixedPrediction(i, estimate);
				}
			}
		}

		return state;
	}

	private void runIteration(MctsNode node, SimulationState state) {
		// Selection
		while (node.isFullyExpanded() && node.hasChildren()) {
			node = node.selectChildUcb1(EXPLORATION_CONSTANT);
			state.applyAction(node.getAction());
		}

		// Expansion
		if (node.hasUntriedActions() && !state.isTerminal()) {
			MctsAction action = node.getUntriedAction();
			double heuristic = RolloutPolicy.heuristicValue(state, action);
			state.applyAction(action);
			List<MctsAction> childActions = state.isTerminal()
					? List.of()
					: RolloutPolicy.prioritizeActions(state, state.getLegalActions());
			node = node.addChild(action, childActions, heuristic);
		}

		// Rollout
		double reward = RolloutPolicy.rollout(state);

		// Backpropagation
		while (node != null) {
			node.update(reward);
			node = node.getParent();
		}
	}

	private void aggregateCardResults(MctsNode root, Map<Card, double[]> cardStats) {
		for (MctsNode child : root.getChildren()) {
			if (child.getAction() instanceof MctsAction.PlayCard(Card card)) {
				double[] stats = cardStats.computeIfAbsent(card, k -> new double[2]);
				stats[0] += child.getTotalReward();
				stats[1] += child.getVisitCount();
			}
		}
	}

	private Card bestCard(Map<Card, double[]> cardStats) {
		Card bestCard = null;
		double bestAvg = Double.NEGATIVE_INFINITY;

		for (Map.Entry<Card, double[]> entry : cardStats.entrySet()) {
			double[] stats = entry.getValue();
			double avg = stats[1] > 0 ? stats[0] / stats[1] : 0;
			// Exact ties are equivalent cards; taking the lowest keeps the pick independent of hash order
			if (avg > bestAvg || avg == bestAvg && LOWEST_FIRST.compare(entry.getKey(), bestCard) < 0) {
				bestAvg = avg;
				bestCard = entry.getKey();
			}
		}

		return bestCard;
	}

	/**
	 * Cards are equivalent when they are all the same color and no opponent
	 * can hold a card between the lowest and highest — so the choice among
	 * them can never affect the outcome. A gap is allowed if the missing
	 * card is not in unknownCards (i.e. it was already played or is on the table).
	 */
	private boolean allEquivalent(List<Card> sortedCards, List<Card> unknownCards) {
		if (sortedCards.size() <= 1) {
			return true;
		}
		Color color = sortedCards.getFirst().getColor();
		int min = sortedCards.getFirst().getValue();
		int max = sortedCards.getLast().getValue();
		for (Card card : sortedCards) {
			if (card.getColor() != color) {
				return false;
			}
		}
		// Check that no opponent can hold a card in the gap
		for (Card card : unknownCards) {
			if (card.getColor() == color && card.getValue() > min && card.getValue() < max) {
				return false;
			}
		}
		return true;
	}
}
