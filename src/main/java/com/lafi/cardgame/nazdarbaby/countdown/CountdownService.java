package com.lafi.cardgame.nazdarbaby.countdown;

import com.lafi.cardgame.nazdarbaby.broadcast.BroadcastListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CountdownService implements Runnable {

	private final Map<BroadcastListener, CountdownTask> listenerToTask = new ConcurrentHashMap<>();

    public CountdownService() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
    }

	@Override
	public void run() {
		listenerToTask.values().forEach(countdownTask -> {
			if (countdownTask.isCanceled()) {
				removeCountdownTask(countdownTask);
			} else {
				countdownTask.eachRun();

				if (countdownTask.decreaseAndGet() < 0) {
					countdownTask.finalRun();
					removeCountdownTask(countdownTask);
				} else if (!countdownTask.isListening()) {
					removeCountdownTask(countdownTask);
				}
			}
		});
	}

	public void addCountdownTask(CountdownTask countdownTask) {
		CountdownTask previousCountdownTask = listenerToTask.put(countdownTask.getListener(), countdownTask);
		countdownTask.reusePreviousCountdownTime(previousCountdownTask);
	}

	private void removeCountdownTask(CountdownTask countdownTask) {
		BroadcastListener broadcastListener = countdownTask.getListener();
		CountdownTask removedCountdownTask = listenerToTask.remove(broadcastListener);

		if (!countdownTask.equals(removedCountdownTask)) {
			addCountdownTask(removedCountdownTask);
		}
	}
}
