package com.lafi.cardgame.nazdarbaby.points;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Points {

	public static final Map<Integer, Map<Integer, Float>> NUMBER_OF_USERS_TO_WIN_MAP;

	static {
		Map<Integer, Map<Integer, Float>> numberOfUsersToWinMap = new HashMap<>(6);

		Map<Integer, Float> winMapFor2 = new HashMap<>(1);
		winMapFor2.put(1, 5f);
		addWinMap(winMapFor2, numberOfUsersToWinMap);

		Map<Integer, Float> winMapFor3 = new HashMap<>(2);
		winMapFor3.put(1, 10f);
		winMapFor3.put(2, 5f);
		addWinMap(winMapFor3, numberOfUsersToWinMap);

		Map<Integer, Float> winMapFor4 = new HashMap<>(3);
		winMapFor4.put(1, 12f);
		winMapFor4.put(2, 6f);
		winMapFor4.put(3, 4f);
		addWinMap(winMapFor4, numberOfUsersToWinMap);

		Map<Integer, Float> winMapFor5 = new HashMap<>(4);
		winMapFor5.put(1, 12f);
		winMapFor5.put(2, 6f);
		winMapFor5.put(3, 4f);
		winMapFor5.put(4, 3f);
		addWinMap(winMapFor5, numberOfUsersToWinMap);

		Map<Integer, Float> winMapFor6 = new HashMap<>(5);
		winMapFor6.put(1, 15f);
		winMapFor6.put(2, 8f);
		winMapFor6.put(3, 5f);
		winMapFor6.put(4, 4f);
		winMapFor6.put(5, 3f);
		addWinMap(winMapFor6, numberOfUsersToWinMap);

		Map<Integer, Float> winMapFor7 = new HashMap<>(6);
		winMapFor7.put(1, 15f);  //18
		winMapFor7.put(2, 7.5f); // 9
		winMapFor7.put(3, 6f);   // 6
		winMapFor7.put(4, 4.5f); // 4.5
		winMapFor7.put(5, 3f);   // 3.5
		winMapFor7.put(6, 2.5f); // 3
		addWinMap(winMapFor7, numberOfUsersToWinMap);

		NUMBER_OF_USERS_TO_WIN_MAP = Collections.unmodifiableMap(numberOfUsersToWinMap);
	}

	private Points() {
	}

	public static float getWinPoints(int numberOfUsers, int numberOfWinners) {
		if (numberOfWinners == 0) {
			return 0;
		}

		Map<Integer, Float> winMap = NUMBER_OF_USERS_TO_WIN_MAP.get(numberOfUsers);
		if (winMap == null) {
			throw new IllegalArgumentException("numberOfUsers = " + numberOfUsers);
		}

		Float winPoints = winMap.get(numberOfWinners);
		if (winPoints == null) {
			throw new IllegalArgumentException("numberOfWinners = " + numberOfWinners);
		}

		return winPoints;
	}

	private static void addWinMap(Map<Integer, Float> winMap, Map<Integer, Map<Integer, Float>> numberOfUsersToWinMap) {
		numberOfUsersToWinMap.put(winMap.size() + 1, winMap);
	}
}
