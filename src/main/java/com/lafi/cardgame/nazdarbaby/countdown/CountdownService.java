package com.lafi.cardgame.nazdarbaby.countdown;

import com.lafi.cardgame.nazdarbaby.broadcast.BroadcastListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CountdownService implements Runnable {

	private final Map<BroadcastListener, CountdownTask> listenerToCounter = new ConcurrentHashMap<>();
	private ScheduledExecutorService executorService;

	@Override
	public void run() {
		for (CountdownTask countdownTask : listenerToCounter.values()) {
			if (countdownTask.isCanceled()) {
				removeCountdownCounter(countdownTask);
			} else {
				countdownTask.eachRun();

				if (countdownTask.decreaseAndGet() < 0) {
					countdownTask.finalRun();
					removeCountdownCounter(countdownTask);
				} else if (!countdownTask.isListening()) {
					removeCountdownCounter(countdownTask);
				}
			}
		}
	}

	public synchronized void addCountdownCounter(CountdownTask countdownTask) {
		listenerToCounter.put(countdownTask.getListener(), countdownTask);

		if (executorService == null) {
			executorService = Executors.newSingleThreadScheduledExecutor();
			executorService.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
		}
	}

	private void removeCountdownCounter(CountdownTask countdownTask) {
		BroadcastListener broadcastListener = countdownTask.getListener();
		listenerToCounter.remove(broadcastListener);

		if (listenerToCounter.isEmpty()) {
			executorService.shutdown();
			executorService = null;
		}
	}
}
