package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.mcts.BotArena.Bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seats the engine at the arena. {@link BaselineBots} compiles this same source against the
 * baseline engine, so it may only call engine API the baseline has too, and it imports {@link Bot}
 * because the package move would otherwise resolve a bare {@code Bot} inside the baseline package.
 */
public record MctsArenaBot(String name, MctsEngine engine) implements Bot {

	public MctsArenaBot(String name) {
		this(name, new MctsEngine());
	}

	@Override
	public double predict(List<Card> hand, int position, int totalPlayers, int totalTricks,
						  Integer[] predictions, List<Card> unknownCards, Map<Integer, Set<Color>> voids) {
		int[] expectedTakes = new int[totalPlayers];
		int[] opponentSlots = new int[totalPlayers];
		int predictionsDone = 0;

		List<List<Card>> hands = new ArrayList<>(totalPlayers);
		for (int i = 0; i < totalPlayers; i++) {
			hands.add(i == position ? new ArrayList<>(hand) : new ArrayList<>());
			opponentSlots[i] = i == position ? 0 : totalTricks;
			if (predictions[i] != null) {
				expectedTakes[i] = predictions[i];
				++predictionsDone;
			}
		}

		SimulationState state = new SimulationState(hands, expectedTakes, new int[totalPlayers],
				new ArrayList<>(), SimulationState.Phase.PREDICTING, 0, position, 0, totalTricks,
				position, predictionsDone);
		for (int i = 0; i < totalPlayers; i++) {
			if (predictions[i] != null) {
				state.setKnownPrediction(i);
			}
		}

		return engine.predictTakes(state, unknownCards, opponentSlots, voids, Map.of());
	}

	@Override
	public Card play(List<Card> hand, int position, int totalPlayers, int totalTricks, int tricksPlayed,
					 int[] expectedTakes, int[] actualTakes, List<Card> table,
					 List<Card> unknownCards, int[] opponentSlots, Map<Integer, Set<Color>> voids) {
		List<List<Card>> hands = new ArrayList<>(totalPlayers);
		for (int i = 0; i < totalPlayers; i++) {
			hands.add(i == position ? new ArrayList<>(hand) : new ArrayList<>());
		}

		SimulationState state = new SimulationState(hands, expectedTakes.clone(), actualTakes.clone(),
				new ArrayList<>(table), SimulationState.Phase.PLAYING, 0, position, tricksPlayed,
				totalTricks, position, totalPlayers);
		for (int i = 0; i < totalPlayers; i++) {
			state.setKnownPrediction(i);
		}

		return engine.selectCard(state, unknownCards, opponentSlots, voids, Map.of());
	}
}
