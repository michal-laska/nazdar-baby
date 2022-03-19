package com.lafi.cardgame.nazdarbaby.util;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public final class TimeUtil {

	private TimeUtil() {
	}

	public static long getRemainingDurationInSeconds(int durationInMinutes) {
		return getRemainingDurationInSeconds(Instant.now(), durationInMinutes);
	}

	public static long getRemainingDurationInSeconds(Instant from, int durationInMinutes) {
		Duration duration = Duration.between(from, Instant.now());
		return TimeUnit.MINUTES.toSeconds(durationInMinutes) - duration.getSeconds();
	}
}
