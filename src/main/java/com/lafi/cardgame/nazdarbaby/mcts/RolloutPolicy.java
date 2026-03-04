package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Game-aware heuristic playout for MCTS rollouts.
 * Considers trick position, trump strategy, and card conservation.
 */
final class RolloutPolicy {

	private static final int ACE_VALUE = 14;
	private static final int KING_VALUE = 13;

	private RolloutPolicy() {
	}

	/**
	 * Estimate expected takes based on hand strength, player count, and suit distribution.
	 * More players means each trick is harder to win — scales non-ace strength down.
	 * Void suits (0 cards) increase estimate since the player can trump.
	 */
	static int estimateTakes(List<Card> hand, int totalPlayers) {
		double estimate = 0.0;

		// Player count scaling: with more players, non-guaranteed cards are worth less
		// Aces are near-guaranteed regardless of player count
		// Other strong cards scale down with more opponents
		double opponentFactor = 1.0 / (totalPlayers - 1);

		// Count cards per suit for void detection
		int[] suitCount = new int[Color.values().length];
		for (Card card : hand) {
			suitCount[card.getColor().ordinal()]++;
		}

		// Count void suits (excluding hearts — being void in hearts isn't useful for trumping)
		int voidSuits = 0;
		for (Color color : Color.values()) {
			if (color != Color.HEARTS && suitCount[color.ordinal()] == 0) {
				voidSuits++;
			}
		}

		// Count hearts in hand (for trump power estimation)
		int heartsCount = suitCount[Color.HEARTS.ordinal()];

		for (Card card : hand) {
			if (card.getValue() == ACE_VALUE) {
				if (card.getColor() == Color.HEARTS) {
					// Ace of hearts: nearly always wins
					estimate += 1.0;
				} else {
					// Non-heart ace: strong but can be trumped if opponents are void
					// With more players, more likely someone is void
					estimate += Math.max(0.5, 1.0 - 0.1 * (totalPlayers - 2));
				}
			} else if (card.getColor() == Color.HEARTS) {
				// Hearts (trump): value depends on how high and how many players
				if (card.getValue() >= 12) {
					estimate += 0.7 * opponentFactor * 2;
				} else if (card.getValue() >= 10) {
					estimate += 0.5 * opponentFactor * 2;
				} else {
					estimate += 0.3 * opponentFactor * 2;
				}
			} else if (card.getValue() == KING_VALUE) {
				// King: decent but easily beaten by ace or trumped
				estimate += 0.4 * opponentFactor * 2;
			} else if (card.getValue() >= 12) {
				// Queen: marginal strength
				estimate += 0.2 * opponentFactor * 2;
			}
		}

		// Void bonus: each void suit lets us trump, but only if we have hearts
		if (heartsCount > 0 && voidSuits > 0) {
			estimate += Math.min(voidSuits, heartsCount) * 0.3;
		}

		int result = (int) Math.round(estimate);
		return Math.max(0, Math.min(result, hand.size()));
	}

	/**
	 * Heuristic playout to completion. Returns the reward for the bot player.
	 */
	static double rollout(SimulationState state) {
		while (!state.isTerminal()) {
			List<MctsAction> actions = state.getLegalActions();
			if (actions.isEmpty()) {
				break;
			}

			MctsAction action;
			if (state.getPhase() == SimulationState.Phase.PREDICTING) {
				action = selectPredictionAction(state, actions);
			} else {
				action = selectPlayAction(state, actions);
			}

			state.applyAction(action);
		}

		return state.getRewardForBot();
	}

	private static MctsAction selectPredictionAction(SimulationState state, List<MctsAction> actions) {
		int playerIndex = state.getCurrentPlayerIndex();
		List<Card> hand = state.getHand(playerIndex);
		int estimate = estimateTakes(hand, state.getTotalPlayers());

		for (MctsAction action : actions) {
			if (action instanceof MctsAction.PredictTakes predict && predict.takes() == estimate) {
				return action;
			}
		}

		return actions.get(Math.min(estimate, actions.size() - 1));
	}

