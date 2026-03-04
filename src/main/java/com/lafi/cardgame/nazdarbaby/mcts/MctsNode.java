package com.lafi.cardgame.nazdarbaby.mcts;

import java.util.ArrayList;
import java.util.List;

final class MctsNode {

	private final MctsAction action;
	private final MctsNode parent;
	private final List<MctsNode> children = new ArrayList<>();
	private final List<MctsAction> untriedActions;
	private final double heuristicValue;

	private int visitCount;
	private double totalReward;

	MctsNode(MctsAction action, MctsNode parent, List<MctsAction> untriedActions) {
		this(action, parent, untriedActions, 0.0);
	}

	MctsNode(MctsAction action, MctsNode parent, List<MctsAction> untriedActions, double heuristicValue) {
		this.action = action;
		this.parent = parent;
		this.untriedActions = new ArrayList<>(untriedActions);
		this.heuristicValue = heuristicValue;
	}

	MctsNode selectChildUcb1(double explorationConstant) {
		MctsNode bestChild = null;
		double bestValue = Double.NEGATIVE_INFINITY;

		double logParentVisits = Math.log(visitCount);

		for (MctsNode child : children) {
			double exploitation = child.totalReward / child.visitCount;
			double exploration = explorationConstant * Math.sqrt(logParentVisits / child.visitCount);
			double progressiveBias = child.heuristicValue / (child.visitCount + 1);
			double ucb1 = exploitation + exploration + progressiveBias;

			if (ucb1 > bestValue) {
				bestValue = ucb1;
				bestChild = child;
			}
		}

		return bestChild;
	}

	MctsNode addChild(MctsAction action, List<MctsAction> childUntriedActions, double heuristicValue) {
		untriedActions.remove(action);

		MctsNode child = new MctsNode(action, this, childUntriedActions, heuristicValue);
		children.add(child);
		return child;
	}

	void update(double reward) {
		visitCount++;
		totalReward += reward;
	}

	boolean hasUntriedActions() {
		return !untriedActions.isEmpty();
	}

	MctsAction getUntriedAction() {
		return untriedActions.getLast();
	}

	boolean isFullyExpanded() {
		return untriedActions.isEmpty();
	}

	boolean hasChildren() {
		return !children.isEmpty();
	}

	MctsAction getAction() {
		return action;
	}

	MctsNode getParent() {
		return parent;
	}

	List<MctsNode> getChildren() {
		return children;
	}

	int getVisitCount() {
		return visitCount;
	}

	double getTotalReward() {
		return totalReward;
	}
}
