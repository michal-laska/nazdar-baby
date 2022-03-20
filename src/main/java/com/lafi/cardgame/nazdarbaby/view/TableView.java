package com.lafi.cardgame.nazdarbaby.view;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.counter.CountdownCounter;
import com.lafi.cardgame.nazdarbaby.provider.Game;
import com.lafi.cardgame.nazdarbaby.provider.Table;
import com.lafi.cardgame.nazdarbaby.provider.TableProvider;
import com.lafi.cardgame.nazdarbaby.provider.UserProvider;
import com.lafi.cardgame.nazdarbaby.user.User;
import com.lafi.cardgame.nazdarbaby.util.Constant;
import com.lafi.cardgame.nazdarbaby.util.TimeUtil;
import com.lafi.cardgame.nazdarbaby.util.UiUtil;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

@Route(TableView.ROUTE_LOCATION)
public class TableView extends ParameterizedView {

	static final String ROUTE_LOCATION = "table";

	private static final String ENTER_PASSWORD_PLACEHOLDER = "Enter password";
	private static final String NOTIFY_BUTTON_TEXT = "Notify";

	private HorizontalLayout userNameHL;
	private TextField nameField;

	public TableView(Broadcaster broadcaster, TableProvider tableProvider) {
		super(broadcaster, tableProvider);
	}

	@Override
	public void receiveBroadcast(String message) {
		// in case original table was deleted and a new one with the same name was created
		initTable();

		access(this::showView);
	}

	@Override
	void showView() {
		String tableName = getTableName();

		if (tableProvider.tableWaitForPassword(tableName)) {
			showCreatePassword();
			return;
		}

		UserProvider userProvider = table.getUserProvider();

		if (tableProvider.isTablePasswordProtected(tableName) && !userProvider.isCurrentSessionLoggedIn()) {
			showEnterPassword();
			return;
		}

		Game game = table.getGame();
		if (game.isGameInProgress()) {
			User currentUser = userProvider.getCurrentUser();
			if (currentUser == null || currentUser.isLoggedOut()) {
				showGameInProgress();
			} else {
				navigateToTable(BoardView.ROUTE_LOCATION);
			}
		} else {
			showUsers();
		}
	}

	private void addUserNameHL() {
		if (userNameHL == null) {
			nameField = new TextField();
			nameField.setPlaceholder("Your name");
			nameField.focus();

			nameField.addInputListener(inputEvent -> UiUtil.makeFieldValid(nameField));
			nameField.addKeyUpListener(Key.ENTER, event -> addUserAction(nameField));

			Button okButton = new Button(Constant.OK_LABEL);
			okButton.addClickListener(click -> addUserAction(nameField));

			userNameHL = new HorizontalLayout(nameField, okButton);
		} else {
			nameField.focus();
		}
		add(userNameHL);
	}

	private void addUserAction(TextField nameField) {
		String userName = nameField.getValue();
		UserProvider userProvider = table.getUserProvider();

		if (StringUtils.isBlank(userName)) {
			UiUtil.invalidateFieldWithFocus(nameField, "Name cannot be blank");
			return;
		}
		if (userProvider.usernameExist(userName)) {
			UiUtil.invalidateFieldWithFocus(nameField, "Name already exists");
			return;
		}

		userProvider.addUser(userName);
		remove(userNameHL);

		broadcast();
	}

