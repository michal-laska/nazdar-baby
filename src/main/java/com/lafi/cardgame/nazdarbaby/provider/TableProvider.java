package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;

import java.util.HashMap;
import java.util.Map;

public enum TableProvider {

	INSTANCE;

	private final Map<String, Table> tableNameToTable = new HashMap<>();

	public synchronized Table get(String tableName, Broadcaster broadcaster) {
		return tableNameToTable.computeIfAbsent(tableName, s -> new Table(tableName, broadcaster));
	}

	void delete(String tableName) {
		tableNameToTable.remove(tableName);
	}
}
