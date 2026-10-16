package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.mcts.BotArena.Bot;
import com.lafi.cardgame.nazdarbaby.mcts.MctsEngine.LogOddsCalibration;
import com.lafi.cardgame.nazdarbaby.provider.Game;
import com.lafi.cardgame.nazdarbaby.provider.Table;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Re-fits the hint calibrations in {@link MctsEngine} after a change to how the bots play.
 *
 * <p>Run it with {@code ./gradlew hintCalibration --args="<deals per table> <threads> [--dump <file>]"}. Every hand
 * size the game deals is played with bots in every seat, also with each seat predicting its second-likeliest option
 * half the time, since the hint shows both. The default 200 deals per table take 30 to 40 minutes on 10 threads.
 * {@code --dump} also writes every prediction to a CSV file, so later questions need no new run.
 */
public final class HintCalibration {

	private static final double[] SECOND_OPTION_SHARES = {0.0, 0.5};
	private static final int CALIBRATION_BUCKETS = 5;

	private HintCalibration() {
	}

	public static void main(String[] args) throws Exception {
		List<String> arguments = new ArrayList<>(Arrays.asList(args));
		String dump = BotArena.Options.takeFlag(arguments, "--dump", "a file");
		BotArena.Options.require(arguments.size() <= 2,
				"Expected <deals per table> <threads> [--dump <file>], got " + String.join(" ", args));
		int deals = BotArena.Options.number(arguments, 0, "deals per table", 200);
		int threads = BotArena.Options.number(arguments, 1, "threads", Runtime.getRuntime().availableProcessors());
		BotArena.Options.require(deals >= 1, "deals per table must be at least 1, got " + deals);
		BotArena.Options.require(threads >= 1, "threads must be at least 1, got " + threads);
		Path dumpFile = dump == null ? null : Path.of(dump);
		if (dumpFile != null) {
			ensureWritable(dumpFile);
		}

		List<Prediction> predictions = play(tables(), deals, threads);
		if (dumpFile != null) {
			Files.write(dumpFile, csv(predictions));
		}
		System.out.print(report(predictions));
	}

	record HandTable(int players, int handSize) {
	}

	record Prediction(int players, int handSize, int deal, double secondOptionShare, boolean secondOption,
					  double chance, boolean matched) {
	}