	private static MctsAction selectPlayAction(SimulationState state, List<MctsAction> actions) {
		if (actions.size() == 1) {
			return actions.getFirst();
		}

		int playerIndex = state.getCurrentPlayerIndex();
		int expected = state.getExpectedTakes(playerIndex);
		int actual = state.getActualTakes(playerIndex);
		int needed = expected - actual;

		List<Card> currentTrick = state.getCurrentTrick();
		boolean isLeading = currentTrick.isEmpty();
		boolean isLast = currentTrick.size() == state.getTotalPlayers() - 1;

		if (isLeading) {
			return selectLeadCard(state, actions, needed);
		}

		if (needed > 0) {
			return selectToWin(actions, currentTrick, isLast);
		}
		return selectToLose(actions, currentTrick, isLast);
	}

	/**
	 * Leading a trick: choose what to open with.
	 */
	private static MctsAction selectLeadCard(SimulationState state, List<MctsAction> actions, int needed) {
		int remaining = state.getTotalTricks() - state.getTricksPlayed();

		if (needed > 0) {
			// Need tricks — lead aggressively
			// High urgency (need most remaining tricks): lead strongest
			// Low urgency: lead a medium-strong card, save strongest
			if (needed >= remaining) {
				return selectHighest(actions);
			}
			// Lead with a strong non-heart card if possible (save hearts for trumping later)
			MctsAction bestNonHeart = selectHighestInCategory(actions, false);
			if (bestNonHeart != null) {
				return bestNonHeart;
			}
			return selectHighest(actions);
		}

		// Don't need more tricks — lead low to avoid winning
		// Prefer leading from a suit with fewest cards (work toward creating a void)
		return selectLowestFromShortestSuit(state, actions);
	}

	/**
	 * Following and need to win this trick.
	 */
	private static MctsAction selectToWin(List<MctsAction> actions, List<Card> currentTrick, boolean isLast) {
		List<MctsAction> winners = new ArrayList<>();
		List<MctsAction> losers = new ArrayList<>();

		for (MctsAction action : actions) {
			if (action instanceof MctsAction.PlayCard play && wouldWinTrick(play.card(), currentTrick)) {
				winners.add(action);
			} else {
				losers.add(action);
			}
		}

		if (!winners.isEmpty()) {
			if (isLast) {
				// Last to play — use the lowest winning card (efficient)
				return selectLowest(winners);
			}
			// Not last — play strong to survive followers, but not wastefully
			// Use the second-highest if we have 3+ winners, otherwise highest
			if (winners.size() >= 3) {
				return selectSecondHighest(winners);
			}
			return selectHighest(winners);
		}

		// Can't win — dump lowest card to save strong cards for future tricks
		return selectLowest(losers.isEmpty() ? actions : losers);
	}

	/**
	 * Following and don't want to win this trick.
	 */
	private static MctsAction selectToLose(List<MctsAction> actions, List<Card> currentTrick, boolean isLast) {
		List<MctsAction> losers = new ArrayList<>();
		List<MctsAction> winners = new ArrayList<>();

		for (MctsAction action : actions) {
			if (action instanceof MctsAction.PlayCard play && wouldWinTrick(play.card(), currentTrick)) {
				winners.add(action);
			} else {
				losers.add(action);
			}
		}

		if (!losers.isEmpty()) {
			if (isLast) {
				// Last to play and can lose — play highest loser (conserve low cards)
				return selectHighest(losers);
			}
			// Not last — play lowest to safely lose
			return selectLowest(losers);
		}

		// Forced to win — play highest to get rid of dangerous high cards
		return selectHighest(winners.isEmpty() ? actions : winners);
	}

	/**
	 * Check if playing this card would currently win the trick.
	 */
	private static boolean wouldWinTrick(Card card, List<Card> currentTrick) {
		List<Card> testTrick = new ArrayList<>(currentTrick);
		testTrick.add(card);
		int winnerIndex = TrickEvaluator.getWinningIndex(testTrick);
		return winnerIndex == testTrick.size() - 1;
	}

