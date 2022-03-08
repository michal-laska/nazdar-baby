package com.lafi.cardgame.nazdarbaby.card;

public final class CardPlaceholder extends Card {

	public CardPlaceholder() {
		super("gray_back.png", 0, null);
	}

	@Override
	public boolean isPlaceholder() {
		return true;
	}

	@Override
	String getFolderName() {
		return "card_placeholder";
	}
}