	/**
	 * Fails now rather than after the run, and leaves an existing file as it is.
	 */
	static void ensureWritable(Path file) throws IOException {
		Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND).close();
	}

	static List<String> csv(List<Prediction> predictions) {
		List<String> lines = new ArrayList<>(predictions.size() + 1);
		lines.add("players,handSize,deal,secondOptionShare,secondOption,chance,matched");
		for (Prediction prediction : predictions) {
			lines.add(prediction.players() + "," + prediction.handSize() + "," + prediction.deal() + ","
					+ prediction.secondOptionShare() + "," + prediction.secondOption() + "," + prediction.chance() + ","
					+ prediction.matched());
		}
		return lines;
	}

	static List<HandTable> tables() {
		List<HandTable> tables = new ArrayList<>();
		for (int players = Table.MINIMUM_USERS; players <= Table.MAXIMUM_USERS; players++) {
			int maxHand = Math.min(new CardProvider(players).getDeckOfCardsSize() / players, Game.MAX_CARDS_IN_HAND);
			for (int handSize = 1; handSize <= maxHand; handSize++) {
				tables.add(new HandTable(players, handSize));
			}
		}
		return tables;
	}

	static List<Prediction> play(List<HandTable> tables, int deals, int threads)
			throws InterruptedException, ExecutionException {
		List<Callable<List<Prediction>>> tableDeals = new ArrayList<>();
		for (HandTable table : tables) {
			for (double secondOptionShare : SECOND_OPTION_SHARES) {
				for (int deal = 0; deal < deals; deal++) {
					int dealId = deal;
					tableDeals.add(() -> playDeal(table, dealId, secondOptionShare));
				}
			}
		}

		List<Prediction> predictions = new ArrayList<>();
		BotArena.runAll(tableDeals, threads).forEach(predictions::addAll);
		return predictions;
	}

	private static List<Prediction> playDeal(HandTable table, int deal, double secondOptionShare) {
		CalibrationBot[] seats = new CalibrationBot[table.players()];
		for (int seat = 0; seat < seats.length; seat++) {
			seats[seat] = new CalibrationBot(secondOptionShare);
		}
		BotArena.playOneSet(seats, table.handSize(), new CardProvider(table.players()).getShuffledDeckOfCards());

		List<Prediction> predictions = new ArrayList<>(seats.length);
		for (CalibrationBot seat : seats) {
			predictions.add(new Prediction(table.players(), table.handSize(), deal, secondOptionShare,
					seat.shownSecondOption, seat.shownChance, seat.matched));
		}
		return predictions;
	}

	static String report(List<Prediction> predictions) {
		Map<Boolean, Map<Integer, List<Prediction>>> byHandAndPlayers = predictions.stream()
				.collect(Collectors.partitioningBy(p -> MctsEngine.exceedsFirstCardLimit(p.players(), p.handSize()),
						Collectors.groupingBy(Prediction::players, TreeMap::new, Collectors.toList())));

		StringBuilder report = new StringBuilder();
		byHandAndPlayers.forEach((largeHands, byPlayers) -> {
			if (!byPlayers.isEmpty()) {
				// Above the limit per-card terms scored worse on held-out deals
				appendCalibration(report, largeHands ? "LARGE" : "SMALL", !largeHands, byPlayers);
			}
		});
		report.append("\nThen update the values MctsEngineTest.HintChanceTest and BotSimulatorTest expect.\n");
		return report.toString();
	}

	private static void appendCalibration(StringBuilder report, String hands, boolean perCard,
										  Map<Integer, List<Prediction>> byPlayers) {
		report.append("\nMctsEngine.").append(hands).append("_HAND_HINT_CALIBRATION_BY_PLAYERS\n");
		report.append("Held-out deals (fit on even deals, scored on odd), |mean gap| / calibration error in points:\n");
		byPlayers.forEach((players, all) -> {
			List<Prediction> even = all.stream().filter(p -> p.deal() % 2 == 0).toList();
			List<Prediction> odd = all.stream().filter(p -> p.deal() % 2 == 1).toList();
			LogOddsCalibration heldOut = fit(even, perCard);

			for (boolean secondOption : new boolean[]{false, true}) {
				List<Prediction> shown = odd.stream().filter(p -> p.secondOption() == secondOption).toList();
				if (shown.isEmpty()) {
					continue;
				}
				report.append(String.format(Locale.ROOT,
						"  %d players, %-6s option, %5d predictions:  uncorrected %s   current %s   refit %s%n",
						players, secondOption ? "second" : "first", shown.size(),
						score(shown, Prediction::chance),
						score(shown, p -> MctsEngine.hintChance(p.chance(), p.players(), p.handSize())),
						score(shown, p -> heldOut.apply(p.chance(), p.handSize()))));
			}
		});

		report.append("Refit on all deals, to paste:\n");
		byPlayers.forEach((players, all) -> {
			LogOddsCalibration refit = fit(all, perCard);
			String perCardTerms = perCard
					? String.format(Locale.ROOT, ", %.3f, %.3f", refit.shiftPerCard(), refit.scalePerCard())
					: "";
			report.append(String.format(Locale.ROOT, "\t\t\t%d, new LogOddsCalibration(%.3f, %.3f%s),%n",
					players, refit.shift(), refit.scale(), perCardTerms));
		});
	}

	/**
	 * Logistic regression of matched on the chance's log-odds, per card also on the hand size. Chances of 0 and 1 are
	 * left out, since the calibration keeps them as they are.
	 */
	static LogOddsCalibration fit(List<Prediction> predictions, boolean perCard) {
		List<Prediction> uncertain = predictions.stream().filter(p -> p.chance() > 0 && p.chance() < 1).toList();
		// With one hand size a per-card term cannot be told apart from the constant one
		int parameters = perCard && uncertain.stream().mapToInt(Prediction::handSize).distinct().count() > 1 ? 4 : 2;
		double[] coefficients = {0, 1, 0, 0};
		for (int iteration = 0; iteration < 100; iteration++) {
			LogOddsCalibration calibration = calibration(coefficients);
			double[] gradient = new double[parameters];
			double[][] hessian = new double[parameters][parameters];
			for (Prediction prediction : uncertain) {
				double logOdds = Math.log(prediction.chance() / (1 - prediction.chance()));
				double[] features = {1, logOdds, prediction.handSize(), prediction.handSize() * logOdds};
				double fitted = calibration.apply(prediction.chance(), prediction.handSize());
				double residual = (prediction.matched() ? 1 : 0) - fitted;
				double weight = fitted * (1 - fitted);
				for (int i = 0; i < parameters; i++) {
					gradient[i] += residual * features[i];
					for (int j = 0; j < parameters; j++) {
						hessian[i][j] += weight * features[i] * features[j];
					}
				}
			}
			double[] step = solve(hessian, gradient);
			double stepSize = 0;
			for (int i = 0; i < parameters; i++) {
				coefficients[i] += step[i];
				stepSize += Math.abs(step[i]);
			}
			if (stepSize < 1e-9) {
				break;
			}
		}
		return calibration(coefficients);
	}

	private static LogOddsCalibration calibration(double[] coefficients) {
		return new LogOddsCalibration(coefficients[0], coefficients[1], coefficients[2], coefficients[3]);
	}

	// Gauss-Jordan without pivoting: the Hessian is positive definite
	private static double[] solve(double[][] matrix, double[] vector) {
		int size = vector.length;
		double[][] augmented = new double[size][];
		for (int row = 0; row < size; row++) {
			augmented[row] = Arrays.copyOf(matrix[row], size + 1);
			augmented[row][size] = vector[row];
		}
		for (int column = 0; column < size; column++) {
			double[] pivotRow = augmented[column];
			for (int row = 0; row < size; row++) {
				if (row != column) {
					double factor = augmented[row][column] / pivotRow[column];
					for (int k = column; k <= size; k++) {
						augmented[row][k] -= factor * pivotRow[k];
					}
				}
			}
		}
		double[] solution = new double[size];
		for (int row = 0; row < size; row++) {
			solution[row] = augmented[row][size] / augmented[row][row];
		}
		return solution;
	}

	/**
	 * |mean(matched - shown)| and the same per chance bucket, weighted by the bucket's size, in points.
	 */
	static String score(List<Prediction> predictions, ToDoubleFunction<Prediction> shown) {
		double gap = 0;
		double[] shownSums = new double[CALIBRATION_BUCKETS];
		double[] matchedSums = new double[CALIBRATION_BUCKETS];
		int[] counts = new int[CALIBRATION_BUCKETS];
		for (Prediction prediction : predictions) {
			double chance = shown.applyAsDouble(prediction);
			double matched = prediction.matched() ? 1 : 0;
			gap += matched - chance;
			int bucket = Math.min((int) (chance * CALIBRATION_BUCKETS), CALIBRATION_BUCKETS - 1);
			shownSums[bucket] += chance;
			matchedSums[bucket] += matched;
			counts[bucket]++;
		}
		double calibrationError = 0;
		for (int bucket = 0; bucket < CALIBRATION_BUCKETS; bucket++) {
			calibrationError += Math.abs(matchedSums[bucket] - shownSums[bucket]);
		}
		return String.format(Locale.ROOT, "%4.1f / %4.1f",
				100 * Math.abs(gap) / predictions.size(), 100 * calibrationError / predictions.size());
	}

	/**
	 * Predicts from the chances the hint calibrates, the second-likeliest option for a share of its deals.
	 */
	static final class CalibrationBot implements Bot {

		private final MctsArenaBot player = new MctsArenaBot("calibration", new MctsEngine());
		private final double secondOptionShare;

		boolean shownSecondOption;
		double shownChance;
		boolean matched;

		CalibrationBot(double secondOptionShare) {
			this.secondOptionShare = secondOptionShare;
		}

		@Override
		public String name() {
			return player.name();
		}

		@Override
		public double predict(List<Card> hand, int position, int totalPlayers, int totalTricks,
							  Integer[] predictions, List<Card> unknownCards, Map<Integer, Set<Color>> voids) {
			SimulationState state = MctsArenaBot.predictionState(hand, position, totalPlayers, totalTricks,
					predictions);
			int[] opponentSlots = MctsArenaBot.opponentSlots(position, totalPlayers, totalTricks);
			List<Map.Entry<Integer, Double>> options = player.engine()
					.predictionChances(state, unknownCards, opponentSlots, voids, Map.of())
					.entrySet().stream()
					.sorted(MctsEngine.LIKELIEST_FIRST)
					.toList();
			boolean second = options.size() > 1 && ThreadLocalRandom.current().nextDouble() < secondOptionShare;
			Map.Entry<Integer, Double> chosen = second ? options.get(1) : options.getFirst();
			shownSecondOption = second;
			shownChance = chosen.getValue();
			return chosen.getKey();
		}

		@Override
		public Card play(List<Card> hand, int position, int totalPlayers, int totalTricks, int tricksPlayed,
						 int[] expectedTakes, int[] actualTakes, List<Card> table,
						 List<Card> unknownCards, int[] opponentSlots, Map<Integer, Set<Color>> voids) {
			return player.play(hand, position, totalPlayers, totalTricks, tricksPlayed, expectedTakes, actualTakes,
					table, unknownCards, opponentSlots, voids);
		}

		@Override
		public void finished(boolean matchedPrediction) {
			matched = matchedPrediction;
		}
	}
}
