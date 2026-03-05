package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;

import java.util.ArrayList;
import java.util.List;

public final class SimulationState {

	public enum Phase {
		PREDICTING,
		PLAYING
	}

	private final List<List<Card>> hands;
	private final int[] expectedTakes;
	private final int[] actualTakes;
	private final boolean[] knownPrediction;
	private final List<Card> currentTrick;
	private final int totalPlayers;
	private final int totalTricks;
	private final int botPlayerIndex;
	private boolean predictionMode;

	private Phase phase;
	private int leadPlayerIndex;
	private int currentPlayerIndex;
	private int tricksPlayed;
	private int predictionsDone;

	public SimulationState(List<List<Card>> hands, int[] expectedTakes, int[] actualTakes,
						   List<Card> currentTrick, Phase phase, int leadPlayerIndex,
						   int currentPlayerIndex, int tricksPlayed, int totalTricks,
						   int botPlayerIndex, int predictionsDone) {
		this(hands, expectedTakes, actualTakes, new boolean[hands.size()], false,
				currentTrick, phase, leadPlayerIndex, currentPlayerIndex,
				tricksPlayed, totalTricks, botPlayerIndex, predictionsDone);
	}

	private SimulationState(List<List<Card>> hands, int[] expectedTakes, int[] actualTakes,
							boolean[] knownPrediction, boolean predictionMode,
							List<Card> currentTrick, Phase phase,
							int leadPlayerIndex, int currentPlayerIndex, int tricksPlayed,
							int totalTricks, int botPlayerIndex, int predictionsDone) {
		this.hands = hands;
		this.expectedTakes = expectedTakes;
		this.actualTakes = actualTakes;
		this.knownPrediction = knownPrediction;
		this.predictionMode = predictionMode;
		this.currentTrick = currentTrick;
		this.phase = phase;
		this.leadPlayerIndex = leadPlayerIndex;
		this.currentPlayerIndex = currentPlayerIndex;
		this.tricksPlayed = tricksPlayed;
		this.totalPlayers = hands.size();
		this.totalTricks = totalTricks;
		this.botPlayerIndex = botPlayerIndex;
		this.predictionsDone = predictionsDone;
	}

	/**
	 * Mark a player's prediction as known (fixed). During simulation,
	 * this player's prediction will not be searched — it will be auto-applied.
	 */
	public void setKnownPrediction(int playerIndex) {
		knownPrediction[playerIndex] = true;
	}

	/**
	 * Set a player's prediction and mark it as known.
	 * Used to fix opponent predictions from heuristics before tree search,
	 * avoiding cooperative bias from UCB1 optimizing opponent choices.
	 */
	void setFixedPrediction(int playerIndex, int takes) {
		expectedTakes[playerIndex] = takes;
		knownPrediction[playerIndex] = true;
	}

	/**
	 * Enable prediction mode: reward is pure binary (did bot match its prediction?)
	 * without opponent-aware scaling. Prevents bias toward extreme predictions.
	 */
	public void setPredictionMode(boolean predictionMode) {
		this.predictionMode = predictionMode;
	}

	SimulationState deepCopy() {
		List<List<Card>> handsCopy = new ArrayList<>(totalPlayers);
		for (List<Card> hand : hands) {
			handsCopy.add(new ArrayList<>(hand));
		}

		return new SimulationState(
				handsCopy,
				expectedTakes.clone(),
				actualTakes.clone(),
				knownPrediction.clone(),
				predictionMode,
				new ArrayList<>(currentTrick),
				phase,
				leadPlayerIndex,
				currentPlayerIndex,
				tricksPlayed,
				totalTricks,
				botPlayerIndex,
				predictionsDone
		);
	}

	List<MctsAction> getLegalActions() {
		if (phase == Phase.PREDICTING) {
			if (knownPrediction[currentPlayerIndex]) {
				// This player's prediction is fixed — only one legal "action"
				return List.of(new MctsAction.PredictTakes(expectedTakes[currentPlayerIndex]));
			}

			int handSize = hands.get(currentPlayerIndex).size();
			List<MctsAction> actions = new ArrayList<>(handSize + 1);
			for (int i = 0; i <= handSize; i++) {
				actions.add(new MctsAction.PredictTakes(i));
			}
			return actions;
		}

		List<Card> hand = hands.get(currentPlayerIndex);
		List<Card> legalPlays = TrickEvaluator.getLegalPlays(hand, currentTrick);

		List<MctsAction> actions = new ArrayList<>(legalPlays.size());
		for (Card card : legalPlays) {
			actions.add(new MctsAction.PlayCard(card));
		}
		return actions;
	}

