package com.lafi.cardgame.nazdarbaby.mcts;

import com.lafi.cardgame.nazdarbaby.mcts.BotArena.Bot;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Builds arena bots from the engine as it was at a git ref: its {@code mcts} sources are moved to
 * a {@code baseline} subpackage and compiled, together with {@link MctsArenaBot}, into a child
 * class loader. Cards, points and {@link Bot} still come from the current classpath, so both
 * seats share them and only the engine differs.
 */
final class BaselineBots {

	static final String BASELINE_PACKAGE = "com.lafi.cardgame.nazdarbaby.mcts.baseline";

	private static final String ENGINE_SOURCES = "src/main/java/com/lafi/cardgame/nazdarbaby/mcts/";
	private static final Path ADAPTER_SOURCE = Path.of("src/test/java/com/lafi/cardgame/nazdarbaby/mcts/MctsArenaBot.java");
	private static final Pattern PACKAGE_DECLARATION =
			Pattern.compile("^package com\\.lafi\\.cardgame\\.nazdarbaby\\.mcts;$", Pattern.MULTILINE);

	private BaselineBots() {
	}

	record Baseline(String commit, Function<String, Bot> botFactory) {
	}

	static Baseline load(String gitRef) throws IOException, InterruptedException {
		String commit = git("rev-parse", "--short", "--verify", "--quiet", gitRef + "^{commit}");
		if (commit.isEmpty()) {
			throw new IllegalArgumentException("Unknown git ref " + gitRef + " (the arena must run inside the repository)");
		}

		List<String> engineFiles = git("ls-tree", "--name-only", commit, ENGINE_SOURCES).lines()
				.filter(path -> path.endsWith(".java"))
				.toList();
		if (engineFiles.isEmpty()) {
			throw new IllegalArgumentException("No engine sources under " + ENGINE_SOURCES + " at " + gitRef);
		}

		Path workDir = Files.createTempDirectory("bot-arena-baseline");
		// The baseline's classes load lazily, so the directory can only go once the JVM is done with it
		Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(workDir)));
		Path sourceDir = Files.createDirectories(workDir.resolve("src"));
		Path classDir = Files.createDirectories(workDir.resolve("classes"));

		List<Path> sources = new ArrayList<>();
		for (String engineFile : engineFiles) {
			sources.add(write(sourceDir, engineFile, git("show", commit + ":" + engineFile)));
		}
		sources.add(write(sourceDir, ADAPTER_SOURCE.toString(), Files.readString(ADAPTER_SOURCE)));

		compile(sources, classDir, gitRef);

		URLClassLoader classLoader = new URLClassLoader(new URL[]{classDir.toUri().toURL()}, BaselineBots.class.getClassLoader());
		try {
			Constructor<? extends Bot> constructor = classLoader.loadClass(BASELINE_PACKAGE + ".MctsArenaBot")
					.asSubclass(Bot.class)
					.getConstructor(String.class);
			return new Baseline(commit, name -> newBot(constructor, name));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Compiled baseline has no MctsArenaBot(String) constructor", e);
		}
	}

	static String moveToBaselinePackage(String source) {
		Matcher matcher = PACKAGE_DECLARATION.matcher(source);
		if (!matcher.find()) {
			throw new IllegalArgumentException("Source is not in the com.lafi.cardgame.nazdarbaby.mcts package");
		}
		return matcher.replaceFirst("package " + BASELINE_PACKAGE + ";");
	}

	private static Path write(Path sourceDir, String originalPath, String source) throws IOException {
		Path file = sourceDir.resolve(Path.of(originalPath).getFileName());
		return Files.writeString(file, moveToBaselinePackage(source));
	}

	private static void compile(List<Path> sources, Path classDir, String gitRef) throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException("Comparing against a baseline needs a JDK, not a JRE");
		}

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
			List<String> options = List.of("-d", classDir.toString(), "-classpath", System.getProperty("java.class.path"),
					"-proc:none", "-nowarn");
			boolean compiled = compiler.getTask(null, fileManager, diagnostics, options, null,
					fileManager.getJavaFileObjectsFromPaths(sources)).call();
			if (!compiled) {
				String errors = diagnostics.getDiagnostics().stream()
						.filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
						.limit(5)
						.map(Object::toString)
						.collect(Collectors.joining("\n"));
				throw new IllegalStateException("The engine at " + gitRef + " does not compile with today's MctsArenaBot,"
						+ " cards and points (older engines had a different API):\n" + errors);
			}
		}
	}

	private static void deleteRecursively(Path directory) {
		try (Stream<Path> paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		} catch (IOException e) {
			System.err.println("Could not delete " + directory + ": " + e.getMessage());
		}
	}

	private static Bot newBot(Constructor<? extends Bot> constructor, String name) {
		try {
			return constructor.newInstance(name);
		} catch (InvocationTargetException e) {
			throw new IllegalStateException("Baseline bot failed to start", e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Baseline bot cannot be created", e);
		}
	}

	private static String git(String... arguments) throws IOException, InterruptedException {
		List<String> command = new ArrayList<>(List.of("git"));
		command.addAll(List.of(arguments));

		Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		process.waitFor();
		return output.strip();
	}
}
