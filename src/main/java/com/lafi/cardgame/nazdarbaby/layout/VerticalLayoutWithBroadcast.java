package com.lafi.cardgame.nazdarbaby.layout;

import com.lafi.cardgame.nazdarbaby.broadcast.BroadcastListener;
import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.util.UiUtil;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.server.Command;

import java.util.function.Consumer;

@PreserveOnRefresh
public abstract class VerticalLayoutWithBroadcast extends VerticalLayout implements BroadcastListener {

	protected final Broadcaster broadcaster;

	public VerticalLayoutWithBroadcast(Broadcaster broadcaster) {
		this.broadcaster = broadcaster;
	}

	public void access(Command command) {
		makeUIAction(ui -> ui.access(command));
	}

	public void access(Component component, Command command) {
		UiUtil.access(component, command);
	}

	public void navigate(String location) {
		makeUIAction(ui -> ui.navigate(location));
	}

	@Override
	protected void onAttach(AttachEvent attachEvent) {
		broadcaster.register(this);
		super.onAttach(attachEvent);
	}

	@Override
	protected void onDetach(DetachEvent detachEvent) {
		super.onDetach(detachEvent);
		broadcaster.unregister(this);
	}

	protected void broadcast() {
		broadcast(null);
	}

	protected void broadcast(String message) {
		broadcast(getClass(), message);
	}

	protected void broadcast(Class<? extends VerticalLayoutWithBroadcast> clazz, String message) {
		String tableName = getTableName();
		broadcaster.broadcast(clazz, tableName, message);
	}

	private void makeUIAction(Consumer<UI> action) {
		UiUtil.makeUIAction(getUI(), action);
	}
}
