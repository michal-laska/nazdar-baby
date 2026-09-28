package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.point.PointProvider;
import com.lafi.cardgame.nazdarbaby.provider.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Plays whole sets between two bot versions to measure whether a change to the engine
 * actually makes it stronger. Reading the engine cannot answer that — several plausible
 * improvements measured worse than what they replaced.
 *
 * <p>Run it with {@code ./gradlew botArena --args="<deals> <players> <tricks> <threads> [--baseline <git-ref>]"}.
 * It is a main method rather than a test: a useful sample takes minutes.
 *
 * <p>Every deal is played twice with the seats mirrored, and the score reported is the
 * difference within that pair. Without the mirroring, who was dealt the aces swamps the
 * effect of the change. Even so the noise floor is around 0.7 points per deal over 600
 * deals, so treat anything smaller than about 1.5 points per deal as no evidence.
 *
 * <p>Without {@code --baseline} both seats play the current engine, which measures the noise
 * floor and the absolute hit rate. With it, the OLD seats play the engine as it was at that git
 * ref (see {@link BaselineBots}), so a change is measured against the commit it started from.
 */
public final class BotArena {

	static final String THREAD_PREFIX = "bot-arena-";

	private static final PointProvider POINT_PROVIDER = new PointProvider();

	private final int totalPlayers;
	private final int totalTricks;
	private final Bot[] seatBots;

	private BotArena(int totalPlayers, int totalTricks, Bot[] seatBots) {
		this.totalPlayers = totalPlayers;
		this.totalTricks = totalTricks;
		this.seatBots = seatBots;
	}

	public static void main(String[] args) throws Exception {
		Options options = Options.parse(args);

		BaselineBots.Baseline baseline = options.baseline() == null ? null : BaselineBots.load(options.baseline());
		Function<String, Bot> oldBots = baseline == null ? MctsArenaBot::new : baseline.botFactory();
		String oldEngine = baseline == null ? "current engine (noise floor)" : options.baseline() + " (" + baseline.commit() + ")";

		play(options, oldBots).print(options.deals(), options.players(), options.tricks(), oldEngine);
	}

	static Totals play(Options options, Function<String, Bot> oldBots) throws InterruptedException, ExecutionException {
		ExecutorService pool = Executors.newFixedThreadPool(options.threads(), Thread.ofPlatform().name(THREAD_PREFIX, 0).factory());
		// A failed deal must not leave the pool's threads keeping the JVM alive
		try {
			List<Future<DealResult>> futures = new ArrayList<>(options.deals());
			for (int deal = 0; deal < options.deals(); deal++) {
				futures.add(pool.submit(() -> playMirroredDeal(options.players(), options.tricks(), oldBots)));
			}

			Totals totals = new Totals();
			for (Future<DealResult> future : futures) {
				totals.add(future.get());
			}
			return totals;
		} finally {
			pool.shutdownNow();
		}
	}

	record Options(int deals, int players, int tricks, int threads, String baseline) {

		static Options parse(String[] args) {
			List<String> positional = new ArrayList<>();
			String baseline = null;
			for (int i = 0; i < args.length; i++) {
				if (args[i].equals("--baseline")) {
					if (i + 1 == args.length) {
						throw new IllegalArgumentException("--baseline needs a git ref");
					}
					baseline = args[++i];
				} else {
					positional.add(args[i]);
				}
			}
			if (positional.size() > 4) {
				throw new IllegalArgumentException("Expected <deals> <players> <tricks> <threads> [--baseline <git-ref>], got "
						+ String.join(" ", args));
			}

			int deals = number(positional, 0, "deals", 100);
			int players = number(positional, 1, "players", 4);
			int tricks = number(positional, 2, "tricks", 5);
			int threads = number(positional, 3, "threads", Runtime.getRuntime().availableProcessors());

			require(deals >= 1, "deals must be at least 1, got " + deals);
			require(players >= Table.MINIMUM_USERS && players <= Table.MAXIMUM_USERS,
					"players must be " + Table.MINIMUM_USERS + " to " + Table.MAXIMUM_USERS + ", got " + players);
			int maxTricks = new CardProvider(players).getDeckOfCardsSize() / players;
			require(tricks >= 1 && tricks <= maxTricks,
					"tricks must be 1 to " + maxTricks + " for " + players + " players, got " + tricks);
			require(threads >= 1, "threads must be at least 1, got " + threads);

			return new Options(deals, players, tricks, threads, baseline);
		}

