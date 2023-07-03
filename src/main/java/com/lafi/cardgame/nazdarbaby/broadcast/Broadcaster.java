package com.lafi.cardgame.nazdarbaby.broadcast;

import com.lafi.cardgame.nazdarbaby.view.TableView;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class Broadcaster {

	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final Set<BroadcastListener> listeners = ConcurrentHashMap.newKeySet();

	public void register(BroadcastListener listener) {
		listeners.add(listener);
	}

	public void unregister(BroadcastListener listener) {
		listeners.remove(listener);
	}

	public boolean isRegistered(BroadcastListener listener) {
		return listeners.contains(listener);
	}

	public void broadcast(Class<?> clazz, String tableName) {
		broadcast(clazz, tableName, null);
	}

	public void broadcast(Class<?> clazz, String tableName, String message) {
		listeners.stream()
				.filter(listener -> filterView(listener, clazz, tableName))
				.forEach(listener -> executorService.execute(() -> listener.receiveBroadcast(message)));
	}

	private boolean filterView(BroadcastListener listener, Class<?> clazz, String tableName) {
		return filterTablesView(listener, clazz) || filterParameterizedView(listener, clazz, tableName);
	}

	private boolean filterTablesView(BroadcastListener listener, Class<?> clazz) {
		return listener.getTableName() == null && TableView.class.equals(clazz);
	}

	private boolean filterParameterizedView(BroadcastListener listener, Class<?> clazz, String tableName) {
		return clazz.equals(listener.getClass()) && tableName.equals(listener.getTableName());
	}
}
