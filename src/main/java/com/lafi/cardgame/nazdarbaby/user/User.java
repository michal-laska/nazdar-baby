package com.lafi.cardgame.nazdarbaby.user;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.Color;
import org.apache.commons.rng.simple.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class User {

	private final List<Card> cards = new ArrayList<>();
	private final String name;
	private final Integer takeoverCode;
	private final boolean isBot;

	private int actualTakes;
	private Integer expectedTakes;
	private Action action = Action.NONE;
	private float points;
	private Float lastAddedPoints;
	private Boolean terminator = false;

	public User(String name, Set<Integer> takeoverCodes) {
		this(name, false, takeoverCodes);
	}

	public User(String name) {
		this(name, true, null);
	}

	private User(String name, boolean isBot, Set<Integer> takeoverCodes) {
		this.name = name;
		this.isBot = isBot;

		if (isBot) {
			takeoverCode = null;
		} else {
			var randomNumberGenerator = RandomSource.XO_RO_SHI_RO_128_PP.create();

			int randomInt;
			do {
				randomInt = randomNumberGenerator.nextInt(10, 100);
			} while (takeoverCodes.contains(randomInt));

			takeoverCodes.add(randomInt);

			takeoverCode = randomInt;
		}
	}

	public String getName() {
		return name;
	}

	public Integer getTakeoverCode() {
		return takeoverCode;
	}

	public boolean isBot() {
		return isBot;
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

		var user = (User) obj;
		return Objects.equals(name, user.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name);
	}

	@Override
	public String toString() {
		var result = name;

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