		private static int number(List<String> positional, int index, String name, int fallback) {
			if (index >= positional.size()) {
				return fallback;
			}
			try {
				return Integer.parseInt(positional.get(index));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(name + " must be a number, got " + positional.get(index));
			}
		}

		private static void require(boolean valid, String message) {
			if (!valid) {
				throw new IllegalArgumentException(message);
			}
		}
	}

	private static DealResult playMirroredDeal(int totalPlayers, int totalTricks, Function<String, Bot> oldBots) {
		List<Card> deck = new CardProvider(totalPlayers).getShuffledDeckOfCards();
		DealResult result = new DealResult();

		for (int mirror = 0; mirror < 2; mirror++) {
			Bot[] seats = new Bot[totalPlayers];
			for (int seat = 0; seat < totalPlayers; seat++) {
				seats[seat] = (seat + mirror) % 2 == 0 ? new MctsArenaBot("NEW") : oldBots.apply("OLD");
			}
			new BotArena(totalPlayers, totalTricks, seats).playSet(new ArrayList<>(deck), result);
		}

		return result;
	}

	private void playSet(List<Card> deck, DealResult result) {
		List<Card> fullDeck = new ArrayList<>(deck);

		// Players in trick order; index 0 leads and predicts first, like Game.trickUsers
		List<Player> order = new ArrayList<>(totalPlayers);
		Map<Player, Set<Color>> voids = new HashMap<>();
		for (int seat = 0; seat < totalPlayers; seat++) {
			Player player = new Player(seatBots[seat]);
			for (int card = 0; card < totalTricks; card++) {
				player.hand.add(deck.removeFirst());
			}
			order.add(player);
			voids.put(player, EnumSet.noneOf(Color.class));
		}

		predict(order, fullDeck, voids);

		Set<Card> playedOut = new HashSet<>();
		for (int trick = 0; trick < totalTricks; trick++) {
			List<Card> table = new ArrayList<>(totalPlayers);
			for (int position = 0; position < totalPlayers; position++) {
				Player player = order.get(position);
				Card card = chooseCard(order, player, position, table, playedOut, fullDeck, voids);
				player.hand.remove(card);
				table.add(card);
			}
			registerVoids(order, table, voids);
			playedOut.addAll(table);

			int winnerPosition = TrickEvaluator.getWinningIndex(table);
			++order.get(winnerPosition).actualTakes;
			Collections.rotate(order, -winnerPosition);
		}

		score(order, result);
	}

	private void predict(List<Player> order, List<Card> fullDeck, Map<Player, Set<Color>> voids) {
		Integer[] predictions = new Integer[totalPlayers];

		for (int position = 0; position < totalPlayers; position++) {
			Player player = order.get(position);

			List<Card> unknownCards = new ArrayList<>(fullDeck);
			unknownCards.removeAll(player.hand);

			double guess = player.bot.predict(player.hand, position, totalPlayers, totalTricks,
					predictions, unknownCards, getVoidsByPosition(order, position, voids));

			int othersSum = 0;
			for (Integer prediction : predictions) {
				if (prediction != null) {
					othersSum += prediction;
				}
			}

			// The last player may not make the predictions sum up to the number of tricks
			boolean last = position == totalPlayers - 1;
			int forbiddenTakes = totalTricks - othersSum;
			int takes = MctsEngine.allowedPrediction(guess, candidate -> last && candidate == forbiddenTakes);

			player.expectedTakes = takes;
			predictions[position] = takes;
		}
	}

	private Card chooseCard(List<Player> order, Player player, int position, List<Card> table,
							Set<Card> playedOut, List<Card> fullDeck, Map<Player, Set<Color>> voids) {
		List<Card> legalPlays = TrickEvaluator.getLegalPlays(player.hand, table);
		if (legalPlays.size() == 1) {
			return legalPlays.getFirst();
		}

		List<Card> unknownCards = new ArrayList<>(fullDeck);
		unknownCards.removeAll(player.hand);
		unknownCards.removeAll(playedOut);
		unknownCards.removeAll(table);

		int[] expectedTakes = new int[totalPlayers];
		int[] actualTakes = new int[totalPlayers];
		int[] opponentSlots = new int[totalPlayers];
		int tricksPlayed = 0;
		for (int i = 0; i < totalPlayers; i++) {
			Player other = order.get(i);
			expectedTakes[i] = other.expectedTakes;
			actualTakes[i] = other.actualTakes;
			tricksPlayed += other.actualTakes;
			opponentSlots[i] = i == position ? 0 : other.hand.size();
		}

		Card card = player.bot.play(player.hand, position, totalPlayers, player.hand.size() + tricksPlayed,
				tricksPlayed, expectedTakes, actualTakes, table, unknownCards, opponentSlots,
				getVoidsByPosition(order, position, voids));

		return card != null && legalPlays.contains(card) ? card : legalPlays.getFirst();
	}

