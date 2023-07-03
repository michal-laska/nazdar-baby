package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.countdown.CountdownService;
import com.lafi.cardgame.nazdarbaby.point.PointProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TableProvider {

	private final Map<String, Table> tableNameToTable = new ConcurrentHashMap<>();
	private final PointProvider pointProvider;

	public TableProvider(PointProvider pointProvider) {
		this.pointProvider = pointProvider;
	}

	public Table getOrCreate(String tableName, Broadcaster broadcaster, CountdownService countdownService) {
		return tableNameToTable.getOrDefault(tableName, new Table(tableName, broadcaster, countdownService, pointProvider));
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
