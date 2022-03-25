package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.points.PointProvider;
import com.lafi.cardgame.nazdarbaby.session.SessionProvider;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TableProvider {

	private final Map<String, Table> tableNameToTable = new ConcurrentHashMap<>();
	private final SessionProvider sessionProvider;
	private final PointProvider pointProvider;

	public TableProvider(SessionProvider sessionProvider, PointProvider pointProvider) {
		this.sessionProvider = sessionProvider;
		this.pointProvider = pointProvider;
	}

	public Table getOrCreate(String tableName, Broadcaster broadcaster) {
		return tableNameToTable.getOrDefault(tableName, new Table(tableName, broadcaster, sessionProvider, pointProvider));
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
