package com.lafi.cardgame.nazdarbaby.util;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.DurationFormatUtils;

public final class ExecutorServiceUtil {

	private ExecutorServiceUtil() {
	}

	public static ExecutorService runPerSecond(CountdownRunnable runnable) {
		ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
		scheduledExecutorService.scheduleAtFixedRate(runnable, 0, 1, TimeUnit.SECONDS);

		runnable.executorService = scheduledExecutorService;

		return scheduledExecutorService;
	}

	public static long getRemainingDurationInSeconds(int durationInMinutes) {
		return getRemainingDurationInSeconds(Instant.now(), durationInMinutes);
	}

	public static long getRemainingDurationInSeconds(Instant from, int durationInMinutes) {
		Duration duration = Duration.between(from, Instant.now());
		return TimeUnit.MINUTES.toSeconds(durationInMinutes) - duration.getSeconds();
	}

	public abstract static class CountdownRunnable implements Runnable {

		private static final String FORMATTED_COUNTDOWN_SPLITTER = " (";
		protected static final String FORMATTED_COUNTDOWN_REGEX_SPLITTER = FORMATTED_COUNTDOWN_SPLITTER.replaceAll("[\\W]", "\\\\$0");

		private long countdownInSeconds;
		private ExecutorService executorService;

		protected CountdownRunnable(long countdownInSeconds) {
			this.countdownInSeconds = countdownInSeconds;
		}

		@Override
		public void run() {
			everyRun();

			if (--countdownInSeconds < 0) {
				finalRun();
				executorService.shutdown();
			}
		}

		protected String getFormattedCountdown() {
			long countdownInMillis = TimeUnit.SECONDS.toMillis(countdownInSeconds);

			String format = FORMATTED_COUNTDOWN_SPLITTER;
			if (countdownInSeconds >= TimeUnit.MINUTES.toSeconds(1)) {
				format += "mm:s";
			}
			format += "s)";

			return DurationFormatUtils.formatDuration(countdownInMillis, format);
		}

		protected abstract void everyRun();

		protected abstract void finalRun();
	}
}