	private Map<Integer, Set<Color>> getVoidsByPosition(List<Player> order, int position,
														Map<Player, Set<Color>> voids) {
		Map<Integer, Set<Color>> voidsByPosition = new HashMap<>();
		for (int i = 0; i < totalPlayers; i++) {
			if (i == position) {
				continue;
			}
			Set<Color> known = voids.get(order.get(i));
			if (!known.isEmpty()) {
				voidsByPosition.put(i, EnumSet.copyOf(known));
			}
		}
		return voidsByPosition;
	}

	private void registerVoids(List<Player> order, List<Card> table, Map<Player, Set<Color>> voids) {
		Color leadingColor = table.getFirst().getColor();

		for (int i = 1; i < table.size(); i++) {
			Card card = table.get(i);
			if (card.getColor() == leadingColor) {
				continue;
			}

			Set<Color> known = voids.get(order.get(i));
			known.add(leadingColor);
			if (card.getColor() != Color.HEARTS) {
				known.add(Color.HEARTS);
			}
		}
	}

	private void score(List<Player> order, DealResult result) {
		int winCount = 0;
		for (Player player : order) {
			if (player.isWinner()) {
				++winCount;
			}
		}

		float winPoints = POINT_PROVIDER.getWinnerPoints(totalPlayers, winCount);
		float losePoints = POINT_PROVIDER.getLoserPoints(totalPlayers, winCount);

		for (Player player : order) {
			boolean won = player.isWinner();
			result.add(player.bot.name(), won ? winPoints : losePoints, won);
		}
	}

	public interface Bot {

		String name();

		double predict(List<Card> hand, int position, int totalPlayers, int totalTricks,
					   Integer[] predictions, List<Card> unknownCards, Map<Integer, Set<Color>> voids);

		Card play(List<Card> hand, int position, int totalPlayers, int totalTricks, int tricksPlayed,
				  int[] expectedTakes, int[] actualTakes, List<Card> table,
				  List<Card> unknownCards, int[] opponentSlots, Map<Integer, Set<Color>> voids);
	}

	private static final class Player {

		private final Bot bot;
		private final List<Card> hand = new ArrayList<>();

		private int expectedTakes;
		private int actualTakes;

		private Player(Bot bot) {
			this.bot = bot;
		}

		private boolean isWinner() {
			return expectedTakes == actualTakes;
		}
	}

	/**
	 * Points and hits of both versions within one mirrored deal.
	 */
	private static final class DealResult {

		private final Map<String, double[]> pointsAndHits = new HashMap<>();

		private void add(String name, float points, boolean hit) {
			double[] sums = pointsAndHits.computeIfAbsent(name, _ -> new double[3]);
			sums[0] += points;
			sums[1] += hit ? 1 : 0;
			++sums[2];
		}

		private double getPoints(String name) {
			return pointsAndHits.getOrDefault(name, new double[3])[0];
		}
	}

	static final class Totals {

		private final Map<String, double[]> pointsAndHits = new HashMap<>();

		private double diffSum;
		private double diffSquareSum;

		private void add(DealResult result) {
			result.pointsAndHits.forEach((name, sums) -> {
				double[] totals = pointsAndHits.computeIfAbsent(name, _ -> new double[3]);
				for (int i = 0; i < sums.length; i++) {
					totals[i] += sums[i];
				}
			});

			double diff = result.getPoints("NEW") - result.getPoints("OLD");
			diffSum += diff;
			diffSquareSum += diff * diff;
		}

		private void print(int deals, int totalPlayers, int totalTricks, String oldEngine) {
			System.out.println("deals=" + deals + " (each played twice, seats mirrored)"
					+ " players=" + totalPlayers + " tricks=" + totalTricks);
			System.out.println("OLD = " + oldEngine);

			pointsAndHits.forEach((name, totals) -> System.out.printf("%-4s points=%9.1f  hitRate=%5.2f%%%n",
					name, totals[0], 100 * totals[1] / totals[2]));

			double meanDiff = diffSum / deals;
			double variance = diffSquareSum / deals - meanDiff * meanDiff;
			double standardError = Math.sqrt(variance / deals);

			System.out.printf("NEW - OLD per deal: %+.3f +/- %.3f (%.1f sigma)%n",
					meanDiff, standardError, standardError == 0 ? 0 : meanDiff / standardError);
		}
	}
}
