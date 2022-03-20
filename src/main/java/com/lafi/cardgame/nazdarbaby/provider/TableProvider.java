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

	public boolean tableNameExist(String tableName) {
		return tableNameToTable.containsKey(tableName);
	}

	public boolean tableWaitForPassword(String tableName) {
		return tableNameExist(tableName) && tableIsNotCreated(tableName);
	}

	public boolean tableIsNotCreated(String tableName) {
		return verifyPassword(tableName, null);
	}

	public boolean isTablePasswordProtected(String tableName) {
		return !verifyPassword(tableName, 0);
	}

	public boolean verifyPassword(String tableName, Integer passwordHash) {
		return Objects.equals(getPasswordHash(tableName), passwordHash);
	}

	public Set<String> getTableNames() {
		return tableNameToTable.keySet();
	}

	void delete(String tableName) {
		tableNameToTable.remove(tableName);
	}

	private Integer getPasswordHash(String tableName) {
		Table table = get(tableName);
		return table.getPasswordHash();
	}
}
