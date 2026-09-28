package com.lafi.cardgame.nazdarbaby.mcts;

import static com.lafi.cardgame.nazdarbaby.card.TestCards.getCard;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.mcts.BotArena.Bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BaselineBotsTest {

	// The first MCTS engine, whose predictTakes and selectCard had no excludedCards parameter yet
	private static final String FIRST_MCTS_COMMIT = "d8181bd";
	private static final String COMMIT_BEFORE_MCTS = FIRST_MCTS_COMMIT + "^";

	@Nested
	class MoveToBaselinePackageTest {

		@Test
		void rewritesOnlyThePackageDeclaration() {
			String source = """
					package com.lafi.cardgame.nazdarbaby.mcts;

					import com.lafi.cardgame.nazdarbaby.mcts.BotArena.Bot;
					""";

			assertThat(BaselineBots.moveToBaselinePackage(source)).isEqualTo("""
					package com.lafi.cardgame.nazdarbaby.mcts.baseline;

					import com.lafi.cardgame.nazdarbaby.mcts.BotArena.Bot;
					""");
		}

		@Test
		void sourceFromAnotherPackage_isRejected() {
			assertThatThrownBy(() -> BaselineBots.moveToBaselinePackage("package com.lafi.cardgame.nazdarbaby.card;\n"))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Nested
	class LoadTest {

		@Test
		void head_seatsABotFromItsOwnCopyOfTheEngine() throws Exception {
			assumeTrue(insideGitRepository());

			BaselineBots.Baseline baseline = BaselineBots.load("HEAD");
			Bot bot = baseline.botFactory().apply("OLD");

			assertThat(bot.name()).isEqualTo("OLD");
			assertThat(bot.getClass().getName()).isEqualTo(BaselineBots.BASELINE_PACKAGE + ".MctsArenaBot");
			assertThat(bot.getClass().getClassLoader()).isNotSameAs(MctsArenaBot.class.getClassLoader());
			assertThat(baseline.commit()).isNotBlank();

			List<Card> hand = List.of(getCard(14, Color.HEARTS));
			List<Card> unknownCards = List.of(getCard(7, Color.CLUBS), getCard(8, Color.CLUBS));
			double prediction = bot.predict(hand, 0, 3, 1, new Integer[3], unknownCards, Map.of());
			assertThat(prediction).isEqualTo(1.0);
		}

		@Test
		void refBeforeTheEngineExisted_isRejected() {
			assumeTrue(insideGitRepository() && commitExists(COMMIT_BEFORE_MCTS));

			assertThatThrownBy(() -> BaselineBots.load(COMMIT_BEFORE_MCTS))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageStartingWith("No engine sources under");
		}

		@Test
		void engineWithAnOlderApi_isRejectedWithTheCompilerErrors() {
			assumeTrue(insideGitRepository() && commitExists(FIRST_MCTS_COMMIT));

			assertThatThrownBy(() -> BaselineBots.load(FIRST_MCTS_COMMIT))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("does not compile with today's MctsArenaBot")
					.hasMessageContaining("predictTakes");
		}

		@Test
		void unknownRef_isRejected() {
			assumeTrue(insideGitRepository());

			assertThatThrownBy(() -> BaselineBots.load("no-such-ref"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageStartingWith("Unknown git ref no-such-ref");
		}
	}

	private static boolean commitExists(String ref) {
		return gitSucceeds("cat-file", "-e", ref + "^{commit}");
	}

	private static boolean insideGitRepository() {
		return gitSucceeds("rev-parse", "--is-inside-work-tree");
	}

	private static boolean gitSucceeds(String... arguments) {
		List<String> command = new ArrayList<>(List.of("git"));
		command.addAll(List.of(arguments));
		try {
			Process process = new ProcessBuilder(command)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			return process.waitFor() == 0;
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
