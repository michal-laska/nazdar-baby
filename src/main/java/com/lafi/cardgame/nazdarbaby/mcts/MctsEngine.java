package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monte Carlo Tree Search with determinization (Information Set MCTS).
 * Orchestrates sampling of opponent hands, MCTS iterations, and result aggregation.
 */
public final class MctsEngine {

	private static final int ITERATIONS_PER_CARD = 2000;
	private static final int MIN_DETERMINIZATIONS = 10;
	private static final int DETERMINIZATIONS_PER_CARD = 3;
	private static final int DETERMINIZATIONS_PER_OPPONENT = 5;
	private static final double EXPLORATION_CONSTANT = 0.7;

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
	 * Predict the number of takes using MCTS with determinization.
	 * Uses pure binary reward (no sole-winner bias) to find the most likely outcome.
	 */
	public double predictTakes(SimulationState baseState, List<Card> unknownCards,
							   int[] opponentSlots, Map<Integer, Set<Color>> colorVoids,
							   Map<Integer, Set<Card>> excludedCards) {
		baseState.setPredictionMode(true);

		int handSize = baseState.getHand(baseState.getBotPlayerIndex()).size();
		int opponents = baseState.getTotalPlayers() - 1;
		int iterations = computeIterations(handSize, opponents);
		int determinizations = computeDeterminizations(handSize, opponents);

		Map<Integer, double[]> takesStats = new HashMap<>();
		int iterationsPerWorld = iterations / determinizations;

		for (int d = 0; d < determinizations; d++) {
			SimulationState state = createDeterminizedState(baseState, unknownCards, opponentSlots, colorVoids, excludedCards);

			MctsNode root = new MctsNode(null, null, RolloutPolicy.prioritizeActions(state, state.getLegalActions()));

			for (int i = 0; i < iterationsPerWorld; i++) {
				runIteration(root, state.deepCopy());
			}

			aggregateTakesResults(root, takesStats);
		}

		return bestTakes(takesStats);
	}

	private SimulationState createDeterminizedState(SimulationState baseState, List<Card> unknownCards,
													int[] opponentSlots, Map<Integer, Set<Color>> colorVoids,
													Map<Integer, Set<Card>> excludedCards) {
		SimulationState state = baseState.deepCopy();
		int botIndex = state.getBotPlayerIndex();

		// Build opponent predictions array for prediction-aware determinization
		// Use -1 for bot and opponents whose predictions aren't known yet,
		// so Determinizer skips plausibility checks and avoids hand-strength bias.
		int[] opponentPredictions = new int[state.getTotalPlayers()];
		for (int i = 0; i < state.getTotalPlayers(); i++) {
			if (i == botIndex) {
				opponentPredictions[i] = -1;
			} else if (!state.isKnownPrediction(i)) {
				opponentPredictions[i] = -1;
			} else {
				opponentPredictions[i] = state.getExpectedTakes(i);
			}
		}

		List<List<Card>> sampledHands = Determinizer.sampleOpponentHands(
				unknownCards, opponentSlots, colorVoids, botIndex, opponentPredictions, excludedCards);

		// Replace opponent hands in the state copy
		for (int i = 0; i < state.getTotalPlayers(); i++) {
			if (i != botIndex) {
				List<Card> hand = state.getHand(i);
				hand.clear();
				hand.addAll(sampledHands.get(i));
			}
		}

		// Fix unknown opponent predictions based on their dealt hands.
		// Avoids searching opponent predictions in the tree — UCB1 would
		// select predictions that help the bot, creating cooperative bias.
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

	private void aggregateTakesResults(MctsNode root, Map<Integer, double[]> takesStats) {
		for (MctsNode child : root.getChildren()) {
			if (child.getAction() instanceof MctsAction.PredictTakes(int takes)) {
                double[] stats = takesStats.computeIfAbsent(takes, k -> new double[2]);
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
			if (avg > bestAvg) {
				bestAvg = avg;
				bestCard = entry.getKey();
			}
		}

		return bestCard;
	}

	private double bestTakes(Map<Integer, double[]> takesStats) {
		int bestTakes = 0;
		double bestAvg = Double.NEGATIVE_INFINITY;

		double weightedSum = 0;
		double weightTotal = 0;

		for (Map.Entry<Integer, double[]> entry : takesStats.entrySet()) {
			double[] stats = entry.getValue();
			double avg = stats[1] > 0 ? stats[0] / stats[1] : 0;
			if (avg > bestAvg) {
				bestAvg = avg;
				bestTakes = entry.getKey();
			}
			if (avg > 0) {
				weightedSum += entry.getKey() * avg;
				weightTotal += avg;
			}
		}

		// Return weighted average so the fractional part indicates
		// which direction to adjust when the best prediction is forbidden
		if (weightTotal > 0) {
			double weightedAvg = weightedSum / weightTotal;
			// Clamp so Math.round still yields bestTakes
			return Math.clamp(weightedAvg, bestTakes - 0.49, bestTakes + 0.49);
		}
		return bestTakes;
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
