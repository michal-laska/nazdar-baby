package com.lafi.cardgame.nazdarbaby.view;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.layout.VerticalLayoutWithBroadcast;
import com.lafi.cardgame.nazdarbaby.provider.Table;
import com.lafi.cardgame.nazdarbaby.provider.TableProvider;
import com.lafi.cardgame.nazdarbaby.provider.UserProvider;
import com.lafi.cardgame.nazdarbaby.util.Constant;
import com.lafi.cardgame.nazdarbaby.util.UiUtil;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

import org.apache.commons.lang3.StringUtils;

@Route(TablesView.ROUTE_LOCATION)
public class TablesView extends VerticalLayoutWithBroadcast {

	public static final String ROUTE_LOCATION = StringUtils.EMPTY;

	private HorizontalLayout createTableHL;

	public TablesView(Broadcaster broadcaster, TableProvider tableProvider) {
		super(broadcaster, tableProvider);
		showView();
	}

	@Override
	public void receiveBroadcast(String message) {
		access(this::showView);
	}

	@Override
	public String getTableName() {
		return null;
	}

	private void showView() {
		removeAll();

		H1 tablesH1 = new H1("Nazdar baby");
		add(tablesH1);

		Label newTableLabel = new Label("Create table:");
		add(newTableLabel);

		if (createTableHL == null) {
			TextField tableNameField = new TextField();
			tableNameField.setPlaceholder("Table name");
			tableNameField.focus();
			tableNameField.addKeyUpListener(Key.ENTER, event -> addTableAction(tableNameField));
			tableNameField.addInputListener(inputEvent -> UiUtil.makeFieldValid(tableNameField));

			Button createTableButton = new Button("Create");
			createTableButton.addClickListener(clickEvent -> addTableAction(tableNameField));

			createTableHL = new HorizontalLayout(tableNameField, createTableButton);
		}
		add(createTableHL);

		Label joinTableLabel = new Label("Join table:");
		add(joinTableLabel);

		tableProvider.getTableNames().forEach(this::addTableToJoinIfCreated);
	}

	private void addTableAction(TextField tableNameField) {
		String tableName = tableNameField.getValue();

		if (StringUtils.isBlank(tableName)) {
			UiUtil.invalidateFieldWithFocus(tableNameField, "Table name cannot be blank");
			return;
		}
		if (tableProvider.existTableName(tableName)) {
			UiUtil.invalidateFieldWithFocus(tableNameField, "Table name already exists or it's under construction");
			return;
		}

		tableProvider.addTable(tableName, null);

		String location = UiUtil.createLocation(TableView.ROUTE_LOCATION, tableName);
		navigate(location);
	}

	private void addTableToJoinIfCreated(String tableName) {
		if (tableProvider.isNotTableCreated(tableName)) {
			return;
		}

		Table table = tableProvider.get(tableName, broadcaster);

		Button tableButton = new Button(tableName);
		tableButton.setEnabled(!table.isFull());

		if (tableProvider.isTablePasswordProtected(tableName)) {
			UserProvider userProvider = table.getUserProvider();

			VaadinIcon icon = userProvider.isCurrentSessionLoggedIn() ? Constant.PASSWORD_OPEN_ICON : Constant.PASSWORD_LOCK_ICON;
			tableButton.setIcon(icon.create());
			tableButton.setIconAfterText(true);
		}

		tableButton.addClickListener(clickEvent -> {
			String location = UiUtil.createLocation(TableView.ROUTE_LOCATION, tableName);
			navigate(location);
		});

		String tableInfo = table.getInfo();
		Label tableInfoLabel = new Label(tableInfo);

		HorizontalLayout tableHL = new HorizontalLayout(tableButton, tableInfoLabel);
		tableHL.setDefaultVerticalComponentAlignment(Alignment.CENTER);
		add(tableHL);
	}
}
