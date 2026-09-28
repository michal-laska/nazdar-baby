package com.lafi.cardgame.nazdarbaby.user;

import static com.lafi.cardgame.nazdarbaby.card.TestCards.getCard;
import static org.assertj.core.api.Assertions.assertThat;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserTest {

	@Nested
	class GetCardsInHandTest {

		@Test
		void noCards_isEmpty() {
			assertThat(new User("bot").getCardsInHand()).isEmpty();
		}

		@Test
		void playedCards_areLeftOut() {
			User user = new User("bot");
			Card sevenOfClubs = getCard(7, Color.CLUBS);
			Card aceOfHearts = getCard(14, Color.HEARTS);
			user.addCard(sevenOfClubs);
			user.addCard(CardProvider.CARD_PLACEHOLDER);
			user.addCard(aceOfHearts);

			assertThat(user.getCardsInHand()).containsExactly(sevenOfClubs, aceOfHearts);
		}

		@Test
		void everyCardPlayed_isEmpty() {
			User user = new User("bot");
			user.addCard(CardProvider.CARD_PLACEHOLDER);
			user.addCard(CardProvider.CARD_PLACEHOLDER);

			assertThat(user.getCardsInHand()).isEmpty();
		}

		@Test
		void doesNotChangeTheHand() {
			User user = new User("bot");
			user.addCard(getCard(7, Color.CLUBS));
			user.addCard(CardProvider.CARD_PLACEHOLDER);

			user.getCardsInHand();

			assertThat(user.getCards()).hasSize(2);
		}
	}
}
