package com.lafi.cardgame.nazdarbaby.point;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class PointProvider {

	public static final Map<Integer, Map<Integer, Float>> NUMBER_OF_USERS_TO_WINNER_MAP;

	static {
		var numberOfUsersToWinnerMap = new HashMap<Integer, Map<Integer, Float>>(6);

		var winnerMap = Map.of(1, 10f, 2, 5f);
		addWinnerMap(winnerMap, numberOfUsersToWinnerMap);

		winnerMap = Map.of(1, 12f, 2, 6f, 3, 4f);
		addWinnerMap(winnerMap, numberOfUsersToWinnerMap);

		winnerMap = Map.of(1, 12f, 2, 6f, 3, 4f, 4, 3f);
		addWinnerMap(winnerMap, numberOfUsersToWinnerMap);

		winnerMap = Map.of(1, 15f, 2, 8f, 3, 5f, 4, 4f, 5, 3f);
		addWinnerMap(winnerMap, numberOfUsersToWinnerMap);

		// 18, 9, 6, 4.5, 3.5, 3
		winnerMap = Map.of(1, 15f, 2, 7.5f, 3, 6f, 4, 4.5f, 5, 3f, 6, 2.5f);
		addWinnerMap(winnerMap, numberOfUsersToWinnerMap);

		NUMBER_OF_USERS_TO_WINNER_MAP = Collections.unmodifiableMap(numberOfUsersToWinnerMap);
	}

	public float getWinnerPoints(int numberOfUsers, int numberOfWinners) {
		if (numberOfWinners == 0) {
			return 0;
		}

		var winMap = NUMBER_OF_USERS_TO_WINNER_MAP.get(numberOfUsers);
		if (winMap == null) {
			throw new IllegalArgumentException("numberOfUsers = " + numberOfUsers);
		}

		var winPoints = winMap.get(numberOfWinners);
		if (winPoints == null) {
			throw new IllegalArgumentException("numberOfWinners = " + numberOfWinners);
		}

		return winPoints;
	}

	private static void addWinnerMap(Map<Integer, Float> winMap, Map<Integer, Map<Integer, Float>> numberOfUsersToWinMap) {
		numberOfUsersToWinMap.put(winMap.size() + 1, winMap);
	}
}