	private void showUsers() {
		removeAll();

		VaadinIcon icon = tableProvider.isTablePasswordProtected(getTableName()) ? Constant.PASSWORD_OPEN_ICON : null;
		addTableH1(icon);

		UserProvider userProvider = table.getUserProvider();
		User currentUser = userProvider.getCurrentUser();

		if (currentUser == null) {
			addUserNameHL();
		}

		H2 playersH2 = new H2("Players:");
		add(playersH2);

		for (User user : userProvider.getAllUsers()) {
			boolean isCurrentUser = user.equals(currentUser);

			if (isCurrentUser && user.isLoggedOut()) {
				user.resetAction();
				broadcast();

				break;
			}
		}

		for (User user : userProvider.getAllUsers()) {
			Label userName = new Label(user.getName());

			if (user.isLoggedOut()) {
				userName.getStyle().set("text-decoration", "line-through");
			}

			boolean isCurrentUser = user.equals(currentUser);

			Checkbox readyCheckbox = new Checkbox("Ready", user.isReady());
			readyCheckbox.setEnabled(isCurrentUser);
			readyCheckbox.addClickListener(event -> {
				boolean value = readyCheckbox.getValue();
				user.setReady(value);

				if (userProvider.getReadyUsersCount() >= Table.MINIMUM_USERS) {
					table.startNewGameCountdown(this);
				} else {
					table.stopNewGameCountdown();
				}

				if (value) {
					table.tryStartNewGame();
				}

				broadcast();
			});

			Checkbox logoutCheckbox = new Checkbox(Constant.LOGOUT_LABEL, user.isLoggedOut());
			logoutCheckbox.setEnabled(isCurrentUser);

			boolean tableHasLastOneLoggedInUser = userProvider.getPlayingUsers().size() == 1;

			if (isCurrentUser && !user.isLoggedOut() && tableHasLastOneLoggedInUser) {
				logoutCheckbox.setLabel(logoutCheckbox.getLabel() + " and delete table");
				logoutCheckbox.getStyle().set(Constant.COLOR_STYLE, Constant.RED_COLOR);
			}

			logoutCheckbox.addClickListener(event -> {
				boolean value = logoutCheckbox.getValue();
				user.setLoggedOut(value);

				if (userProvider.getReadyUsersCount() < Table.MINIMUM_USERS) {
					table.stopNewGameCountdown();
				}

				if (value) {
					if (tableHasLastOneLoggedInUser) {
						table.delete();
					} else {
						table.tryStartNewGame();
					}

					navigateToTablesView();
				}

				broadcast();
			});

			Label userPoints = new Label("(" + user.getPoints() + Constant.POINTS_LABEL + ')');

			HorizontalLayout horizontalLayout = new HorizontalLayout(userName, userPoints, readyCheckbox, logoutCheckbox);
			horizontalLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
			add(horizontalLayout);

			if (isCurrentUser) {
				if (userProvider.arePlayingUsersReady()) {
					List<User> playingUsers = userProvider.getPlayingUsers();
					int numberOfPlayingUsers = playingUsers.size();

					if (numberOfPlayingUsers < Table.MINIMUM_USERS) {
						Notification.show("Minimum number of players is " + Table.MINIMUM_USERS);
					} else if (numberOfPlayingUsers > Table.MAXIMUM_USERS) {
						Notification.show("Maximum number of players is " + Table.MAXIMUM_USERS);
					}
				} else if (user.isReady()) {
					table.addCountdownCheckbox(readyCheckbox);
				} else {
					table.addCountdownCheckbox(logoutCheckbox);
				}
			}
		}
	}

	private void addTableH1(VaadinIcon icon) {
		H1 tableH1 = new H1(getTableName() + " table");

		if (icon == null) {
			add(tableH1);
		} else {
			HorizontalLayout tableHL = new HorizontalLayout(tableH1, icon.create());
			tableHL.setAlignItems(Alignment.BASELINE);
			add(tableHL);
		}
	}

	private void showGameInProgress() {
		userNameHL = null;
		removeAll();

		Label gameInProgressLabel = new Label("Game in progress");
		add(gameInProgressLabel);

		Label joinLabel = new Label("Want to join - notify players");
		Button notifyButton = new Button(NOTIFY_BUTTON_TEXT);

		notifyButton.addClickListener(clickEvent -> {
			table.setLastNotificationTimeToNow();

			showGameInProgress();

			UserProvider userProvider = table.getUserProvider();
			User currentUser = userProvider.getCurrentUser();

			String notificationMessage = currentUser == null ? "A new player" : "Player '" + currentUser + '\'';
			notificationMessage += " would like to join";

			broadcast(BoardView.class, notificationMessage);
		});

		HorizontalLayout redirectHorizontalLayout = new HorizontalLayout(joinLabel, notifyButton);
		redirectHorizontalLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
		add(redirectHorizontalLayout);

		disableNotifyButtonIfRequired(notifyButton);

		UiUtil.createNavigationToTablesView(this);
	}

