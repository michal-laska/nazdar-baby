package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.point.PointProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Plays whole sets between two bot versions to measure whether a change to the engine
 * actually makes it stronger. Reading the engine cannot answer that — several plausible
 * improvements measured worse than what they replaced.
 *
 * <p>Run it with {@code ./gradlew botArena --args="<deals> <players> <tricks> <threads>"}.
 * It is a main method rather than a test: a useful sample takes minutes.
 *
 * <p>Every deal is played twice with the seats mirrored, and the score reported is the
 * difference within that pair. Without the mirroring, who was dealt the aces swamps the
 * effect of the change. Even so the noise floor is around 0.7 points per deal over 600
 * deals, so treat anything smaller than about 1.5 points per deal as no evidence.
 *
 * <p>Out of the box both seats play the current engine, which measures the noise floor and
 * the absolute hit rate. To compare two versions, copy {@code com.lafi.cardgame.nazdarbaby.mcts}
 * to a second package (changing only the package declarations), and point {@link #createOldBot()}
 * at a {@link Bot} backed by that copy.
 */
public final class BotArena {

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
		int deals = args.length > 0 ? Integer.parseInt(args[0]) : 100;
		int totalPlayers = args.length > 1 ? Integer.parseInt(args[1]) : 4;
		int totalTricks = args.length > 2 ? Integer.parseInt(args[2]) : 5;
		int threads = args.length > 3 ? Integer.parseInt(args[3]) : Runtime.getRuntime().availableProcessors();

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		List<Future<DealResult>> futures = new ArrayList<>(deals);

		for (int deal = 0; deal < deals; deal++) {
			futures.add(pool.submit(() -> playMirroredDeal(totalPlayers, totalTricks)));
		}

		Totals totals = new Totals();
		for (Future<DealResult> future : futures) {
			totals.add(future.get());
		}
		pool.shutdown();

		totals.print(deals, totalPlayers, totalTricks);
	}

	private static DealResult playMirroredDeal(int totalPlayers, int totalTricks) {
		List<Card> deck = new CardProvider(totalPlayers).getShuffledDeckOfCards();
		DealResult result = new DealResult();

		for (int mirror = 0; mirror < 2; mirror++) {
			Bot[] seats = new Bot[totalPlayers];
			for (int seat = 0; seat < totalPlayers; seat++) {
				seats[seat] = (seat + mirror) % 2 == 0 ? createNewBot() : createOldBot();
			}
			new BotArena(totalPlayers, totalTricks, seats).playSet(new ArrayList<>(deck), result);
		}

		return result;
	}

	private static Bot createNewBot() {
		return new MctsBot("NEW");
	}

	/**
	 * The version to measure against. Point this at a copy of the engine to compare two versions.
	 */
	private static Bot createOldBot() {
		return new MctsBot("OLD");
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

			int winnerPosition = getWinningIndex(table);
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
			int takes = (int) Math.round(guess);

			int othersSum = 0;
			for (Integer prediction : predictions) {
				if (prediction != null) {
					othersSum += prediction;
				}
			}

			// The last player may not make the predictions sum up to the number of tricks
			boolean last = position == totalPlayers - 1;
			if (last && othersSum + takes == totalTricks) {
				takes = guess > takes || takes == 0 ? takes + 1 : takes - 1;
			}

			player.expectedTakes = takes;
			predictions[position] = takes;
		}
	}

	private Card chooseCard(List<Player> order, Player player, int position, List<Card> table,
							Set<Card> playedOut, List<Card> fullDeck, Map<Player, Set<Color>> voids) {
		List<Card> legalPlays = getLegalPlays(player.hand, table);
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

		int loseCount = totalPlayers - winCount;
		float winPoints = POINT_PROVIDER.getWinnerPoints(totalPlayers, winCount);
		float losePoints = loseCount == 0 ? 0 : winCount * winPoints / -loseCount;

		for (Player player : order) {
			boolean won = player.isWinner();
			result.add(player.bot.name(), won ? winPoints : losePoints, won);
		}
	}

	private static List<Card> getLegalPlays(List<Card> hand, List<Card> table) {
		if (table.isEmpty()) {
			return hand;
		}

		Color leadingColor = table.getFirst().getColor();
		List<Card> leadingColorCards = hand.stream().filter(card -> card.getColor() == leadingColor).toList();
		if (!leadingColorCards.isEmpty()) {
			return leadingColorCards;
		}

		List<Card> hearts = hand.stream().filter(card -> card.getColor() == Color.HEARTS).toList();
		return hearts.isEmpty() ? hand : hearts;
	}

	private static int getWinningIndex(List<Card> table) {
		Card winningCard = table.getFirst();
		int winningIndex = 0;

		for (int i = 1; i < table.size(); i++) {
			Card card = table.get(i);
			if (winningCard.getColor() == card.getColor()) {
				if (card.getValue() > winningCard.getValue()) {
					winningCard = card;
					winningIndex = i;
				}
			} else if (card.getColor() == Color.HEARTS) {
				winningCard = card;
				winningIndex = i;
			}
		}

		return winningIndex;
	}

	interface Bot {

		String name();

		double predict(List<Card> hand, int position, int totalPlayers, int totalTricks,
					   Integer[] predictions, List<Card> unknownCards, Map<Integer, Set<Color>> voids);

		Card play(List<Card> hand, int position, int totalPlayers, int totalTricks, int tricksPlayed,
				  int[] expectedTakes, int[] actualTakes, List<Card> table,
				  List<Card> unknownCards, int[] opponentSlots, Map<Integer, Set<Color>> voids);
	}

	private record MctsBot(String name, MctsEngine engine) implements Bot {

		private MctsBot(String name) {
			this(name, new MctsEngine());
		}

		@Override
		public double predict(List<Card> hand, int position, int totalPlayers, int totalTricks,
							  Integer[] predictions, List<Card> unknownCards, Map<Integer, Set<Color>> voids) {
			int[] expectedTakes = new int[totalPlayers];
			int[] opponentSlots = new int[totalPlayers];
			int predictionsDone = 0;

			List<List<Card>> hands = new ArrayList<>(totalPlayers);
			for (int i = 0; i < totalPlayers; i++) {
				hands.add(i == position ? new ArrayList<>(hand) : new ArrayList<>());
				opponentSlots[i] = i == position ? 0 : totalTricks;
				if (predictions[i] != null) {
					expectedTakes[i] = predictions[i];
					++predictionsDone;
				}
			}

			SimulationState state = new SimulationState(hands, expectedTakes, new int[totalPlayers],
					new ArrayList<>(), SimulationState.Phase.PREDICTING, 0, position, 0, totalTricks,
					position, predictionsDone);
			for (int i = 0; i < totalPlayers; i++) {
				if (predictions[i] != null) {
					state.setKnownPrediction(i);
				}
			}

			return engine.predictTakes(state, unknownCards, opponentSlots, voids, Map.of());
		}

		@Override
		public Card play(List<Card> hand, int position, int totalPlayers, int totalTricks, int tricksPlayed,
						 int[] expectedTakes, int[] actualTakes, List<Card> table,
						 List<Card> unknownCards, int[] opponentSlots, Map<Integer, Set<Color>> voids) {
			List<List<Card>> hands = new ArrayList<>(totalPlayers);
			for (int i = 0; i < totalPlayers; i++) {
				hands.add(i == position ? new ArrayList<>(hand) : new ArrayList<>());
			}

			SimulationState state = new SimulationState(hands, expectedTakes.clone(), actualTakes.clone(),
					new ArrayList<>(table), SimulationState.Phase.PLAYING, 0, position, tricksPlayed,
					totalTricks, position, totalPlayers);
			for (int i = 0; i < totalPlayers; i++) {
				state.setKnownPrediction(i);
			}

			return engine.selectCard(state, unknownCards, opponentSlots, voids, Map.of());
		}
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

	private static final class Totals {

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

		private void print(int deals, int totalPlayers, int totalTricks) {
			System.out.println("deals=" + deals + " (each played twice, seats mirrored)"
					+ " players=" + totalPlayers + " tricks=" + totalTricks);

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
