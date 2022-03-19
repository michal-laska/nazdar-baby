package com.lafi.cardgame.nazdarbaby.view;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.layout.VerticalLayoutWithBroadcast;
import com.lafi.cardgame.nazdarbaby.provider.Table;
import com.lafi.cardgame.nazdarbaby.provider.TableProvider;
import com.lafi.cardgame.nazdarbaby.router.DecodedStringUrlParameter;

public abstract class ParameterizedView extends VerticalLayoutWithBroadcast implements DecodedStringUrlParameter {

	protected transient Table table;

	protected ParameterizedView(Broadcaster broadcaster) {
		super(broadcaster);
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
		table = TableProvider.INSTANCE.get(tableName, broadcaster);
	}
}
