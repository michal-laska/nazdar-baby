package com.lafi.cardgame.nazdarbaby.view;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.layout.VerticalLayoutWithBroadcast;
import com.lafi.cardgame.nazdarbaby.provider.TableProvider;
import com.lafi.cardgame.nazdarbaby.util.Constant;
import com.lafi.cardgame.nazdarbaby.util.UiUtil;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.apache.commons.lang3.StringUtils;

@Route(TablesView.ROUTE_LOCATION)
public class TablesView extends VerticalLayoutWithBroadcast {

    public static final String ROUTE_LOCATION = StringUtils.EMPTY;

    private TextField tableNameField;

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

        var tablesH1 = new H1("Nazdar baby");
        add(tablesH1);

        var newTableLabel = new NativeLabel("Create table:");
        add(newTableLabel);

        if (tableNameField == null) {
            tableNameField = new TextField();
            tableNameField.setPlaceholder("Table name");

            tableNameField.addInputListener(inputEvent -> UiUtil.makeFieldValid(tableNameField));
            tableNameField.addBlurListener(blurEvent -> UiUtil.makeFieldValid(tableNameField));
            tableNameField.addKeyUpListener(Key.ENTER, keyUpEvent -> addTableAction(tableNameField));
        }
        tableNameField.focus();

        var createTableButton = new Button("Create");
        createTableButton.addClickListener(clickEvent -> addTableAction(tableNameField));

        var createTableHL = new HorizontalLayout(tableNameField, createTableButton);
        add(createTableHL);

        var joinTableLabel = new NativeLabel("Join table:");
        add(joinTableLabel);

        tableProvider.getTableNames().stream()
                .filter(tableProvider::isTableCreated)
                .sorted()
                .forEach(this::showCreatedTables);
    }

    private void addTableAction(TextField tableNameField) {
        String tableName = tableNameField.getValue();

        if (StringUtils.isBlank(tableName)) {
            UiUtil.invalidateFieldWithFocus(tableNameField, "Table name cannot be blank");
            return;
        }
        if (tableProvider.isTableCreated(tableName)) {
            UiUtil.invalidateFieldWithFocus(tableNameField, "Table with this name already exist");
            return;
        }

        navigate(TableView.ROUTE_LOCATION, tableName);
    }

    private void showCreatedTables(String tableName) {
        var table = tableProvider.get(tableName);
        var userProvider = table.getUserProvider();

        var tableButton = new Button(tableName);
        tableButton.setEnabled(!table.isFull());

        if (table.isPasswordProtected()) {
            var icon = userProvider.isCurrentSessionLoggedIn() ? Constant.PASSWORD_OPEN_ICON : Constant.PASSWORD_LOCK_ICON;
            tableButton.setIcon(icon.create());
            tableButton.setIconAfterText(true);
        }

        tableButton.addClickListener(clickEvent -> {
            navigate(TableView.ROUTE_LOCATION, tableName);

            if (userProvider.isCurrentSessionLoggedIn()) {
                var game = table.getGame();
                if (game.isGameInProgress()) {
                    // following line needs to be here in case of navigation from table to board
                    broadcast(TableView.class, tableName, null);
                }
            }
        });

        var tableInfo = table.getInfo();
        var tableInfoLabel = new NativeLabel(tableInfo);

        var tableHL = new HorizontalLayout(tableButton, tableInfoLabel);
        tableHL.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        add(tableHL);
    }
}
