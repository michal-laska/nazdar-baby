package com.lafi.cardgame.nazdarbaby.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

class CardProviderTest {

	@Test
	void getShuffledDeckOfCards_returnsNoDuplicates() {
		var cardProvider = new CardProvider(4);

		var deck = cardProvider.getShuffledDeckOfCards();

		assertThat(new HashSet<>(deck)).hasSameSizeAs(deck);
	}

	@Test
	void getShuffledDeckOfCards_doesNotMutateInternalDeck() {
		var cardProvider = new CardProvider(4);

		var first = cardProvider.getShuffledDeckOfCards();
		var second = cardProvider.getShuffledDeckOfCards();

		assertThat(first).hasSameSizeAs(second);
		assertThat(new HashSet<>(first)).isEqualTo(new HashSet<>(second));
	}

	@Test
	void getShuffledDeckOfCards_noDuplicatesUnderConcurrency() {
		var cardProvider = new CardProvider(4);
		var threadCount = 10;
		var iterationsPerThread = 100;

		var startLatch = new CountDownLatch(1);
		var failures = new CopyOnWriteArrayList<String>();

		try (var executor = Executors.newFixedThreadPool(threadCount)) {
			for (int t = 0; t < threadCount; t++) {
				executor.submit(() -> {
					try {
						startLatch.await();
						for (int i = 0; i < iterationsPerThread; i++) {
							var deck = cardProvider.getShuffledDeckOfCards();
							if (new HashSet<>(deck).size() != deck.size()) {
								failures.add("Duplicate cards in deck of size " + deck.size());
							}
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				});
			}

			startLatch.countDown();
		}

		assertThat(failures).isEmpty();
	}
}