	private void disableNotifyButtonIfRequired(Button notifyButton) {
		long remainingDurationInSeconds = TimeUtil.getRemainingDurationInSeconds(
				table.getLastNotificationTime(), Table.NOTIFICATION_DELAY_IN_MINUTES);

		if (remainingDurationInSeconds <= 0) {
			return;
		}

		notifyButton.setEnabled(false);

		CountdownCounter countdownCounter = createCountdownCounter(remainingDurationInSeconds, notifyButton);
		countdownCounter.start();
	}

	private CountdownCounter createCountdownCounter(long remainingDurationInSeconds, Button notifyButton) {
		return new CountdownCounter(remainingDurationInSeconds, broadcaster, this) {

			@Override
			public void eachRun() {
				String newNotifyButtonText = NOTIFY_BUTTON_TEXT + getFormattedCountdown();
				access(notifyButton, () -> notifyButton.setText(newNotifyButtonText));
			}

			@Override
			public void finalRun() {
				access(notifyButton, () -> {
					notifyButton.setText(NOTIFY_BUTTON_TEXT);
					notifyButton.setEnabled(true);
				});
			}
		};
	}

	private void createPasswordAction(PasswordField createPasswordField, PasswordField confirmPasswordField) {
		String password1 = createPasswordField.getValue();
		String password2 = confirmPasswordField.getValue();

		if (password1.equals(password2)) {
			tableProvider.setPasswordHash(getTableName(), password1.hashCode());

			UserProvider userProvider = table.getUserProvider();
			userProvider.logInCurrentSession();

			broadcast();
		} else {
			UiUtil.invalidateFieldWithFocus(confirmPasswordField, "Passwords don't match");
		}
	}

	private void verifyPasswordAction(PasswordField passwordField) {
		int passwordHash = passwordField.getValue().hashCode();
		boolean passwordIsCorrect = tableProvider.verifyPassword(getTableName(), passwordHash);

		if (passwordIsCorrect) {
			UserProvider userProvider = table.getUserProvider();
			userProvider.logInCurrentSession();

			showView();
		} else {
			UiUtil.invalidateFieldWithFocus(passwordField, "Incorrect password");
		}
	}

	private void showCreatePassword() {
		removeAll();

		addTableH1(null);

		String minimalWidth = "6cm";

		PasswordField createPasswordField = new PasswordField("Create table password (optional)");
		createPasswordField.setMinWidth(minimalWidth);
		createPasswordField.setPlaceholder(ENTER_PASSWORD_PLACEHOLDER);
		createPasswordField.focus();
		add(createPasswordField);

		PasswordField confirmPasswordField = new PasswordField();
		confirmPasswordField.setMinWidth(minimalWidth);
		confirmPasswordField.setPlaceholder("Confirm password");

		createPasswordField.addKeyUpListener(Key.ENTER, event -> createPasswordAction(createPasswordField, confirmPasswordField));
		confirmPasswordField.addKeyUpListener(Key.ENTER, event -> createPasswordAction(createPasswordField, confirmPasswordField));

		createPasswordField.addInputListener(inputEvent -> UiUtil.makeFieldValid(confirmPasswordField));
		confirmPasswordField.addInputListener(inputEvent -> UiUtil.makeFieldValid(confirmPasswordField));

		Button okButton = new Button(Constant.OK_LABEL);
		okButton.addClickListener(click -> createPasswordAction(createPasswordField, confirmPasswordField));

		HorizontalLayout confirmPasswordHL = new HorizontalLayout(confirmPasswordField, okButton);
		add(confirmPasswordHL);
	}

	private void showEnterPassword() {
		removeAll();

		addTableH1(Constant.PASSWORD_LOCK_ICON);

		PasswordField passwordField = new PasswordField("Table is password protected");
		passwordField.setPlaceholder(ENTER_PASSWORD_PLACEHOLDER);
		passwordField.focus();
		passwordField.addKeyUpListener(Key.ENTER, event -> verifyPasswordAction(passwordField));
		passwordField.addInputListener(inputEvent -> UiUtil.makeFieldValid(passwordField));

		Button okButton = new Button(Constant.OK_LABEL);
		okButton.addClickListener(click -> verifyPasswordAction(passwordField));

		HorizontalLayout passwordHL = new HorizontalLayout(passwordField, okButton);
		passwordHL.setDefaultVerticalComponentAlignment(Alignment.BASELINE);
		add(passwordHL);
	}
}
