package com.lafi.cardgame.nazdarbaby.util;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

public final class PredictionHint {

	private static final int SHOWN_PREDICTIONS = 2;
	private static final Comparator<Map.Entry<Integer, Double>> LIKELIEST_FIRST =
			Map.Entry.<Integer, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey());

	private PredictionHint() {
	}

	public static String format(Map<Integer, Double> chances) {
		return chances.entrySet().stream()
				.sorted(LIKELIEST_FIRST)
				.limit(SHOWN_PREDICTIONS)
				.map(entry -> entry.getKey() + ": " + Math.round(100 * entry.getValue()) + "%")
				.collect(Collectors.joining(" · "));
	}
}
