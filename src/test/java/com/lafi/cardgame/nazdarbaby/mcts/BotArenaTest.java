package com.lafi.cardgame.nazdarbaby.mcts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BotArenaTest {

	@Nested
	class PlayTest {

		@Test
		void failingBot_failsTheRunAndStopsItsThreads() throws InterruptedException {
			BotArena.Options options = new BotArena.Options(20, 3, 3, 2, null);

			assertThatThrownBy(() -> BotArena.play(options, name -> {
				throw new IllegalStateException("broken baseline");
			}))
					.isInstanceOf(ExecutionException.class)
					.hasRootCauseMessage("broken baseline");

			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (arenaThreadsAlive() && System.nanoTime() < deadline) {
				Thread.sleep(20);
			}
			assertThat(arenaThreadsAlive()).isFalse();
		}

		private boolean arenaThreadsAlive() {
			return Thread.getAllStackTraces().keySet().stream()
					.anyMatch(thread -> thread.getName().startsWith(BotArena.THREAD_PREFIX) && thread.isAlive());
		}
	}

	@Nested
	class OptionsParseTest {

		@Test
		void noArguments_usesDefaultsAndNoBaseline() {
			BotArena.Options options = BotArena.Options.parse(new String[0]);

			assertThat(options).isEqualTo(new BotArena.Options(100, 4, 5, Runtime.getRuntime().availableProcessors(), null));
		}

		@Test
		void positionalArguments_fillInOrder() {
			BotArena.Options options = BotArena.Options.parse(new String[]{"600", "3", "7", "2"});

			assertThat(options).isEqualTo(new BotArena.Options(600, 3, 7, 2, null));
		}

		@Test
		void baseline_isAcceptedBeforeOrAfterThePositionalArguments() {
			assertThat(BotArena.Options.parse(new String[]{"600", "3", "--baseline", "main"}).baseline()).isEqualTo("main");
			assertThat(BotArena.Options.parse(new String[]{"--baseline", "main", "600", "3"}))
					.isEqualTo(new BotArena.Options(600, 3, 5, Runtime.getRuntime().availableProcessors(), "main"));
		}

		@Test
		void baselineWithoutRef_isRejected() {
			assertThatThrownBy(() -> BotArena.Options.parse(new String[]{"600", "--baseline"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("--baseline needs a git ref");
		}

		@Test
		void limits_areAcceptedAtTheirBoundaries() {
			assertThat(BotArena.Options.parse(new String[]{"1", "3", "10", "1"})).isEqualTo(new BotArena.Options(1, 3, 10, 1, null));
			assertThat(BotArena.Options.parse(new String[]{"1", "7", "7", "1"})).isEqualTo(new BotArena.Options(1, 7, 7, 1, null));
		}

		@Test
		void noDeals_isRejected() {
			assertThatThrownBy(() -> BotArena.Options.parse(new String[]{"0"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("deals must be at least 1, got 0");
		}

		@Test
		void tableSizeWithoutScoring_isRejected() {
			assertThatThrownBy(() -> BotArena.Options.parse(new String[]{"5", "2"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("players must be 3 to 7, got 2");
			assertThatThrownBy(() -> BotArena.Options.parse(new String[]{"5", "8"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("players must be 3 to 7, got 8");
		}

		@Test
		void moreTricksThanTheDeckCanDeal_isRejected() {
			// 3 players share the 32-card deck, 7 players the 52-card one
			assertThatThrownBy(() -> BotArena.Options.parse(new String[]{"5", "3", "11"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("tricks must be 1 to 10 for 3 players, got 11");
			assertThatThrownBy(() -> BotArena.Options.parse(new String[]{"5", "7", "8"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("tricks must be 1 to 7 for 7 players, got 8");
		}

		@Test
		void noTricks_isRejected() {
			assertThatThrownBy(() -> BotArena.Options.parse(new String[]{"5", "3", "0"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("tricks must be 1 to 10 for 3 players, got 0");
		}

		@Test
		void noThreads_isRejected() {
			assertThatThrownBy(() -> BotArena.Options.parse(new String[]{"5", "3", "3", "0"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("threads must be at least 1, got 0");
		}

		@Test
		void notANumber_isRejectedWithItsName() {
			assertThatThrownBy(() -> BotArena.Options.parse(new String[]{"5", "x"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("players must be a number, got x");
		}

		@Test
		void fifthPositionalArgument_isRejected() {
			assertThatThrownBy(() -> BotArena.Options.parse(new String[]{"600", "3", "7", "2", "9"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageStartingWith("Expected <deals> <players> <tricks> <threads>");
		}
	}
}
