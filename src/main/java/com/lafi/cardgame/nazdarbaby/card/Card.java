package com.lafi.cardgame.nazdarbaby.card;

import com.lafi.cardgame.nazdarbaby.util.Constant;
import com.vaadin.flow.component.html.Image;

import java.util.Objects;

public class Card implements Comparable<Card> {

	static final String JACK = "J";
	static final String QUEEN = "Q";
	static final String KING = "K";
	static final String ACE = "A";

	private final String fileName;
	private final int value;
	private final Color color;

	Card(String valueStr, Color color) {
		this(valueStr + color.getStrValue() + ".png", getValueFromString(valueStr), color);
	}

	Card(String fileName, int value, Color color) {
		this.fileName = fileName;
		this.value = value;
		this.color = color;
	}

	private static int getValueFromString(String valueStr) {
		return switch (valueStr) {
			case ACE -> 14;
			case KING -> 13;
			case QUEEN -> 12;
			case JACK -> 11;
			default -> Integer.parseInt(valueStr);
		};
	}

	public Image getImage() {
		Image image = new Image();
		image.setSrc(getFolderName() + '/' + fileName);
		image.setHeight(Constant.IMAGE_HEIGHT);

		return image;
	}

	public int getValue() {
		return value;
	}

	public Color getColor() {
		return color;
	}

	public boolean isPlaceholder() {
		return false;
	}

	@Override
	public int compareTo(Card card) {
		Integer value = this.value + color.getCompareToValue();
		Integer otherValue = card.value + card.color.getCompareToValue();

		return value.compareTo(otherValue);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		Card card = (Card) obj;
		return value == card.value && color == card.color;
	}

	@Override
	public int hashCode() {
		return Objects.hash(value, color);
	}

	String getFolderName() {
		return "cards";
	}
}