	void applyAction(MctsAction action) {
		switch (action) {
			case MctsAction.PredictTakes predict -> applyPrediction(predict.takes());
			case MctsAction.PlayCard play -> applyPlayCard(play.card());
		}
	}

	boolean isTerminal() {
		if (phase == Phase.PREDICTING) {
			return false;
		}
		return tricksPlayed >= totalTricks && currentTrick.isEmpty();
	}

	/**
	 * Reward for the bot player.
	 * In prediction mode: pure binary (1.0 exact match, 0.0 otherwise) to avoid
	 * bias toward extreme "sole winner" predictions.
	 * In play mode: opponent-aware (sole winner > shared win) to incentivize disruption.
	 */
	double getRewardForBot() {
		int expected = expectedTakes[botPlayerIndex];
		int actual = actualTakes[botPlayerIndex];
		boolean botWon = expected == actual;

		if (predictionMode) {
			return botWon ? 1.0 : 0.0;
		}

		if (!botWon) {
			int loseCount = 0;
			for (int i = 0; i < totalPlayers; i++) {
				if (expectedTakes[i] != actualTakes[i]) {
					loseCount++;
				}
			}
			return loseCount > 1 ? 0.1 : 0.0;
		}

		int winCount = 0;
		for (int i = 0; i < totalPlayers; i++) {
			if (expectedTakes[i] == actualTakes[i]) {
				winCount++;
			}
		}
		return 1.0 / winCount;
	}

	int getCurrentPlayerIndex() {
		return currentPlayerIndex;
	}

	int getBotPlayerIndex() {
		return botPlayerIndex;
	}

	Phase getPhase() {
		return phase;
	}

	List<Card> getHand(int playerIndex) {
		return hands.get(playerIndex);
	}

	int getExpectedTakes(int playerIndex) {
		return expectedTakes[playerIndex];
	}

	boolean isKnownPrediction(int playerIndex) {
		return knownPrediction[playerIndex];
	}

	int getActualTakes(int playerIndex) {
		return actualTakes[playerIndex];
	}

	List<Card> getCurrentTrick() {
		return currentTrick;
	}

	int getTotalTricks() {
		return totalTricks;
	}

	int getTricksPlayed() {
		return tricksPlayed;
	}

	int getTotalPlayers() {
		return totalPlayers;
	}

	int getLeadPlayerIndex() {
		return leadPlayerIndex;
	}

	private void applyPrediction(int takes) {
		expectedTakes[currentPlayerIndex] = takes;
		predictionsDone++;

		if (predictionsDone >= totalPlayers) {
			phase = Phase.PLAYING;
			currentPlayerIndex = leadPlayerIndex;
		} else {
			currentPlayerIndex = (currentPlayerIndex + 1) % totalPlayers;
			// Auto-advance past players with known predictions
			autoApplyKnownPredictions();
		}
	}

	private void autoApplyKnownPredictions() {
		while (phase == Phase.PREDICTING && knownPrediction[currentPlayerIndex]) {
			expectedTakes[currentPlayerIndex] = expectedTakes[currentPlayerIndex]; // already set
			predictionsDone++;

			if (predictionsDone >= totalPlayers) {
				phase = Phase.PLAYING;
				currentPlayerIndex = leadPlayerIndex;
			} else {
				currentPlayerIndex = (currentPlayerIndex + 1) % totalPlayers;
			}
		}
	}

	private void applyPlayCard(Card card) {
		List<Card> hand = hands.get(currentPlayerIndex);
		hand.remove(card);
		currentTrick.add(card);

		if (currentTrick.size() == totalPlayers) {
			resolveTrick();
		} else {
			currentPlayerIndex = (currentPlayerIndex + 1) % totalPlayers;
		}
	}

	private void resolveTrick() {
		int winnerOffset = TrickEvaluator.getWinningIndex(currentTrick);
		int winnerIndex = (leadPlayerIndex + winnerOffset) % totalPlayers;

		actualTakes[winnerIndex]++;
		tricksPlayed++;
		currentTrick.clear();

		leadPlayerIndex = winnerIndex;
		currentPlayerIndex = winnerIndex;
	}
}
