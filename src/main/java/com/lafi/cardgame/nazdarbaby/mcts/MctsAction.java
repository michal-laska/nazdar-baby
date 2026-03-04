package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;

public sealed interface MctsAction {

	record PlayCard(Card card) implements MctsAction {
	}

	record PredictTakes(int takes) implements MctsAction {
	}
}
