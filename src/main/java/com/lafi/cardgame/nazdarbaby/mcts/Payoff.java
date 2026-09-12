package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.point.PointProvider;

/**
 * Translates the real end-of-set scoring into the [0, 1] reward MCTS backs up,
 * so simulations optimize points instead of a hand-tuned approximation.
 */
final class Payoff {

	private static final PointProvider POINT_PROVIDER = new PointProvider();

	private Payoff() {
	}

	/**
	 * Normalized points a player scores when {@code winCount} players match their prediction.
	 * A sole winner scores 1, the sole loser next to everyone else winning scores 0,
	 * and a set nobody wins scores 0.5 for everyone — nobody gains or loses points there.
	 */
	static double normalized(int totalPlayers, int winCount, boolean playerWon) {
		// Everybody winning needs the predictions to sum to the trick count, which the game forbids
		int winners = Math.min(winCount, totalPlayers - 1);

		float soleWinnerPoints = POINT_PROVIDER.getWinnerPoints(totalPlayers, 1);
		float winnerPoints = POINT_PROVIDER.getWinnerPoints(totalPlayers, winners);
		float points = playerWon ? winnerPoints : -(winners * winnerPoints) / (totalPlayers - winners);

		return (points + soleWinnerPoints) / (2 * soleWinnerPoints);
	}
}
