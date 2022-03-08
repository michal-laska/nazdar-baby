package com.lafi.cardgame.nazdarbaby.router;

import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public interface DecodedStringUrlParameter extends HasUrlParameter<String> {

	@Override
	default void setParameter(BeforeEvent beforeEvent, String parameter) {
		String tableName = URLDecoder.decode(parameter, StandardCharsets.UTF_8);
		showView(tableName);
	}

	void showView(String tableName);
}
