package com.lafi.cardgame.nazdarbaby.session;

import com.vaadin.flow.server.VaadinSession;

public enum SessionProvider {

	INSTANCE;

	public VaadinSession getSession() {
		VaadinSession session = VaadinSession.getCurrent();

		if (session == null) {
			throw new NullPointerException("session");
		}

		return session;
	}
}
