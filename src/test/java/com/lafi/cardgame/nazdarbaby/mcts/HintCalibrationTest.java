package com.lafi.cardgame.nazdarbaby.mcts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.lafi.cardgame.nazdarbaby.card.TestCards.getCard;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.mcts.HintCalibration.CalibrationBot;
import com.lafi.cardgame.nazdarbaby.mcts.HintCalibration.Dump;
import com.lafi.cardgame.nazdarbaby.mcts.HintCalibration.HandTable;
import com.lafi.cardgame.nazdarbaby.mcts.HintCalibration.Prediction;
import com.lafi.cardgame.nazdarbaby.mcts.MctsEngine.LogOddsCalibration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class HintCalibrationTest {

	private static final String HEADER = "players,handSize,deal,secondOptionShare,secondOption,chance,matched";
	private static final String PROVENANCE = "commit=6da31cd deals=200 date=2026-10-16";

	@Nested
	class TablesTest {

		@Test
		void everyHandSizeTheGameDeals() {
			assertThat(HintCalibration.tables())
					.hasSize(10 + 10 + 10 + 8 + 7)
					.doesNotHaveDuplicates()
					.contains(new HandTable(3, 1), new HandTable(3, 10), new HandTable(6, 8), new HandTable(7, 7))
					.doesNotContain(new HandTable(3, 11), new HandTable(6, 9), new HandTable(7, 8));
		}
	}

	@Nested
	class FitTest {

		private static final LogOddsCalibration PER_CARD = new LogOddsCalibration(-0.1, 1.0, 0.06, -0.05);

		@Test
		void recoversTheCalibrationThatProducedTheOutcomes() {
			LogOddsCalibration fit =
					HintCalibration.fit(predictionsFrom(new LogOddsCalibration(0.3, 0.7), new Random(1), 8, 8), false);

			assertThat(fit.shift()).isCloseTo(0.3, within(0.05));
			assertThat(fit.scale()).isCloseTo(0.7, within(0.05));
		}

		@Test
		void calibratedChances_needNoCorrection() {
			LogOddsCalibration fit =
					HintCalibration.fit(predictionsFrom(new LogOddsCalibration(0, 1), new Random(2), 8, 8), false);

			assertThat(fit.shift()).isCloseTo(0, within(0.05));
			assertThat(fit.scale()).isCloseTo(1, within(0.05));
		}

		@Test
		void perCard_recoversTheHandSizeTerms() {
			LogOddsCalibration fit = HintCalibration.fit(predictionsFrom(PER_CARD, new Random(3), 1, 7), true);

			assertThat(fit.shift()).isCloseTo(-0.1, within(0.1));
			assertThat(fit.scale()).isCloseTo(1.0, within(0.1));
			assertThat(fit.shiftPerCard()).isCloseTo(0.06, within(0.02));
			assertThat(fit.scalePerCard()).isCloseTo(-0.05, within(0.02));
		}

		@Test
		void notPerCard_leavesTheHandSizeOut() {
			LogOddsCalibration fit = HintCalibration.fit(predictionsFrom(PER_CARD, new Random(3), 1, 7), false);

			assertThat(fit.shiftPerCard()).isZero();
			assertThat(fit.scalePerCard()).isZero();
		}

		@Test
		void perCard_withOneHandSize_fitsLikeWithout() {
			List<Prediction> predictions = predictionsFrom(new LogOddsCalibration(0.3, 0.7), new Random(1), 8, 8);

			assertThat(HintCalibration.fit(predictions, true)).isEqualTo(HintCalibration.fit(predictions, false));
		}

		@Test
		void perCard_withAnotherHandSizeOnlyAtCertainChances_fitsLikeWithout() {
			List<Prediction> predictions = new ArrayList<>(
					predictionsFrom(new LogOddsCalibration(0.3, 0.7), new Random(1), 8, 8));
			predictions.add(new Prediction(5, 3, 0, 0.0, false, 1, true));
			predictions.add(new Prediction(5, 3, 1, 0.0, false, 0, false));

			assertThat(HintCalibration.fit(predictions, true)).isEqualTo(HintCalibration.fit(predictions, false));
		}

		@Test
		void certainAndImpossibleChances_areLeftOut() {
			List<Prediction> predictions = predictionsFrom(PER_CARD, new Random(1), 1, 7);
			List<Prediction> withExtremes = new ArrayList<>(predictions);
			for (int i = 0; i < 1000; i++) {
				withExtremes.add(new Prediction(5, 1 + i % 7, i, 0.0, false, 1, false));
				withExtremes.add(new Prediction(5, 1 + i % 7, i, 0.0, false, 0, true));
			}

			assertThat(HintCalibration.fit(withExtremes, true)).isEqualTo(HintCalibration.fit(predictions, true));
		}

		private static List<Prediction> predictionsFrom(LogOddsCalibration truth, Random random, int minHandSize,
														int maxHandSize) {
			List<Prediction> predictions = new ArrayList<>();
			for (int i = 0; i < 40_000; i++) {
				int handSize = minHandSize + random.nextInt(maxHandSize - minHandSize + 1);
				double chance = 0.05 + 0.9 * random.nextDouble();
				boolean matched = random.nextDouble() < truth.apply(chance, handSize);
				predictions.add(new Prediction(5, handSize, i, 0.0, false, chance, matched));
			}
			return predictions;
		}
	}

	@Nested
	class EnsureWritableTest {

		@Test
		void existingFile_keepsItsContent(@TempDir Path directory) throws IOException {
			Path file = Files.writeString(directory.resolve("predictions.csv"), "earlier run");

			HintCalibration.ensureWritable(file);

			assertThat(file).hasContent("earlier run");
		}

		@Test
		void missingFile_isCreated(@TempDir Path directory) throws IOException {
			Path file = directory.resolve("predictions.csv");

			HintCalibration.ensureWritable(file);

			assertThat(file).isEmptyFile();
		}

		@Test
		void missingDirectory_isRejected(@TempDir Path directory) {
			assertThatThrownBy(() -> HintCalibration.ensureWritable(directory.resolve("missing").resolve("x.csv")))
					.isInstanceOf(NoSuchFileException.class);
		}

		@Test
		void directory_isRejected(@TempDir Path directory) {
			assertThatThrownBy(() -> HintCalibration.ensureWritable(directory)).isInstanceOf(IOException.class);
		}
	}

	@Nested
	class CsvTest {

		@Test
		void provenanceThenHeaderThenOneLinePerPrediction() {
			List<Prediction> predictions = List.of(new Prediction(3, 2, 7, 0.5, true, 0.25, false),
					new Prediction(7, 6, 0, 0.0, false, 1.0, true));

			assertThat(HintCalibration.csv(PROVENANCE, predictions)).containsExactly(
					"# " + PROVENANCE,
					HEADER,
					"3,2,7,0.5,true,0.25,false",
					"7,6,0,0.0,false,1.0,true");
		}
	}

	@Nested
	class ProvenanceTest {

		@Test
		void namesTheCommitTheDealsAndTheDate() {
			assertThat(HintCalibration.provenance("6da31cd-dirty", 200, LocalDate.of(2026, 10, 16)))
					.isEqualTo("commit=6da31cd-dirty deals=200 date=2026-10-16");
		}

		@Test
		void cleanCheckout_isItsCommit(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			String commit = commitEngine(directory, "first");

			assertThat(HintCalibration.commit(directory)).isEqualTo(commit);
		}

		@Test
		void changedEngine_isDirty(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			String commit = commitEngine(directory, "first");
			Files.writeString(directory.resolve(BaselineBots.ENGINE_SOURCES).resolve("MctsEngine.java"), "edited");

			assertThat(HintCalibration.commit(directory)).isEqualTo(commit + "-dirty");
		}

		@Test
		void newEngineFile_isDirty(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			String commit = commitEngine(directory, "first");
			Files.writeString(directory.resolve(BaselineBots.ENGINE_SOURCES).resolve("Determinizer.java"), "new");

			assertThat(HintCalibration.commit(directory)).isEqualTo(commit + "-dirty");
		}

		@Test
		void changedRules_areDirty(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			commitFile(directory, RULES, "first");
			String commit = commitEngine(directory, "first");
			writeFile(directory, RULES, "edited");

			assertThat(HintCalibration.commit(directory)).isEqualTo(commit + "-dirty");
		}

		@Test
		void changedAdapter_isDirty(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			commitFile(directory, BaselineBots.ADAPTER_SOURCE.toString(), "first");
			String commit = commitEngine(directory, "first");
			writeFile(directory, BaselineBots.ADAPTER_SOURCE.toString(), "edited");

			assertThat(HintCalibration.commit(directory)).isEqualTo(commit + "-dirty");
		}

		@Test
		void changedArena_isClean(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			commitFile(directory, ARENA, "first");
			String commit = commitEngine(directory, "first");
			writeFile(directory, ARENA, "edited");

			assertThat(HintCalibration.commit(directory)).isEqualTo(commit);
		}

		@Test
		void changesOutsideTheEngine_areClean(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			Path readme = Files.writeString(directory.resolve("README.md"), "first");
			String commit = commitEngine(directory, "first");
			Files.writeString(readme, "edited");
			Files.writeString(directory.resolve("notes.txt"), "new");

			assertThat(HintCalibration.commit(directory)).isEqualTo(commit);
		}

		@Test
		void repositoryWithoutCommits_isUnknown(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			BaselineBots.git("-C", directory.toString(), "init", "--quiet");

			assertThat(HintCalibration.commit(directory)).isEqualTo("unknown");
		}

		@Test
		void outsideARepository_isUnknown(@TempDir Path directory) throws InterruptedException {
			assertThat(HintCalibration.commit(directory)).isEqualTo("unknown");
		}
	}

	@Nested
	class EngineWarningTest {

		@Test
		void noProvenanceLine_cannotBeChecked(@TempDir Path directory) throws InterruptedException {
			assertThat(HintCalibration.engineWarning(null, directory))
					.isEqualTo("it records no commit, so it cannot be checked against this checkout");
		}

		@Test
		void unknownCommit_cannotBeChecked(@TempDir Path directory) throws InterruptedException {
			assertThat(HintCalibration.engineWarning("commit=unknown deals=200 date=2026-10-16", directory))
					.isEqualTo("it records no commit, so it cannot be checked against this checkout");
		}

		@Test
		void provenanceWithoutCommit_cannotBeChecked(@TempDir Path directory) throws InterruptedException {
			assertThat(HintCalibration.engineWarning("deals=200 date=2026-10-16", directory))
					.isEqualTo("it records no commit, so it cannot be checked against this checkout");
		}

		@Test
		void uncommittedChanges_cannotBeChecked(@TempDir Path directory) throws InterruptedException {
			assertThat(HintCalibration.engineWarning("commit=6da31cd-dirty deals=200 date=2026-10-16", directory))
					.isEqualTo("it was played on uncommitted changes to the engine or what it plays through, so it "
							+ "cannot be checked against this checkout");
		}

		@Test
		void sameEngine_noWarning(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			String commit = commitEngine(directory, "first");
			Files.writeString(directory.resolve("README.md"), "changed outside the engine");
			commitAll(directory);

			assertThat(HintCalibration.engineWarning(provenance(commit), directory)).isNull();
		}

		@Test
		void engineChangedSince_isWarned(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			String commit = commitEngine(directory, "first");
			commitEngine(directory, "second");

			assertThat(HintCalibration.engineWarning(provenance(commit), directory))
					.isEqualTo("the engine or what it plays through changed since " + commit);
		}

		@Test
		void rulesChangedSince_isWarned(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			String commit = commitFile(directory, RULES, "first");
			commitFile(directory, RULES, "second");

			assertThat(HintCalibration.engineWarning(provenance(commit), directory))
					.isEqualTo("the engine or what it plays through changed since " + commit);
		}

		@Test
		void arenaChangedSince_noWarning(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			String commit = commitFile(directory, ARENA, "first");
			commitFile(directory, ARENA, "second");

			assertThat(HintCalibration.engineWarning(provenance(commit), directory)).isNull();
		}

		@Test
		void engineChangedInTheWorkingTree_isWarned(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			String commit = commitEngine(directory, "first");
			Files.writeString(directory.resolve(BaselineBots.ENGINE_SOURCES).resolve("MctsEngine.java"), "edited");

			assertThat(HintCalibration.engineWarning(provenance(commit), directory))
					.isEqualTo("the engine or what it plays through changed since " + commit);
		}

		@Test
		void commitNotInTheRepository_cannotBeChecked(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());
			commitEngine(directory, "first");

			assertThat(HintCalibration.engineWarning(provenance("deadbee"), directory))
					.isEqualTo("deadbee is not in this repository, so it cannot be checked against this checkout");
		}

		@Test
		void outsideARepository_cannotBeChecked(@TempDir Path directory) throws Exception {
			assumeTrue(gitWorks());

			assertThat(HintCalibration.engineWarning(provenance("6da31cd"), directory))
					.isEqualTo("6da31cd is not in this repository, so it cannot be checked against this checkout");
		}

		private static String provenance(String commit) {
			return "commit=" + commit + " deals=200 date=2026-10-16";
		}
	}

	private static boolean gitWorks() throws InterruptedException {
		try {
			return !BaselineBots.git("--version").isEmpty();
		} catch (IOException e) {
			return false;
		}
	}

	private static final String RULES = "src/main/java/com/lafi/cardgame/nazdarbaby/card/Card.java";
	private static final String ARENA = "src/test/java/com/lafi/cardgame/nazdarbaby/mcts/BotArena.java";

	private static String commitEngine(Path directory, String content) throws Exception {
		return commitFile(directory, BaselineBots.ENGINE_SOURCES + "MctsEngine.java", content);
	}

	private static String commitFile(Path directory, String file, String content) throws Exception {
		writeFile(directory, file, content);
		return commitAll(directory);
	}

	private static void writeFile(Path directory, String file, String content) throws IOException {
		Path path = directory.resolve(file);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content);
	}

	/**
	 * Commits every file in the directory, making it a repository first if needed, and returns the short commit.
	 */
	private static String commitAll(Path directory) throws Exception {
		String path = directory.toString();
		BaselineBots.git("-C", path, "init", "--quiet");
		BaselineBots.git("-C", path, "add", "--all");
		BaselineBots.git("-C", path, "-c", "user.name=test", "-c", "user.email=test@example.com",
				"-c", "commit.gpgsign=false", "commit", "--quiet", "-m", "change");
		return BaselineBots.git("-C", path, "rev-parse", "--short", "HEAD");
	}

	@Nested
	class PartialTest {

		@Test
		void sitsNextToTheDump() {
			assertThat(HintCalibration.partial(Path.of("runs", "run.csv")))
					.isEqualTo(Path.of("runs", "run.csv.partial"));
		}

		@Test
		void append_addsEachDealAfterWhatIsThere(@TempDir Path directory) throws IOException {
			Path file = Files.write(directory.resolve("run.csv.partial"), List.of("# " + PROVENANCE, HEADER));

			HintCalibration.append(file, List.of(new Prediction(3, 2, 7, 0.5, true, 0.25, false)));
			HintCalibration.append(file, List.of(new Prediction(7, 6, 0, 0.0, false, 1.0, true)));

			assertThat(Files.readAllLines(file))
					.containsExactly("# " + PROVENANCE, HEADER, "3,2,7,0.5,true,0.25,false",
							"7,6,0,0.0,false,1.0,true");
		}

		@Test
		void append_toAFileThatCannotBeWritten_fails(@TempDir Path directory) {
			Path missing = directory.resolve("missing").resolve("run.csv.partial");

			assertThatThrownBy(() -> HintCalibration.append(missing, List.of()))
					.isInstanceOf(UncheckedIOException.class)
					.hasCauseInstanceOf(NoSuchFileException.class);
		}

		@Test
		void play_handsOverEachDealAsItIsPlayed() throws Exception {
			List<List<Prediction>> played = Collections.synchronizedList(new ArrayList<>());

			List<Prediction> predictions =
					HintCalibration.play(List.of(new HandTable(3, 1), new HandTable(7, 1)), 2, 2, played::add);

			assertThat(played).hasSize(2 * 2 * 2).allSatisfy(deal -> assertThat(deal)
					.hasSize(deal.getFirst().players())
					.extracting(Prediction::players, Prediction::deal, Prediction::secondOptionShare)
					.containsOnly(tuple(deal.getFirst().players(), deal.getFirst().deal(),
							deal.getFirst().secondOptionShare())));
			assertThat(played.stream().flatMap(List::stream).toList()).containsExactlyInAnyOrderElementsOf(predictions);
		}
	}

	@Nested
	class ReadCsvTest {

		@Test
		void readsWhatTheDumpWrites() {
			List<Prediction> predictions = List.of(new Prediction(3, 2, 7, 0.5, true, 0.25, false),
					new Prediction(7, 6, 0, 0.0, false, 1.0, true));

			assertThat(HintCalibration.fromCsv(HintCalibration.csv(PROVENANCE, predictions), "run.csv"))
					.isEqualTo(new Dump("run.csv", PROVENANCE, predictions, false));
		}

		@Test
		void withoutProvenance_isStillRead() {
			assertThat(HintCalibration.fromCsv(List.of(HEADER, "3,1,0,0.0,false,0.5,true"), "old.csv"))
					.isEqualTo(new Dump("old.csv", null, List.of(new Prediction(3, 1, 0, 0.0, false, 0.5, true)),
							false));
		}

		@Test
		void severalFiles_areReadInOrder(@TempDir Path directory) throws IOException {
			Prediction first = new Prediction(3, 2, 7, 0.5, true, 0.25, false);
			Prediction second = new Prediction(7, 6, 0, 0.0, false, 1.0, true);
			Path a = Files.write(directory.resolve("a.csv"), HintCalibration.csv(PROVENANCE, List.of(first)));
			Path b = Files.write(directory.resolve("b.csv"), HintCalibration.csv(PROVENANCE, List.of(second)));

			assertThat(HintCalibration.readCsv(a + "," + b)).containsExactly(
					new Dump(a.toString(), PROVENANCE, List.of(first), false),
					new Dump(b.toString(), PROVENANCE, List.of(second), false));
		}

		@Test
		void unfinishedRun_cutLastLine_isSkipped() {
			List<String> lines = List.of("# " + PROVENANCE + " incomplete", HEADER, "3,1,0,0.0,false,0.5,true",
					"3,1,1,0.0,fal");

			assertThat(HintCalibration.fromCsv(lines, "run.csv.partial")).isEqualTo(new Dump("run.csv.partial",
					PROVENANCE + " incomplete", List.of(new Prediction(3, 1, 0, 0.0, false, 0.5, true)), true));
		}

		@Test
		void unfinishedRun_badLineBeforeTheLast_isRejected() {
			List<String> lines = List.of("# " + PROVENANCE + " incomplete", HEADER, "3,1,0,0.0,fal",
					"3,1,1,0.0,false,0.5,true");

			assertThatThrownBy(() -> HintCalibration.fromCsv(lines, "run.csv.partial"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("run.csv.partial line 3: expected 7 fields, got 5");
		}

		@Test
		void finishedRun_cutLastLine_isRejected() {
			List<String> lines = List.of("# " + PROVENANCE, HEADER, "3,1,0,0.0,false,0.5,true", "3,1,1,0.0,fal");

			assertThatThrownBy(() -> HintCalibration.fromCsv(lines, "run.csv"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("run.csv line 4: expected 7 fields, got 5");
		}

		@Test
		void provenanceWithoutHeader_isRejected() {
			assertThatThrownBy(() -> HintCalibration.fromCsv(List.of("# " + PROVENANCE, "3,1,0,0.0,false,0.5,true"),
					"run.csv"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("run.csv: expected the header " + HEADER);
		}

		@Test
		void provenanceOnly_isRejected() {
			assertThatThrownBy(() -> HintCalibration.fromCsv(List.of("# " + PROVENANCE), "run.csv"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("run.csv: expected the header " + HEADER);
		}

		@Test
		void lineNumbers_countTheProvenanceLine() {
			List<String> lines = List.of("# " + PROVENANCE, HEADER, "3,1,0,0.0,false,0.5,true", "3,1,0,0.0,false,0.5");

			assertThatThrownBy(() -> HintCalibration.fromCsv(lines, "run.csv"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("run.csv line 4: expected 7 fields, got 6");
		}

		@Test
		void noHeader_isRejected() {
			assertThatThrownBy(() -> HintCalibration.fromCsv(List.of("3,1,0,false,0.5,true"), "old.csv"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("old.csv: expected the header " + HEADER);
		}

		@Test
		void emptyFile_isRejected() {
			assertThatThrownBy(() -> HintCalibration.fromCsv(List.of(), "empty.csv"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("empty.csv: expected the header " + HEADER);
		}

		@Test
		void missingField_isRejectedWithItsLine() {
			assertRejected("3,1,0,0.0,false,0.5", "expected 7 fields, got 6");
		}

		@Test
		void notANumber_isRejectedWithItsLine() {
			assertRejected("three,1,0,0.0,false,0.5,true", "For input string: \"three\"");
		}

		@Test
		void notABoolean_isRejectedWithItsLine() {
			assertRejected("3,1,0,0.0,false,0.5,yes", "expected true or false, got yes");
		}

		@Test
		void playersOutsideTheGame_areRejected() {
			assertRejected("2,1,0,0.0,false,0.5,true", "players must be 3 to 7, got 2");
			assertRejected("8,1,0,0.0,false,0.5,true", "players must be 3 to 7, got 8");
		}

		@Test
		void noCards_isRejected() {
			assertRejected("3,0,0,0.0,false,0.5,true", "hand size must be at least 1, got 0");
		}

		@Test
		void negativeDeal_isRejected() {
			assertRejected("3,1,-1,0.0,false,0.5,true", "deal must not be negative, got -1");
		}

		@Test
		void chanceOutsideZeroToOne_isRejected() {
			assertRejected("3,1,0,0.0,false,-0.1,true", "chance must be 0 to 1, got -0.1");
			assertRejected("3,1,0,0.0,false,1.5,true", "chance must be 0 to 1, got 1.5");
		}

		private static void assertRejected(String line, String reason) {
			List<String> lines = List.of(HEADER, "3,1,0,0.0,false,0.5,true", line);

			assertThatThrownBy(() -> HintCalibration.fromCsv(lines, "run.csv"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("run.csv line 3: " + reason);
		}
	}

	@Nested
	class ScoreTest {

		@Test
		void matchedAsShown_scoresZero() {
			List<Prediction> predictions =
					List.of(new Prediction(3, 8, 0, 0.0, false, 0.5, true),
							new Prediction(3, 8, 1, 0.0, false, 0.5, false));

			assertThat(HintCalibration.score(predictions, Prediction::chance)).isEqualTo(" 0.0 /  0.0");
		}

		@Test
		void gapsOfOppositeSign_cancelInTheMeanButNotInTheCalibrationError() {
			List<Prediction> predictions =
					List.of(new Prediction(3, 8, 0, 0.0, false, 0.1, true),
							new Prediction(3, 8, 1, 0.0, false, 0.9, false));

			assertThat(HintCalibration.score(predictions, Prediction::chance)).isEqualTo(" 0.0 / 90.0");
		}
	}

	@Nested
	class ReportTest {

		@Test
		void showsEveryTableSizeItHasPredictionsFor_andTheRefitToPaste() throws Exception {
			List<Prediction> predictions =
					HintCalibration.play(List.of(new HandTable(3, 1), new HandTable(7, 1)), 2, 2);

			String report = HintCalibration.report(predictions);

			assertThat(predictions).hasSize(2 * 2 * 3 + 2 * 2 * 7);
			assertThat(predictions).allMatch(prediction -> prediction.chance() >= 0 && prediction.chance() <= 1);
			// About 16 seats with two allowed predictions pick the second half the time: none does once in 65,000 runs
			assertThat(predictions).anyMatch(Prediction::secondOption);
			assertThat(predictions).filteredOn(prediction -> prediction.secondOptionShare() == 0.0)
					.hasSize(2 * 3 + 2 * 7)
					.noneMatch(Prediction::secondOption);
			assertThat(predictions).filteredOn(prediction -> prediction.secondOptionShare() == 0.5)
					.hasSize(2 * 3 + 2 * 7);
			assertThat(report)
					.contains("SMALL_HAND_HINT_CALIBRATION_BY_PLAYERS", "3 players", "7 players",
							"3, new LogOddsCalibration(", "7, new LogOddsCalibration(")
					.doesNotContain("LARGE_HAND_HINT_CALIBRATION_BY_PLAYERS", "4 players", "5 players", "6 players");
		}

		@Test
		void handsWithinAndAboveTheFirstCardLimit_areFittedApart() {
			List<Prediction> predictions = new ArrayList<>(evenDealsMatchedAsShownOddOnesHalfTheTime(1));
			predictions.addAll(matchedAsShown(8));

			String report = HintCalibration.report(predictions);

			assertThat(smallHands(report))
					.containsPattern("3, new LogOddsCalibration\\(-?0\\.000, 0\\.447, 0\\.000, 0\\.000\\),");
			assertThat(largeHands(report)).containsPattern("3, new LogOddsCalibration\\(-?0\\.000, 1\\.000\\),")
					.endsWith("Then update the values MctsEngineTest.HintChanceTest and BotSimulatorTest expect.\n");
		}

		@Test
		void refit_isScoredOnDealsItWasNotFittedOn() {
			assertThat(HintCalibration.report(evenDealsMatchedAsShownOddOnesHalfTheTime(1)))
					.containsPattern("3 players, first  option, {4}20 predictions: {2}uncorrected  0\\.0 / 30\\.0   "
							+ "current .{11}   refit  0\\.0 / 30\\.0");
		}

		@Test
		void firstAndSecondOptions_areScoredApart() {
			List<Prediction> predictions = new ArrayList<>();
			for (int deal = 0; deal < 20; deal++) {
				predictions.add(new Prediction(3, 1, deal, 0.0, false, 0.8, deal % 10 != 9));
				predictions.add(new Prediction(3, 1, deal, 0.5, true, 0.2, deal % 4 == 1));
			}

			// On odd deals the first option is matched 8 times in 10 as shown, the second 5 times instead of 2
			assertThat(HintCalibration.report(predictions))
					.contains("3 players, first  option,    10 predictions:  uncorrected  0.0 /  0.0",
							"3 players, second option,    10 predictions:  uncorrected 30.0 / 30.0");
		}

		@Test
		void refitToPaste_isFittedOnAllDeals() {
			// Over all deals a shown 20% is matched 35% of the time: logit(0.35) / logit(0.2) = 0.447
			assertThat(HintCalibration.report(evenDealsMatchedAsShownOddOnesHalfTheTime(1)))
					.containsPattern("3, new LogOddsCalibration\\(-?0\\.000, 0\\.447, 0\\.000, 0\\.000\\),");
		}

		@Test
		void handsWithinTheFirstCardLimit_pasteTermsPerCard_largerOnesDoNot() {
			List<Prediction> predictions = new ArrayList<>(matchedAsShown(1));
			predictions.addAll(matchedFlatterThanShown(2));
			predictions.addAll(matchedAsShown(8));
			predictions.addAll(matchedFlatterThanShown(9));

			String report = HintCalibration.report(predictions);

			// Scale 1 with 1 card and logit(0.6) / logit(0.7) = 0.479 with 2: 1.521 - 0.521 per card. Even and odd
			// deals match alike, so the held-out fit scores no gap; without per-card terms it would.
			assertThat(smallHands(report))
					.containsPattern("3, new LogOddsCalibration\\(-?0\\.000, 1\\.521, -?0\\.000, -0\\.521\\),")
					.contains("refit  0.0 /  0.0");
			assertThat(largeHands(report)).containsPattern("3, new LogOddsCalibration\\(-?0\\.000, 0\\.\\d{3}\\),");
		}

		@Test
		void worstTables_areListedFurthestFirst() {
			List<Prediction> predictions = new ArrayList<>();
			for (int i = 0; i < 100; i++) {
				// A simulated 50% is shown as 47.7% with 3 players and 54.3% with 4
				predictions.add(new Prediction(3, 8, i, 0.0, false, 0.5, true));
				predictions.add(new Prediction(3, 9, i, 0.0, false, 0.5, false));
				predictions.add(new Prediction(3, 10, i, 0.0, false, 1.0, true));
				predictions.add(new Prediction(4, 8, i, 0.0, false, 0.5, i < 60));
			}

			// 52.3 points over sqrt(100 * 0.477 * 0.523) = 5.0 points of binomial noise is 10.5 standard errors
			assertThat(worstTables(largeHands(HintCalibration.report(predictions)))).containsExactly(
					"  3 players,  8-card hands, first  option,   100 predictions:  +52.3 (10.5)",
					"  3 players,  9-card hands, first  option,   100 predictions:  -47.7 (9.6)",
					"  4 players,  8-card hands, first  option,   100 predictions:   +5.7 (1.1)");
		}

		@Test
		void worstTables_showFive() {
			List<Prediction> predictions = new ArrayList<>();
			for (int handSize = 1; handSize <= 7; handSize++) {
				for (int i = 0; i < 10; i++) {
					predictions.add(new Prediction(3, handSize, i, 0.0, false, 0.5, i < handSize));
				}
			}

			assertThat(worstTables(HintCalibration.report(predictions))).hasSize(5);
		}

		@Test
		void scored_areScoredOnThoseFitOnAll() {
			List<Prediction> scored = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				scored.add(new Prediction(3, 1, i, 0.0, false, 0.2, i < 5));
				scored.add(new Prediction(3, 1, i, 0.0, false, 0.8, i < 5));
			}
			List<Prediction> predictions = new ArrayList<>(matchedAsShown(1));
			predictions.add(new Prediction(4, 1, 0, 0.0, false, 0.5, true));

			// Fitted on chances matched as shown, scored where 20% and 80% are both matched half the time
			assertThat(HintCalibration.report(predictions))
					.contains("Worst tables with the current calibration", "Refit on all deals, to paste:")
					.doesNotContain("--from");
			assertThat(HintCalibration.report(predictions, scored))
					.contains("Held-out files (fit on --from, scored on --score)",
							"Worst tables of the --from files with the current calibration",
							"Refit on all deals of the --from files, to paste:")
					.containsPattern("3 players, first  option, {4}20 predictions: {2}uncorrected  0\\.0 / 30\\.0   "
							+ "current .{11}   refit  0\\.0 / 30\\.0")
					.containsPattern("3, new LogOddsCalibration\\(-?0\\.000, 1\\.000, 0\\.000, 0\\.000\\),")
					.contains("4, new LogOddsCalibration(")
					.doesNotContain("4 players, first  option");
		}

		private static List<String> worstTables(String section) {
			String worst = section.substring(section.indexOf("Worst tables"), section.indexOf("Refit on all deals"));
			return worst.lines().skip(1).toList();
		}

		private static String smallHands(String report) {
			return report.substring(report.indexOf("SMALL_HAND"), report.indexOf("LARGE_HAND"));
		}

		private static String largeHands(String report) {
			return report.substring(report.indexOf("LARGE_HAND"));
		}

		private static List<Prediction> evenDealsMatchedAsShownOddOnesHalfTheTime(int handSize) {
			List<Prediction> predictions = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				predictions.add(new Prediction(3, handSize, 2 * i, 0.0, false, 0.2, i < 2));
				predictions.add(new Prediction(3, handSize, 2 * i, 0.0, false, 0.8, i < 8));
				predictions.add(new Prediction(3, handSize, 2 * i + 1, 0.0, false, 0.2, i < 5));
				predictions.add(new Prediction(3, handSize, 2 * i + 1, 0.0, false, 0.8, i < 5));
			}
			return predictions;
		}

		private static List<Prediction> matchedFlatterThanShown(int handSize) {
			List<Prediction> predictions = new ArrayList<>();
			for (int i = 0; i < 40; i++) {
				predictions.add(new Prediction(3, handSize, i, 0.0, false, 0.3, i / 2 < 8));
				predictions.add(new Prediction(3, handSize, i, 0.0, false, 0.7, i / 2 < 12));
			}
			return predictions;
		}

		private static List<Prediction> matchedAsShown(int handSize) {
			List<Prediction> predictions = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				predictions.add(new Prediction(3, handSize, i, 0.0, false, 0.2, i < 2));
				predictions.add(new Prediction(3, handSize, i, 0.0, false, 0.8, i < 8));
			}
			return predictions;
		}
	}

	@Nested
	class CalibrationBotTest {

		private static final List<Card> ACE_OF_HEARTS = List.of(getCard(14, Color.HEARTS));

		@Test
		void noSecondOptionShare_predictsTheLikeliest() {
			CalibrationBot bot = new CalibrationBot(0);

			assertThat(predictFirst(bot)).isEqualTo(1);
			assertThat(bot.shownSecondOption).isFalse();
			assertThat(bot.shownChance).isEqualTo(1);
		}

		@Test
		void fullSecondOptionShare_predictsTheSecondLikeliest() {
			CalibrationBot bot = new CalibrationBot(1);

			assertThat(predictFirst(bot)).isZero();
			assertThat(bot.shownSecondOption).isTrue();
			assertThat(bot.shownChance).isZero();
		}

		@Test
		void onlyOneAllowedPrediction_isPredictedWhateverTheShare() {
			CalibrationBot bot = new CalibrationBot(1);

			// The others predicted 1 trick of 1 between them, so the last seat must not predict 0
			assertThat(bot.predict(ACE_OF_HEARTS, 2, 3, 1, new Integer[]{1, 0, null}, unknownCards(), Map.of()))
					.isEqualTo(1);
			assertThat(bot.shownSecondOption).isFalse();
			assertThat(bot.shownChance).isEqualTo(1);
		}

		@Test
		void finished_remembersWhetherThePredictionWasMatched() {
			CalibrationBot matched = new CalibrationBot(0);
			CalibrationBot missed = new CalibrationBot(0);

			matched.finished(true);
			missed.finished(false);

			assertThat(matched.matched).isTrue();
			assertThat(missed.matched).isFalse();
		}

		private static double predictFirst(CalibrationBot bot) {
			return bot.predict(ACE_OF_HEARTS, 0, 3, 1, new Integer[3], unknownCards(), Map.of());
		}

		private static List<Card> unknownCards() {
			List<Card> unknownCards = new CardProvider(3).getShuffledDeckOfCards();
			unknownCards.removeAll(ACE_OF_HEARTS);
			return unknownCards;
		}
	}

	@Nested
	class ArgumentsTest {

		private static final String UNKNOWN_COMMIT = "commit=unknown deals=1 date=2026-10-16";
		private static final String NO_COMMIT_WARNING =
				"  warning: it records no commit, so it cannot be checked against this checkout\n";
		private static final List<Prediction> ONE_CARD =
				List.of(new Prediction(3, 1, 0, 0.0, false, 0.2, true), new Prediction(3, 1, 1, 0.0, false, 0.8, true));

		@Test
		void notANumber_isRejectedWithItsName() {
			assertThatThrownBy(() -> HintCalibration.main(new String[]{"many"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("deals per table must be a number, got many");
		}

		@Test
		void noDeals_isRejected() {
			assertThatThrownBy(() -> HintCalibration.main(new String[]{"0"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("deals per table must be at least 1, got 0");
		}

		@Test
		void thirdArgument_isRejected() {
			assertThatThrownBy(() -> HintCalibration.main(new String[]{"10", "2", "9"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("Expected <deals per table> <threads> [--dump <file>] or --from <file>[,<file>...] "
							+ "[--score <file>[,<file>...]], got 10 2 9");
		}

		@Test
		void dump_isTakenOutBeforeTheNumbers(@TempDir Path directory) {
			String file = directory.resolve("predictions.csv").toString();

			assertThatThrownBy(() -> HintCalibration.main(new String[]{"--dump", file, "0"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("deals per table must be at least 1, got 0");
		}

		@Test
		void dumpWithoutFile_isRejected() {
			assertThatThrownBy(() -> HintCalibration.main(new String[]{"10", "2", "--dump"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("--dump needs a file");
		}

		@Test
		void repeatedDump_isRejected(@TempDir Path directory) {
			String file = directory.resolve("predictions.csv").toString();

			assertThatThrownBy(() -> HintCalibration.main(new String[]{"10", "2", "--dump", file, "--dump", file}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("--dump given twice");
		}

		@Test
		@Timeout(5)
		void dumpIntoMissingDirectory_isRejectedBeforePlaying(@TempDir Path directory) {
			String file = directory.resolve("missing").resolve("predictions.csv").toString();

			assertThatThrownBy(() -> HintCalibration.main(new String[]{"10", "2", "--dump", file}))
					.isInstanceOf(NoSuchFileException.class);
		}

		@Test
		@Timeout(5)
		void from_reportsWithoutPlaying(@TempDir Path directory) throws Exception {
			Path file =
					Files.write(directory.resolve("predictions.csv"), HintCalibration.csv(UNKNOWN_COMMIT, ONE_CARD));

			assertThat(mainOutput("--from", file.toString()))
					.startsWith("Fitted on " + file + ": " + UNKNOWN_COMMIT + "\n" + NO_COMMIT_WARNING + "\n")
					.contains("Held-out deals (fit on even deals, scored on odd)", "3 players, first  option");
		}

		@Test
		@Timeout(5)
		void fromWithScore_scoresOnTheScoreFiles(@TempDir Path directory) throws Exception {
			Path fitted = Files.write(directory.resolve("a.csv"), HintCalibration.csv(UNKNOWN_COMMIT, ONE_CARD));
			List<String> withoutProvenance = HintCalibration.csv(UNKNOWN_COMMIT, ONE_CARD).subList(1, 4);
			Path scored = Files.write(directory.resolve("b.csv"), withoutProvenance);

			assertThat(mainOutput("--from", fitted.toString(), "--score", scored.toString()))
					.startsWith("Fitted on " + fitted + ": " + UNKNOWN_COMMIT + "\n" + NO_COMMIT_WARNING
							+ "Scored on " + scored + ": no provenance line\n" + NO_COMMIT_WARNING + "\n")
					.contains("Held-out files (fit on --from, scored on --score)");
		}

		@Test
		@Timeout(5)
		void fromAnUnfinishedRun_isWarned(@TempDir Path directory) throws Exception {
			Path file = Files.write(directory.resolve("run.csv.partial"),
					HintCalibration.csv(UNKNOWN_COMMIT + " incomplete", ONE_CARD));

			assertThat(mainOutput("--from", file.toString())).startsWith("Fitted on " + file + ": " + UNKNOWN_COMMIT
					+ " incomplete\n  warning: its run did not finish, so it lacks some deals\n" + NO_COMMIT_WARNING);
		}

		@Test
		@Timeout(5)
		void fromAnUnfinishedRunWithACutLastLine_isWarned(@TempDir Path directory) throws Exception {
			List<String> lines = new ArrayList<>(HintCalibration.csv(UNKNOWN_COMMIT + " incomplete", ONE_CARD));
			lines.add("3,1,2,0.0,fal");
			Path file = Files.write(directory.resolve("run.csv.partial"), lines);

			assertThat(mainOutput("--from", file.toString())).startsWith("Fitted on " + file + ": " + UNKNOWN_COMMIT
					+ " incomplete\n  warning: its run did not finish, so it lacks some deals\n"
					+ "  warning: its last line was cut off when the run stopped, and is skipped\n"
					+ NO_COMMIT_WARNING);
		}

		@Test
		@Timeout(5)
		void dumpWithAPartialFileLeft_isRejectedBeforePlaying(@TempDir Path directory) throws Exception {
			Path dump = directory.resolve("run.csv");
			Path partial = Files.write(directory.resolve("run.csv.partial"), List.of("earlier deals"));

			assertThatThrownBy(() -> HintCalibration.main(new String[]{"10", "2", "--dump", dump.toString()}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage(partial + " is left from a run that did not finish: report on it with --from, or move "
							+ "it away first");
			assertThat(partial).hasContent("earlier deals");
			assertThat(dump).doesNotExist();
		}

		@Test
		@Timeout(5)
		void dumpIntoADirectory_leavesNoPartialFile(@TempDir Path directory) {
			Path dump = directory.resolve("run.csv");

			assertThatThrownBy(() -> HintCalibration.main(new String[]{"10", "2", "--dump", directory.toString()}))
					.isInstanceOf(IOException.class);
			assertThat(HintCalibration.partial(directory)).doesNotExist();
			assertThat(dump).doesNotExist();
		}

		@Test
		@Timeout(5)
		void scoreWithoutFrom_isRejected() {
			assertThatThrownBy(() -> HintCalibration.main(new String[]{"10", "2", "--score", "b.csv"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("--score needs --from");
		}

		private static String mainOutput(String... args) throws Exception {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			PrintStream console = System.out;
			System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
			try {
				HintCalibration.main(args);
			} finally {
				System.setOut(console);
			}
			return output.toString(StandardCharsets.UTF_8);
		}

		@Test
		void fromWithNumbers_isRejected() {
			assertThatThrownBy(() -> HintCalibration.main(new String[]{"--from", "a.csv", "10"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("--from takes no other arguments than --score, got --from a.csv 10");
		}

		@Test
		void fromWithDump_isRejected() {
			assertThatThrownBy(() -> HintCalibration.main(new String[]{"--from", "a.csv", "--dump", "b.csv"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("--from takes no other arguments than --score, got --from a.csv --dump b.csv");
		}

		@Test
		void fromMissingFile_isRejected(@TempDir Path directory) {
			String file = directory.resolve("missing.csv").toString();

			assertThatThrownBy(() -> HintCalibration.main(new String[]{"--from", file}))
					.isInstanceOf(NoSuchFileException.class);
		}

		@Test
		void noThreads_isRejected() {
			assertThatThrownBy(() -> HintCalibration.main(new String[]{"10", "0"}))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("threads must be at least 1, got 0");
		}
	}
}
