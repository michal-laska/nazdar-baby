package com.lafi.cardgame.nazdarbaby.provider;

import java.util.HashMap;
import java.util.Map;

public enum TableProvider {

	INSTANCE;

	private final Map<String, Table> tableNameToTable = new HashMap<>();

	public synchronized Table get(String tableName) {
		return tableNameToTable.computeIfAbsent(tableName, s -> new Table(tableName));
	}

	void delete(String tableName) {
		tableNameToTable.remove(tableName);
	}
}
