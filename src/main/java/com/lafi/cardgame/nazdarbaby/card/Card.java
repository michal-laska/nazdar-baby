package com.lafi.cardgame.nazdarbaby.card;

import com.lafi.cardgame.nazdarbaby.util.Constant;
import com.vaadin.flow.component.html.Image;

import java.util.Objects;

public class Card implements Comparable<Card> {

	static final String JACK = "J";
	static final String QUEEN = "Q";
	static final String KING = "K";
	static final String ACE = "A";

	private final int value;
	private final Color color;
	private final String imageSrc;

	Card(String valueStr, Color color) {
		this(getValueFromString(valueStr), color, "cards", valueStr + color.getStrValue() + ".png");
	}

	private Card(int value, Color color, String folderName, String fileName) {
		this.value = value;
		this.color = color;
		imageSrc = folderName + '/' + fileName;
	}

	static Card createCardPlaceholder() {
		return new Card(0, null, "card_placeholder", "gray_back.png");
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
		image.setSrc(imageSrc);
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
		return color == null;
	}

	@Override
	public int compareTo(Card card) {
		Integer thisValue = this.value + color.getCompareToValue();
		Integer otherValue = card.value + card.color.getCompareToValue();

		return thisValue.compareTo(otherValue);
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
}
