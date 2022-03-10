package com.lafi.cardgame.nazdarbaby.router;

import com.lafi.cardgame.nazdarbaby.util.Constant;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;

import java.net.URLDecoder;

public interface DecodedStringUrlParameter extends HasUrlParameter<String> {

	@Override
	default void setParameter(BeforeEvent beforeEvent, String tableName) {
		String decodedTableName = URLDecoder.decode(tableName, Constant.CHARSET);
		showView(decodedTableName);
	}

	void showView(String tableName);
}
