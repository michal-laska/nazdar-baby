package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TableProvider {

	private final Map<String, Table> tableNameToTable = new ConcurrentHashMap<>();

	public Table getOrCreate(String tableName, Broadcaster broadcaster) {
		return tableNameToTable.computeIfAbsent(tableName, s -> new Table(tableName, broadcaster));
	}

	public Table get(String tableName) {
		return tableNameToTable.get(tableName);
	}

	public boolean isTableCreated(String tableName) {
		Table table = get(tableName);
		return isTableCreated(table);
	}

	public boolean isTableCreated(Table table) {
		return table != null && !table.verifyPasswordHash(null);
	}

	public boolean isTablePasswordProtected(String tableName) {
		Table table = get(tableName);
		return table.isPasswordProtected();
	}

	public Set<String> getTableNames() {
		return tableNameToTable.keySet();
	}

	public void delete(Table table) {
		table.delete();
		tableNameToTable.remove(table.getTableName());
	}
}