	/**
	 * Select the card with highest strength.
	 */
	private static MctsAction selectHighest(List<MctsAction> actions) {
		MctsAction best = actions.getFirst();
		int bestValue = Integer.MIN_VALUE;

		for (MctsAction action : actions) {
			if (action instanceof MctsAction.PlayCard play) {
				int value = cardStrength(play.card());
				if (value > bestValue) {
					bestValue = value;
					best = action;
				}
			}
		}
		return best;
	}

	/**
	 * Select the card with lowest strength.
	 */
	private static MctsAction selectLowest(List<MctsAction> actions) {
		MctsAction best = actions.getFirst();
		int bestValue = Integer.MAX_VALUE;

		for (MctsAction action : actions) {
			if (action instanceof MctsAction.PlayCard play) {
				int value = cardStrength(play.card());
				if (value < bestValue) {
					bestValue = value;
					best = action;
				}
			}
		}
		return best;
	}

	/**
	 * Select the second-highest card (to win without wasting the best).
	 */
	private static MctsAction selectSecondHighest(List<MctsAction> actions) {
		MctsAction highest = null;
		MctsAction secondHighest = null;
		int highestValue = Integer.MIN_VALUE;
		int secondValue = Integer.MIN_VALUE;

		for (MctsAction action : actions) {
			if (action instanceof MctsAction.PlayCard play) {
				int value = cardStrength(play.card());
				if (value > highestValue) {
					secondHighest = highest;
					secondValue = highestValue;
					highest = action;
					highestValue = value;
				} else if (value > secondValue) {
					secondHighest = action;
					secondValue = value;
				}
			}
		}
		return secondHighest != null ? secondHighest : highest;
	}

	/**
	 * Select highest card in a category (hearts or non-hearts).
	 * Returns null if no card matches the category.
	 */
	private static MctsAction selectHighestInCategory(List<MctsAction> actions, boolean hearts) {
		MctsAction best = null;
		int bestValue = Integer.MIN_VALUE;

		for (MctsAction action : actions) {
			if (action instanceof MctsAction.PlayCard play) {
				boolean isHeart = play.card().getColor() == Color.HEARTS;
				if (isHeart == hearts) {
					int value = cardStrength(play.card());
					if (value > bestValue) {
						bestValue = value;
						best = action;
					}
				}
			}
		}
		return best;
	}

	/**
	 * Select the lowest card from the suit with the fewest cards in hand.
	 * This works toward creating voids for future tricks.
	 */
	private static MctsAction selectLowestFromShortestSuit(SimulationState state, List<MctsAction> actions) {
		int playerIndex = state.getCurrentPlayerIndex();
		List<Card> hand = state.getHand(playerIndex);

		// Count cards per suit in hand
		int[] suitCount = new int[Color.values().length];
		for (Card card : hand) {
			suitCount[card.getColor().ordinal()]++;
		}

		// Find the minimum suit count among playable cards (prefer non-hearts for void creation)
		int minCount = Integer.MAX_VALUE;
		for (MctsAction action : actions) {
			if (action instanceof MctsAction.PlayCard play) {
				Color color = play.card().getColor();
				int count = suitCount[color.ordinal()];
				if (count < minCount) {
					minCount = count;
				}
			}
		}

		// Among cards from shortest suit(s), pick the lowest
		MctsAction best = null;
		int bestValue = Integer.MAX_VALUE;

		for (MctsAction action : actions) {
			if (action instanceof MctsAction.PlayCard play) {
				Color color = play.card().getColor();
				int count = suitCount[color.ordinal()];
				if (count == minCount) {
					int value = cardStrength(play.card());
					if (value < bestValue) {
						bestValue = value;
						best = action;
					}
				}
			}
		}

		return best != null ? best : actions.getFirst();
	}

