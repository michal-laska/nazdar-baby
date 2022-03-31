package com.lafi.cardgame.nazdarbaby.view;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.countdown.CountdownService;
import com.lafi.cardgame.nazdarbaby.layout.VerticalLayoutWithBroadcast;
import com.lafi.cardgame.nazdarbaby.provider.Table;
import com.lafi.cardgame.nazdarbaby.provider.TableProvider;
import com.lafi.cardgame.nazdarbaby.router.DecodedStringUrlParameter;

public abstract class ParameterizedView extends VerticalLayoutWithBroadcast implements DecodedStringUrlParameter {

	protected final transient CountdownService countdownService;

	protected transient Table table;

	protected ParameterizedView(Broadcaster broadcaster, TableProvider tableProvider, CountdownService countdownService) {
		super(broadcaster, tableProvider);
		this.countdownService = countdownService;
	}

	@Override
	public void showView(String tableName) {
		initTable(tableName);
		showView();
	}

	@Override
	public String getTableName() {
		return table.getTableName();
	}

	void initTable() {
		initTable(getTableName());
	}

	abstract void showView();

	private void initTable(String tableName) {
		table = tableProvider.getOrCreate(tableName, broadcaster, countdownService);
	}
}
