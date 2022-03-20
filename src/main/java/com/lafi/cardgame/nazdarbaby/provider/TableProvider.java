package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TableProvider {

	private final Map<String, Table> tableNameToTable = new ConcurrentHashMap<>();
	private final Map<String, Integer> tableNameToPasswordHash = new HashMap<>();

	public Table get(String tableName, Broadcaster broadcaster) {
		return tableNameToTable.computeIfAbsent(tableName, s -> new Table(tableName, broadcaster, this));
	}

	public void addTable(String tableName, Integer passwordHash) {
		tableNameToPasswordHash.put(tableName, passwordHash);
	}

	public boolean existTableName(String tableName) {
		return tableNameToPasswordHash.containsKey(tableName);
	}

	public boolean tableWaitForPassword(String tableName) {
		return existTableName(tableName) && isNotTableCreated(tableName);
	}

	public boolean isNotTableCreated(String tableName) {
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
		tableNameToPasswordHash.remove(tableName);
	}

	private Integer getPasswordHash(String tableName) {
		return tableNameToPasswordHash.get(tableName);
	}
}
