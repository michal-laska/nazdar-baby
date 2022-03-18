package com.lafi.cardgame.nazdarbaby.util;

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

    private final BroadcastListener listener;

    private long countdownInSeconds;
    private ScheduledExecutorService executorService;

    public CountdownCounter(BroadcastListener listener, long countdownInSeconds) {
        this.listener = listener;
        this.countdownInSeconds = countdownInSeconds;
    }

    @Override
    public void run() {
        eachRun();

        if (--countdownInSeconds < 0) {
            finalRun();
            shutdown();
        } else if (!Broadcaster.INSTANCE.isRegistered(listener)) {
            shutdown();
        }
    }

    public ExecutorService start() {
        Broadcaster.INSTANCE.register(listener);

        executorService = Executors.newSingleThreadScheduledExecutor();
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

    protected abstract void eachRun();

    protected abstract void finalRun();

    private void shutdown() {
        executorService.shutdown();
    }
}
