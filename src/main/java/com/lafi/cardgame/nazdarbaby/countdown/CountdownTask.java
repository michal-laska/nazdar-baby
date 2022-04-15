package com.lafi.cardgame.nazdarbaby.countdown;

import com.lafi.cardgame.nazdarbaby.broadcast.BroadcastListener;
import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.DurationFormatUtils;

public abstract class CountdownTask {

	private static final String FORMATTED_COUNTDOWN_SPLITTER = " (";
	protected static final String FORMATTED_COUNTDOWN_REGEX_SPLITTER = FORMATTED_COUNTDOWN_SPLITTER.replaceAll("\\W", "\\\\$0");

	private final Broadcaster broadcaster;
	private final BroadcastListener listener;
	private final boolean reusePreviousCountdownTime;

	private long countdownInSeconds;

	protected CountdownTask(long countdownInSeconds, Broadcaster broadcaster, BroadcastListener listener) {
		this(countdownInSeconds, broadcaster, listener, false);
	}

	protected CountdownTask(long countdownInSeconds, Broadcaster broadcaster, BroadcastListener listener, boolean reusePreviousCountdownTime) {
		this.countdownInSeconds = countdownInSeconds;
		this.broadcaster = broadcaster;
		this.listener = listener;
		this.reusePreviousCountdownTime = reusePreviousCountdownTime;

		broadcaster.register(listener);
	}

	void reusePreviousCountdownTime(CountdownTask previousCountdownTask) {
		if (reusePreviousCountdownTime && previousCountdownTask != null) {
			countdownInSeconds = previousCountdownTask.countdownInSeconds;
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

	protected boolean isCanceled() {
		return false;
	}

	protected abstract void eachRun();

	protected abstract void finalRun();

	long decreaseAndGet() {
		return --countdownInSeconds;
	}

	boolean isListening() {
		return broadcaster.isRegistered(listener);
	}

	BroadcastListener getListener() {
		return listener;
	}
}