	/**
	 * Compute a heuristic value in [0, 1] for an action given the current state.
	 * Used for progressive bias in UCB1 selection — steers early exploration
	 * toward promising actions, then fades as visit count grows.
	 */
	static double heuristicValue(SimulationState state, MctsAction action) {
		if (action instanceof MctsAction.PredictTakes predict) {
			int playerIndex = state.getCurrentPlayerIndex();
			List<Card> hand = state.getHand(playerIndex);
			int estimate = estimateTakes(hand, state.getTotalPlayers());
			int distance = Math.abs(predict.takes() - estimate);
			// 1.0 for exact match, 0.5 for off-by-1, 0.33 for off-by-2, etc.
			return 1.0 / (1.0 + distance);
		}

		if (action instanceof MctsAction.PlayCard) {
			int playerIndex = state.getCurrentPlayerIndex();
			int needed = state.getExpectedTakes(playerIndex) - state.getActualTakes(playerIndex);
			List<Card> currentTrick = state.getCurrentTrick();
			boolean isLast = currentTrick.size() == state.getTotalPlayers() - 1;

			int score = scorePlayAction(action, needed, currentTrick, isLast);
			// Sigmoid normalization: maps any score to (0, 1)
			return 1.0 / (1.0 + Math.exp(-score / 200.0));
		}

		return 0.5;
	}

	/**
	 * Sort actions by heuristic priority for MCTS expansion ordering.
	 * Best actions are placed last (MctsNode.getUntriedAction() picks from the end).
	 * Worst-first ordering ensures the tree explores promising branches first.
	 */
	static List<MctsAction> prioritizeActions(SimulationState state, List<MctsAction> actions) {
		if (actions.size() <= 1) {
			return actions;
		}

		if (state.getPhase() == SimulationState.Phase.PREDICTING) {
			return prioritizePredictions(state, actions);
		}
		return prioritizePlays(state, actions);
	}

	private static List<MctsAction> prioritizePredictions(SimulationState state, List<MctsAction> actions) {
		int playerIndex = state.getCurrentPlayerIndex();
		List<Card> hand = state.getHand(playerIndex);
		int totalPlayers = state.getTotalPlayers();
		int estimate = estimateTakes(hand, totalPlayers);

		// Sort by distance from heuristic estimate — closest last (tried first)
		List<MctsAction> sorted = new ArrayList<>(actions);
		sorted.sort(Comparator.comparingInt((MctsAction a) -> {
			if (a instanceof MctsAction.PredictTakes predict) {
				return -Math.abs(predict.takes() - estimate);
			}
			return 0;
		}));
		return sorted;
	}

	private static List<MctsAction> prioritizePlays(SimulationState state, List<MctsAction> actions) {
		int playerIndex = state.getCurrentPlayerIndex();
		int needed = state.getExpectedTakes(playerIndex) - state.getActualTakes(playerIndex);
		List<Card> currentTrick = state.getCurrentTrick();
		boolean isLast = currentTrick.size() == state.getTotalPlayers() - 1;

		// Score each action — higher score = better = placed last (tried first)
		List<MctsAction> sorted = new ArrayList<>(actions);
		sorted.sort(Comparator.comparingInt(a -> scorePlayAction(a, needed, currentTrick, isLast)));
		return sorted;
	}

	private static int scorePlayAction(MctsAction action, int needed, List<Card> currentTrick, boolean isLast) {
		if (!(action instanceof MctsAction.PlayCard play)) {
			return 0;
		}

		Card card = play.card();
		int strength = cardStrength(card);
		boolean leading = currentTrick.isEmpty();

		if (leading) {
			// Leading: need tricks → prefer high; don't need → prefer low
			return needed > 0 ? strength : -strength;
		}

		boolean wins = wouldWinTrick(card, currentTrick);

		if (needed > 0) {
			if (wins) {
				// Want to win and can — prefer efficient wins (low winner if last, high otherwise)
				return 1000 + (isLast ? -strength : strength);
			}
			// Can't win — prefer saving high cards (dump low)
			return -strength;
		} else {
			if (!wins) {
				// Want to lose and can — prefer conserving low cards (dump high loser if last)
				return 500 + (isLast ? strength : -strength);
			}
			// Forced to win — dump highest to shed dangerous cards
			return -1000 + strength;
		}
	}

	private static int cardStrength(Card card) {
		int base = card.getValue();
		if (card.getColor() == Color.HEARTS) {
			base += 100;
		}
		return base;
	}
}
