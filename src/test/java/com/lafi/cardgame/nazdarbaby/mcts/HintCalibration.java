package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.mcts.BotArena.Bot;
import com.lafi.cardgame.nazdarbaby.mcts.MctsEngine.LogOddsCalibration;
import com.lafi.cardgame.nazdarbaby.provider.Game;
import com.lafi.cardgame.nazdarbaby.provider.Table;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Re-fits the hint calibrations in {@link MctsEngine} after a change to how the bots play.
 *
 * <p>Run it with {@code ./gradlew hintCalibration --args="<deals per table> <threads> [--dump <file>]"}. Every hand
 * size the game deals is played with bots in every seat, also with each seat predicting its second-likeliest option
 * half the time, since the hint shows both. The default 200 deals per table take 30 to 40 minutes on 10 threads.
 * {@code --dump} also writes every prediction to a CSV file, headed by the commit it was played at, so later
 * questions need no new run; until the run finishes, the deals played so far are kept in {@code <file>.partial}, and
 * a run refuses to start while such a file is left.
 * {@code --args="--from <file>[,<file>...] [--score <file>[,<file>...]]"} reports on such files without playing, and
 * warns when the engine has changed since or the run did not finish. With {@code --score} the held-out rows are
 * fitted on the {@code --from} files and scored on the {@code --score} ones, instead of splitting by deal.
 */
public final class HintCalibration {

	private static final double[] SECOND_OPTION_SHARES = {0.0, 0.5};
	private static final int CALIBRATION_BUCKETS = 5;
	private static final int WORST_TABLES = 5;
	private static final String CSV_HEADER = "players,handSize,deal,secondOptionShare,secondOption,chance,matched";
	private static final int CSV_FIELDS = CSV_HEADER.split(",").length;
	private static final String PROVENANCE_PREFIX = "# ";
	private static final Pattern PROVENANCE_COMMIT = Pattern.compile("commit=(\\S+)");
	private static final String UNCHECKED = ", so it cannot be checked against this checkout";
	private static final String INCOMPLETE = " incomplete";
	// Dumps record the commit of this checkout and are checked against it
	private static final Path CHECKOUT = Path.of(".");
	// What the predictions depend on besides the engine: the rules it plays by and the arena's adapter. BotArena and
	// this tool are left out, since they change for unrelated reasons and would make every dump look stale.
	private static final List<String> CALIBRATION_SOURCES = List.of(BaselineBots.ENGINE_SOURCES,
			"src/main/java/com/lafi/cardgame/nazdarbaby/card/", "src/main/java/com/lafi/cardgame/nazdarbaby/point/",
			BaselineBots.ADAPTER_SOURCE.toString());

	private HintCalibration() {
	}

	public static void main(String[] args) throws Exception {
		List<String> arguments = new ArrayList<>(Arrays.asList(args));
		String dump = BotArena.Options.takeFlag(arguments, "--dump", "a file");
		String from = BotArena.Options.takeFlag(arguments, "--from", "a file");
		String score = BotArena.Options.takeFlag(arguments, "--score", "a file");
		if (from != null) {
			BotArena.Options.require(arguments.isEmpty() && dump == null,
					"--from takes no other arguments than --score, got " + String.join(" ", args));
			System.out.print(reportOnDumps(from, score));
			return;
		}
		BotArena.Options.require(score == null, "--score needs --from");
		BotArena.Options.require(arguments.size() <= 2,
				"Expected <deals per table> <threads> [--dump <file>] or --from <file>[,<file>...] [--score <file>[,"
						+ "<file>...]], got " + String.join(" ", args));
		int deals = BotArena.Options.number(arguments, 0, "deals per table", 200);
		int threads = BotArena.Options.number(arguments, 1, "threads", Runtime.getRuntime().availableProcessors());
		BotArena.Options.require(deals >= 1, "deals per table must be at least 1, got " + deals);
		BotArena.Options.require(threads >= 1, "threads must be at least 1, got " + threads);
		List<Prediction> predictions =
				dump == null ? play(tables(), deals, threads) : playAndDump(deals, threads, Path.of(dump));
		System.out.print(report(predictions));
	}

	private static List<Prediction> playAndDump(int deals, int threads, Path dumpFile)
			throws IOException, InterruptedException, ExecutionException {
		String provenance = provenance(commit(CHECKOUT), deals, LocalDate.now());
		Path partialFile = partial(dumpFile);
		// Created only if absent, in one step, so two runs started together cannot both take it
		try {
			Files.write(partialFile, csv(provenance + INCOMPLETE, List.of()), StandardOpenOption.CREATE_NEW,
					StandardOpenOption.WRITE);
		} catch (FileAlreadyExistsException e) {
			throw new IllegalArgumentException(partialFile
					+ " is left from a run that did not finish: report on it with --from, or move it away first", e);
		}
		try {
			ensureWritable(dumpFile);
		} catch (IOException e) {
			Files.delete(partialFile);
			throw e;
		}

		List<Prediction> predictions = play(tables(), deals, threads, deal -> append(partialFile, deal));
		Files.write(dumpFile, csv(provenance, predictions));
		Files.delete(partialFile);
		return predictions;
	}

