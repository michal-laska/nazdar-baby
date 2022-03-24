package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.session.SessionProvider;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TableProvider {

	private final Map<String, Table> tableNameToTable = new ConcurrentHashMap<>();
	private final SessionProvider sessionProvider;

	public TableProvider(SessionProvider sessionProvider) {
		this.sessionProvider = sessionProvider;
	}

	public Table getOrCreate(String tableName, Broadcaster broadcaster) {
		return tableNameToTable.getOrDefault(tableName, new Table(tableName, broadcaster, sessionProvider));
	}

	public void add(Table table) {
		tableNameToTable.put(table.getTableName(), table);
	}

	public Table get(String tableName) {
		return tableNameToTable.get(tableName);
	}

	public boolean isTableCreated(String tableName) {
		return tableNameToTable.containsKey(tableName);
	}

	public Set<String> getTableNames() {
		return tableNameToTable.keySet();
	}

	public void delete(Table table) {
		tableNameToTable.remove(table.getTableName());
	}
}
