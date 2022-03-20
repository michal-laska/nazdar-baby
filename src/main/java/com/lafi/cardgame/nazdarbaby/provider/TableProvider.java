package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TableProvider {

	private final Map<String, Table> tableNameToTable = new ConcurrentHashMap<>();

	public Table getOrCreate(String tableName, Broadcaster broadcaster) {
		return tableNameToTable.computeIfAbsent(tableName, s -> new Table(tableName, broadcaster, this));
	}

	public Table get(String tableName) {
		return tableNameToTable.get(tableName);
	}

	public void setPasswordHash(String tableName, Integer passwordHash) {
		Table table = get(tableName);
		table.setPasswordHash(passwordHash);
	}

	public boolean tableWaitForPassword(String tableName) {
		return tableNameExist(tableName) && !tableIsCreated(tableName);
	}

	public boolean tableIsCreated(String tableName) {
		Table table = get(tableName);
		return tableIsCreated(table);
	}

	public boolean tableIsCreated(Table table) {
		return table != null && table.getPasswordHash() != null;
	}

	public boolean isTablePasswordProtected(String tableName) {
		Table table = get(tableName);
		return table.isPasswordProtected();
	}

	public boolean verifyPassword(String tableName, Integer passwordHash) {
		Table table = get(tableName);
		return Objects.equals(table.getPasswordHash(), passwordHash);
	}

	public Set<String> getTableNames() {
		return tableNameToTable.keySet();
	}

	void delete(String tableName) {
		tableNameToTable.remove(tableName);
	}

	private boolean tableNameExist(String tableName) {
		return tableNameToTable.containsKey(tableName);
	}
}
