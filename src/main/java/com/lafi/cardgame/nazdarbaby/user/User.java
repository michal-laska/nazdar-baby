package com.lafi.cardgame.nazdarbaby.user;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class User {

	private final String name;
	private final List<Card> cards = new ArrayList<>();

	private int actualTakes;
	private Integer expectedTakes;
	private Action action = Action.NONE;
	private float points;
	private Float lastAddedPoints;
	private Boolean terminator = false;

	public User(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public int getActualTakes() {
		return actualTakes;
	}

	public void resetActualTakes() {
		actualTakes = 0;
	}

	public void increaseActualTakes() {
		++actualTakes;
	}

	public Integer getExpectedTakes() {
		return expectedTakes;
	}

	public void setExpectedTakes(Integer expectedTakes) {
		this.expectedTakes = expectedTakes;
	}

	public Boolean wasTerminator() {
		return terminator;
	}

	public void setTerminator(Boolean terminator) {
		this.terminator = terminator;
	}

	public List<Card> getCards() {
		return cards;
	}

	public void addCard(Card card) {
		cards.add(card);
	}

	public float getPoints() {
		return points;
	}

	public void addPoints(float points) {
		lastAddedPoints = points == -0f ? 0f : points;
	}

	public void resetLastAddedPoints() {
		if (lastAddedPoints != null) {
			points += lastAddedPoints;
			lastAddedPoints = null;
		}
	}

	public boolean isReady() {
		return action == Action.READY;
	}

	public void setReady(boolean value) {
		action = value ? Action.READY : Action.NONE;
	}

	public boolean wantNewGame() {
		return action == Action.NEW_GAME;
	}

	public void setNewGame(boolean value) {
		action = value ? Action.NEW_GAME : Action.NONE;
	}

	public boolean isLoggedOut() {
		return action == Action.LOG_OUT;
	}

	public void setLoggedOut(boolean value) {
		action = value ? Action.LOG_OUT : Action.NONE;
	}

	public void resetAction() {
		action = Action.NONE;
	}

	public boolean isWinner() {
		return Integer.valueOf(actualTakes).equals(expectedTakes);
	}

	public boolean hasColor(Color color) {
		return cards.stream().anyMatch(card -> card.getColor() == color);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		User user = (User) obj;
		return Objects.equals(name, user.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name);
	}

	@Override
	public String toString() {
		String result = name;

		if (expectedTakes == null) {
			return result;
		}

		result += " (" + actualTakes + '/' + expectedTakes + ')';

		if (lastAddedPoints != null) {
			result += " " + lastAddedPoints;
		}

		return result;
	}

	private enum Action {
		NONE,
		READY,
		NEW_GAME,
		LOG_OUT
	}
}
