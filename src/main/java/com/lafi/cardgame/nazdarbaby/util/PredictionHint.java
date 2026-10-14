package com.lafi.cardgame.nazdarbaby.util;

import com.lafi.cardgame.nazdarbaby.mcts.MctsEngine;

import java.util.Map;
import java.util.stream.Collectors;

public final class PredictionHint {

	private static final int SHOWN_PREDICTIONS = 2;

	private PredictionHint() {
	}

	public static String format(Map<Integer, Double> chances) {
		return chances.entrySet().stream()
				.sorted(MctsEngine.LIKELIEST_FIRST)
				.limit(SHOWN_PREDICTIONS)
				.map(entry -> entry.getKey() + ": " + Math.round(100 * entry.getValue()) + "%")
				.collect(Collectors.joining(" · "));
	}
}
