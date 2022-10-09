package com.lafi.cardgame.nazdarbaby.countdown;

import com.lafi.cardgame.nazdarbaby.broadcast.BroadcastListener;

import java.util.Iterator;
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
		for (Iterator<Map.Entry<BroadcastListener, CountdownTask>> iterator = listenerToTask.entrySet().iterator(); iterator.hasNext(); ) {
			Map.Entry<BroadcastListener, CountdownTask> entry = iterator.next();
			CountdownTask countdownTask = entry.getValue();

			if (countdownTask.isCanceled()) {
				removeCountdownTask(countdownTask, iterator);
			} else {
				countdownTask.eachRun();

				if (countdownTask.decreaseAndGet() < 0) {
					countdownTask.finalRun();
					removeCountdownTask(countdownTask, iterator);
				} else if (!countdownTask.isListening()) {
					removeCountdownTask(countdownTask, iterator);
				}
			}
		}
	}

	public void addCountdownTask(CountdownTask countdownTask) {
		CountdownTask previousCountdownTask = listenerToTask.put(countdownTask.getListener(), countdownTask);
		countdownTask.reusePreviousCountdownTime(previousCountdownTask);
	}

	private void removeCountdownTask(CountdownTask countdownTask, Iterator<Map.Entry<BroadcastListener, CountdownTask>> iterator) {
		BroadcastListener broadcastListener = countdownTask.getListener();
		CountdownTask previousCountdownTask = listenerToTask.get(broadcastListener);

		if (countdownTask.equals(previousCountdownTask)) {
			iterator.remove();
		}
	}
}
