package com.lafi.cardgame.nazdarbaby.counter;

import com.lafi.cardgame.nazdarbaby.broadcast.BroadcastListener;
import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.DurationFormatUtils;

public abstract class CountdownCounter implements Runnable {

	private static final String FORMATTED_COUNTDOWN_SPLITTER = " (";
	protected static final String FORMATTED_COUNTDOWN_REGEX_SPLITTER = FORMATTED_COUNTDOWN_SPLITTER.replaceAll("[\\W]", "\\\\$0");

	private final Broadcaster broadcaster;
	private final BroadcastListener listener;
	private final ScheduledExecutorService executorService;

	private long countdownInSeconds;

	protected CountdownCounter(long countdownInSeconds, Broadcaster broadcaster, BroadcastListener listener) {
		this.countdownInSeconds = countdownInSeconds;
		this.broadcaster = broadcaster;
		this.listener = listener;

		executorService = Executors.newSingleThreadScheduledExecutor();
	}

	@Override
	public void run() {
		eachRun();

		if (--countdownInSeconds < 0) {
			finalRun();
			shutdown();
		} else if (!broadcaster.isRegistered(listener)) {
			shutdown();
		}
	}

	public ExecutorService start() {
		broadcaster.register(listener);
		executorService.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);

		return executorService;
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

	protected void shutdown() {
		executorService.shutdown();
		shutdownCleaning();
	}

	protected abstract void eachRun();

	protected abstract void finalRun();

	protected abstract void shutdownCleaning();
}