	private static String reportOnDumps(String from, String score) throws IOException, InterruptedException {
		List<Dump> fitted = readCsv(from);
		List<Dump> scored = score == null ? List.of() : readCsv(score);
		return sources("Fitted on", fitted, CHECKOUT) + sources("Scored on", scored, CHECKOUT)
				+ report(predictions(fitted), score == null ? null : predictions(scored));
	}

	record HandTable(int players, int handSize) {
	}

	record Prediction(int players, int handSize, int deal, double secondOptionShare, boolean secondOption,
					  double chance, boolean matched) {
	}

	/**
	 * A CSV file of predictions; the provenance is null for a file written before it was recorded, and lastLineCut
	 * says an unfinished run's half-written last line was skipped.
	 */
	record Dump(String source, String provenance, List<Prediction> predictions, boolean lastLineCut) {

		boolean incomplete() {
			return incomplete(provenance);
		}

		static boolean incomplete(String provenance) {
			return provenance != null && provenance.endsWith(INCOMPLETE);
		}
	}

	/**
	 * Fails now rather than after the run, and leaves an existing file as it is.
	 */
	static void ensureWritable(Path file) throws IOException {
		Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND).close();
	}

	static String provenance(String commit, int deals, LocalDate date) {
		return "commit=" + commit + " deals=" + deals + " date=" + date;
	}

	/**
	 * Marked dirty when the calibration sources differ from it, new files included, since then the commit alone does
	 * not reproduce the predictions.
	 */
	static String commit(Path directory) throws InterruptedException {
		try {
			String commit = BaselineBots.git("-C", directory.toString(), "rev-parse", "--short", "HEAD");
			if (commit.isEmpty()) {
				return "unknown";
			}
			return gitOnCalibrationSources(directory, "status", "--porcelain").isEmpty() ? commit : commit + "-dirty";
		} catch (IOException e) {
			return "unknown";
		}
	}

	private static String gitOnCalibrationSources(Path directory, String... command)
			throws IOException, InterruptedException {
		List<String> arguments = new ArrayList<>(List.of("-C", directory.toString()));
		arguments.addAll(List.of(command));
		arguments.add("--");
		arguments.addAll(CALIBRATION_SOURCES);
		return BaselineBots.git(arguments.toArray(String[]::new));
	}

	static List<String> csv(String provenance, List<Prediction> predictions) {
		List<String> lines = new ArrayList<>(predictions.size() + 2);
		lines.add(PROVENANCE_PREFIX + provenance);
		lines.add(CSV_HEADER);
		predictions.stream().map(HintCalibration::csvLine).forEach(lines::add);
		return lines;
	}

	private static String csvLine(Prediction prediction) {
		return prediction.players() + "," + prediction.handSize() + "," + prediction.deal() + ","
				+ prediction.secondOptionShare() + "," + prediction.secondOption() + "," + prediction.chance() + ","
				+ prediction.matched();
	}

	/**
	 * Where a run with a dump keeps the deals played so far, so a run that stops early leaves them behind without
	 * touching an earlier dump.
	 */
	static Path partial(Path dumpFile) {
		return dumpFile.resolveSibling(dumpFile.getFileName() + ".partial");
	}

	static synchronized void append(Path file, List<Prediction> deal) {
		try {
			Files.write(file, deal.stream().map(HintCalibration::csvLine).toList(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static List<Dump> readCsv(String files) throws IOException {
		List<Dump> dumps = new ArrayList<>();
		for (String file : files.split(",")) {
			dumps.add(fromCsv(Files.readAllLines(Path.of(file)), file));
		}
		return dumps;
	}

	static Dump fromCsv(List<String> lines, String source) {
		boolean hasProvenance = !lines.isEmpty() && lines.getFirst().startsWith(PROVENANCE_PREFIX);
		String provenance = hasProvenance ? lines.getFirst().substring(PROVENANCE_PREFIX.length()) : null;
		int header = hasProvenance ? 1 : 0;
		BotArena.Options.require(lines.size() > header && lines.get(header).equals(CSV_HEADER),
				source + ": expected the header " + CSV_HEADER);
		boolean incomplete = Dump.incomplete(provenance);
		List<Prediction> predictions = new ArrayList<>(lines.size() - header - 1);
		for (int line = header + 1; line < lines.size(); line++) {
			try {
				predictions.add(prediction(lines.get(line).split(",", -1)));
			} catch (IllegalArgumentException e) {
				// A run stopped while appending a deal can leave its last line half written
				if (incomplete && line == lines.size() - 1) {
					return new Dump(source, provenance, predictions, true);
				}
				throw new IllegalArgumentException(source + " line " + (line + 1) + ": " + e.getMessage(), e);
			}
		}
		return new Dump(source, provenance, predictions, false);
	}

	private static List<Prediction> predictions(List<Dump> dumps) {
		return dumps.stream().flatMap(dump -> dump.predictions().stream()).toList();
	}

	private static String sources(String label, List<Dump> dumps, Path checkout) throws InterruptedException {
		StringBuilder sources = new StringBuilder();
		for (Dump dump : dumps) {
			sources.append(label).append(' ').append(dump.source()).append(": ")
					.append(dump.provenance() == null ? "no provenance line" : dump.provenance()).append('\n');
			if (dump.incomplete()) {
				sources.append("  warning: its run did not finish, so it lacks some deals\n");
			}
			if (dump.lastLineCut()) {
				sources.append("  warning: its last line was cut off when the run stopped, and is skipped\n");
			}
			String warning = engineWarning(dump.provenance(), checkout);
			if (warning != null) {
				sources.append("  warning: ").append(warning).append('\n');
			}
		}
		return sources.toString();
	}

	/**
	 * Why the dump may not describe the engine in the checkout, or null when its calibration sources are the same.
	 */
	static String engineWarning(String provenance, Path checkout) throws InterruptedException {
		Matcher matcher = provenance == null ? null : PROVENANCE_COMMIT.matcher(provenance);
		String commit = matcher != null && matcher.find() ? matcher.group(1) : "unknown";
		if (commit.equals("unknown")) {
			return "it records no commit" + UNCHECKED;
		}
		if (commit.endsWith("-dirty")) {
			return "it was played on uncommitted changes to the engine or what it plays through" + UNCHECKED;
		}
		try {
			if (BaselineBots.git("-C", checkout.toString(), "rev-parse", "--verify", "--quiet", commit + "^{commit}")
					.isEmpty()) {
				return commit + " is not in this repository" + UNCHECKED;
			}
			return gitOnCalibrationSources(checkout, "diff", "--name-only", commit).isEmpty()
					? null
					: "the engine or what it plays through changed since " + commit;
		} catch (IOException e) {
			return "git is not available" + UNCHECKED;
		}
	}

	private static Prediction prediction(String[] fields) {
		BotArena.Options.require(fields.length == CSV_FIELDS,
				"expected " + CSV_FIELDS + " fields, got " + fields.length);
		Prediction prediction = new Prediction(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
				Integer.parseInt(fields[2]), Double.parseDouble(fields[3]), bool(fields[4]),
				Double.parseDouble(fields[5]), bool(fields[6]));
		BotArena.Options.require(prediction.players() >= Table.MINIMUM_USERS
				&& prediction.players() <= Table.MAXIMUM_USERS, "players must be " + Table.MINIMUM_USERS + " to "
				+ Table.MAXIMUM_USERS + ", got " + prediction.players());
		BotArena.Options.require(prediction.handSize() >= 1,
				"hand size must be at least 1, got " + prediction.handSize());
		BotArena.Options.require(prediction.deal() >= 0, "deal must not be negative, got " + prediction.deal());
		BotArena.Options.require(prediction.chance() >= 0 && prediction.chance() <= 1,
				"chance must be 0 to 1, got " + prediction.chance());
		return prediction;
	}

	private static boolean bool(String field) {
		BotArena.Options.require(field.equals("true") || field.equals("false"), "expected true or false, got " + field);
		return field.equals("true");
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
		return play(tables, deals, threads, deal -> {});
	}

	/**
	 * Hands each deal's predictions to the consumer as soon as the deal is played, from the playing thread.
	 */
	static List<Prediction> play(List<HandTable> tables, int deals, int threads, Consumer<List<Prediction>> played)
			throws InterruptedException, ExecutionException {
		List<Callable<List<Prediction>>> tableDeals = new ArrayList<>();
		for (HandTable table : tables) {
			for (double secondOptionShare : SECOND_OPTION_SHARES) {
				for (int deal = 0; deal < deals; deal++) {
					int dealId = deal;
					tableDeals.add(() -> {
						List<Prediction> predictions = playDeal(table, dealId, secondOptionShare);
						played.accept(predictions);
						return predictions;
					});
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
		return report(predictions, null);
	}

	/**
	 * Without scored predictions the held-out rows split the predictions by deal; with them, they fit on all the
	 * predictions and score on those.
	 */
	static String report(List<Prediction> predictions, List<Prediction> scored) {
		Map<Boolean, Map<Integer, List<Prediction>>> scoredByHandAndPlayers =
				scored == null ? null : byHandAndPlayers(scored);

		StringBuilder report = new StringBuilder();
		byHandAndPlayers(predictions).forEach((largeHands, byPlayers) -> {
			if (!byPlayers.isEmpty()) {
				// Above the limit per-card terms scored worse on held-out deals
				appendCalibration(report, largeHands ? "LARGE" : "SMALL", !largeHands, byPlayers,
						scoredByHandAndPlayers == null ? null : scoredByHandAndPlayers.get(largeHands));
			}
		});
		report.append("\nThen update the values MctsEngineTest.HintChanceTest and BotSimulatorTest expect.\n");
		return report.toString();
	}

	private static Map<Boolean, Map<Integer, List<Prediction>>> byHandAndPlayers(List<Prediction> predictions) {
		return predictions.stream()
				.collect(Collectors.partitioningBy(p -> MctsEngine.exceedsFirstCardLimit(p.players(), p.handSize()),
						Collectors.groupingBy(Prediction::players, TreeMap::new, Collectors.toList())));
	}

	private static void appendCalibration(StringBuilder report, String hands, boolean perCard,
										  Map<Integer, List<Prediction>> byPlayers,
										  Map<Integer, List<Prediction>> scoredByPlayers) {
		report.append("\nMctsEngine.").append(hands).append("_HAND_HINT_CALIBRATION_BY_PLAYERS\n");
		report.append(scoredByPlayers == null
						? "Held-out deals (fit on even deals, scored on odd)"
						: "Held-out files (fit on --from, scored on --score)")
				.append(", |mean gap| / calibration error in points:\n");
		byPlayers.forEach((players, all) -> {
			List<Prediction> fittedOn = scoredByPlayers == null
					? all.stream().filter(p -> p.deal() % 2 == 0).toList()
					: all;
			List<Prediction> scoredOn = scoredByPlayers == null
					? all.stream().filter(p -> p.deal() % 2 == 1).toList()
					: scoredByPlayers.getOrDefault(players, List.of());
			LogOddsCalibration heldOut = fit(fittedOn, perCard);

			for (boolean secondOption : new boolean[]{false, true}) {
				List<Prediction> shown = scoredOn.stream().filter(p -> p.secondOption() == secondOption).toList();
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

		String fromFiles = scoredByPlayers == null ? "" : " of the --from files";
		appendWorstTables(report, fromFiles, byPlayers.values().stream().flatMap(List::stream).toList());

		report.append("Refit on all deals").append(fromFiles).append(", to paste:\n");
		byPlayers.forEach((players, all) -> {
			LogOddsCalibration refit = fit(all, perCard);
			String perCardTerms = perCard
					? String.format(Locale.ROOT, ", %.3f, %.3f", refit.shiftPerCard(), refit.scalePerCard())
					: "";
			report.append(String.format(Locale.ROOT, "\t\t\t%d, new LogOddsCalibration(%.3f, %.3f%s),%n",
					players, refit.shift(), refit.scale(), perCardTerms));
		});
	}

	private record TableOption(int players, int handSize, boolean secondOption) {
	}

	/**
	 * The tables and options whose matches stray furthest from the current calibration, in binomial standard errors:
	 * two independent runs differed per table by about as much as those predict.
	 */
	private static void appendWorstTables(StringBuilder report, String fromFiles, List<Prediction> predictions) {
		report.append("Worst tables").append(fromFiles)
				.append(" with the current calibration, gap in points (binomial standard errors):\n");
		predictions.stream()
				.collect(Collectors.groupingBy(p -> new TableOption(p.players(), p.handSize(), p.secondOption())))
				.entrySet().stream()
				.map(entry -> TableGap.of(entry.getKey(), entry.getValue()))
				.filter(gap -> gap.variance() > 0)
				.sorted(Comparator.comparingDouble(TableGap::standardErrors).reversed())
				.limit(WORST_TABLES)
				.forEach(gap -> report.append(String.format(Locale.ROOT,
						"  %d players, %2d-card hands, %-6s option, %5d predictions:  %+5.1f (%.1f)%n",
						gap.table().players(), gap.table().handSize(), gap.table().secondOption() ? "second" : "first",
						gap.predictions(), 100 * gap.gap() / gap.predictions(), gap.standardErrors())));
	}

	private record TableGap(TableOption table, int predictions, double gap, double variance) {

		static TableGap of(TableOption table, List<Prediction> predictions) {
			double gap = 0;
			double variance = 0;
			for (Prediction prediction : predictions) {
				double shown = MctsEngine.hintChance(prediction.chance(), prediction.players(), prediction.handSize());
				gap += (prediction.matched() ? 1 : 0) - shown;
				variance += shown * (1 - shown);
			}
			return new TableGap(table, predictions.size(), gap, variance);
		}

		double standardErrors() {
			return Math.abs(gap) / Math.sqrt(variance);
		}
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
