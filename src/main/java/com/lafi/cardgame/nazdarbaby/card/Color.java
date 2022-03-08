package com.lafi.cardgame.nazdarbaby.card;

public enum Color {
	HEARTS("H", 300),
	SPADES("S", 200),
	DIAMONDS("D", 100),
	CLUBS("C", 0);

	private final String strValue;
	private final int compareToValue;

	Color(String strValue, int compareToValue) {
		this.strValue = strValue;
		this.compareToValue = compareToValue;
	}

	public String getStrValue() {
		return strValue;
	}

	public int getCompareToValue() {
		return compareToValue;
	}
}
