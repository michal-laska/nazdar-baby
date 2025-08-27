package com.lafi.cardgame.nazdarbaby.view;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.countdown.CountdownService;
import com.lafi.cardgame.nazdarbaby.countdown.CountdownTask;
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
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

@Route(TableView.ROUTE_LOCATION)
public class TableView extends ParameterizedView {

	static final String ROUTE_LOCATION = "table";

	private static final String ENTER_PASSWORD_PLACEHOLDER = "Enter password";
	private static final String NOTIFY_BUTTON_TEXT = "Notify";

	private TextField nameField;

	public TableView(Broadcaster broadcaster, TableProvider tableProvider, CountdownService countdownService) {
		super(broadcaster, tableProvider, countdownService);
	}

	@Override
	public void receiveBroadcast(String message) {
		// in case original table was deleted and a new one with the same name was created
		initTable();

		access(this::showView);
	}

	@Override
	void showView() {
		if (!tableProvider.isTableCreated(getTableName())) {
			showCreatePassword();
			return;
		}

		UserProvider userProvider = table.getUserProvider();

		if (table.isPasswordProtected() && !userProvider.isCurrentSessionLoggedIn()) {
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
		if (nameField == null) {
			nameField = new TextField();
			nameField.setPlaceholder("Your name");
			nameField.setAutofocus(true);

			nameField.addInputListener(inputEvent -> UiUtil.makeFieldValid(nameField));
			nameField.addBlurListener(blurEvent -> UiUtil.makeFieldValid(nameField));
			nameField.addKeyUpListener(Key.ENTER, event -> addUserAction(nameField));
		}

		Button okButton = new Button(Constant.OK_LABEL);
		okButton.addClickListener(click -> addUserAction(nameField));

		HorizontalLayout userNameHL = new HorizontalLayout(nameField, okButton);
		add(userNameHL);
	}

	private void addUserAction(TextField nameField) {
		String userName = nameField.getValue();

		if (StringUtils.isBlank(userName)) {
			UiUtil.invalidateFieldWithFocus(nameField, "Name cannot be blank");
			return;
		}

		UserProvider userProvider = table.getUserProvider();

		if (userProvider.userNameExist(userName)) {
			UiUtil.invalidateFieldWithFocus(nameField, "Name already exists");
			return;
		}

		userProvider.addUser(userName);

		broadcast();
	}

	private void showUsers() {
		removeAll();

		VaadinIcon icon = table.isPasswordProtected() ? Constant.PASSWORD_OPEN_ICON : null;
		addTableH1(icon);

		UserProvider userProvider = table.getUserProvider();
		User currentUser = userProvider.getCurrentUser();

		if (currentUser == null) {
			addUserNameHL();
		}

		H2 playersH2 = new H2("Players:");
		Button addBotButton = new Button("Add bot");

		HorizontalLayout playersHL = new HorizontalLayout(playersH2, addBotButton);
		playersHL.setDefaultVerticalComponentAlignment(Alignment.BASELINE);
		add(playersHL);

		long botCount = userProvider.getPlayingUsers().stream()
				.filter(User::isBot)
				.count();
		addBotButton.setEnabled(currentUser != null && botCount + 1 < Table.MAXIMUM_USERS);

		addBotButton.addClickListener(event -> {
			addBot(userProvider);
			table.tryStartNewGame();
			broadcast();
		});

		List<User> allUsers = userProvider.getAllUsers();

		for (User user : allUsers) {
			boolean isCurrentUser = user.equals(currentUser);

			if (isCurrentUser && user.isLoggedOut()) {
				user.resetAction();
				broadcast();

				break;
			}
		}

		for (User user : allUsers) {
			NativeLabel userName = new NativeLabel(user.getName());

			if (user.isLoggedOut()) {
				userName.getStyle().set("text-decoration", "line-through");
			}

			NativeLabel userPoints = new NativeLabel("(" + user.getPoints() + Constant.POINTS_LABEL + ')');

			boolean isCurrentUser = user.equals(currentUser);

			Checkbox readyCheckbox = createReadyCheckbox(user, isCurrentUser);
			Checkbox logoutCheckbox = createLogoutCheckbox(user, isCurrentUser);

			HorizontalLayout horizontalLayout = new HorizontalLayout(userName, userPoints);
			if (user.isBot()) {
				user.setReady(true);

				Button removeButton = new Button("Remove");
				removeButton.setEnabled(currentUser != null);

				removeButton.addClickListener(event -> {
					userProvider.removeBot(user);

					if (userProvider.getReadyUsersCount() < Table.MINIMUM_USERS) {
						table.stopNewGameCountdown();
					}
					table.tryStartNewGame();

					broadcast();
				});

				horizontalLayout.add(removeButton);
			} else {
				horizontalLayout.add(readyCheckbox, logoutCheckbox);

				if (isCurrentUser) {
					String text = "(Takeover code = " + user.getTakeoverCode() + ')';
					NativeLabel takeoverLabel = new NativeLabel(text);

					horizontalLayout.add(takeoverLabel);
				}
			}

			horizontalLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
			add(horizontalLayout);

			if (isCurrentUser) {
				long readyUsersCount = userProvider.getReadyUsersCount();

				if (userProvider.arePlayingUsersReady()) {
					List<User> playingUsers = userProvider.getPlayingUsers();
					int numberOfPlayingUsers = playingUsers.size();

					if (numberOfPlayingUsers < Table.MINIMUM_USERS) {
						Notification.show("Minimum number of players is " + Table.MINIMUM_USERS);
					} else if (numberOfPlayingUsers > Table.MAXIMUM_USERS) {
						Notification.show("Maximum number of players is " + Table.MAXIMUM_USERS);
					}
				} else if (readyUsersCount >= Table.MINIMUM_USERS && readyUsersCount > botCount) {
					if (user.isReady()) {
						table.addCountdownCheckbox(this, readyCheckbox);
					} else {
						table.addCountdownCheckbox(this, logoutCheckbox);
					}
				}
			}
		}
	}

	private void addBot(UserProvider userProvider) {
        var botNames = new ArrayList<>(Constant.BOT_NAMES);
        Collections.shuffle(botNames);

		String botName;
		do {
			botName = botNames.removeFirst();
		} while (!userProvider.addBot(botName));
	}

	private Checkbox createReadyCheckbox(User user, boolean isCurrentUser) {
		Checkbox readyCheckbox = new Checkbox("Ready", user.isReady());
		readyCheckbox.setEnabled(isCurrentUser);

		readyCheckbox.addClickListener(event -> {
			boolean value = readyCheckbox.getValue();
			user.setReady(value);

			UserProvider userProvider = table.getUserProvider();
			long readyUsersCount = userProvider.getReadyUsersCount();
			if (readyUsersCount < Table.MINIMUM_USERS || readyUsersCount > Table.MAXIMUM_USERS) {
				table.stopNewGameCountdown();
			}

			if (value) {
				table.tryStartNewGame();
			}

			broadcast();
		});

		return readyCheckbox;
	}

	private Checkbox createLogoutCheckbox(User user, boolean isCurrentUser) {
		Checkbox logoutCheckbox = new Checkbox(Constant.LOGOUT_LABEL, user.isLoggedOut());
		logoutCheckbox.setEnabled(isCurrentUser);

		UserProvider userProvider = table.getUserProvider();
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
					tableProvider.delete(table);
				} else {
					table.tryStartNewGame();
				}

				navigateToTablesView();
			}

			broadcast();
		});

		return logoutCheckbox;
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
		removeAll();

		NativeLabel gameInProgressLabel = new NativeLabel("Game in progress");
		add(gameInProgressLabel);

		addNotifyPossibility();
		addSpectatePossibility();
		addTakeoverUserPossibility();

		UiUtil.createNavigationToTablesView(this);
	}

	private void disableNotifyButtonIfRequired(Button notifyButton) {
		long remainingDurationInSeconds = TimeUtil.getRemainingDurationInSeconds(table.getLastNotificationTime(), Table.NOTIFICATION_DELAY_IN_MINUTES);

		if (remainingDurationInSeconds <= 0) {
			return;
		}

		notifyButton.setEnabled(false);

		addNotificationCountdownTask(remainingDurationInSeconds, notifyButton);
	}

	private void addNotifyPossibility() {
		NativeLabel notifyLabel = new NativeLabel("Want to join - notify players");
		Button notifyButton = new Button(NOTIFY_BUTTON_TEXT);

		HorizontalLayout redirectHL = new HorizontalLayout(notifyLabel, notifyButton);
		redirectHL.setDefaultVerticalComponentAlignment(Alignment.CENTER);
		add(redirectHL);

		notifyButton.addClickListener(clickEvent -> {
			table.setLastNotificationTimeToNow();

			showGameInProgress();

			UserProvider userProvider = table.getUserProvider();
			User currentUser = userProvider.getCurrentUser();

			String notificationMessage = currentUser == null ? "A new player" : "Player '" + currentUser + '\'';
			notificationMessage += " would like to join";

			broadcast(BoardView.class, notificationMessage);
		});

		disableNotifyButtonIfRequired(notifyButton);
	}

	private void addSpectatePossibility() {
		NativeLabel spectateLabel = new NativeLabel("Want to spectate");
		Button spectateButton = new Button("Spectate");

		HorizontalLayout spectateHL = new HorizontalLayout(spectateLabel, spectateButton);
		spectateHL.setDefaultVerticalComponentAlignment(Alignment.CENTER);
		add(spectateHL);

		spectateButton.addClickListener(clickEvent -> navigateToTable(BoardView.ROUTE_LOCATION));
	}

	private void addTakeoverUserPossibility() {
		NativeLabel takeoverLabel = new NativeLabel("Takeover user");

		IntegerField takeoverField = new IntegerField();
		takeoverField.setPlaceholder("Enter code");
		takeoverField.addBlurListener(blurEvent -> UiUtil.makeFieldValid(takeoverField));
		takeoverField.addKeyUpListener(Key.ENTER, event -> takeoverAction(takeoverField));

		Button takeoverButton = new Button("Takeover");
		takeoverButton.addClickListener(clickEvent -> takeoverAction(takeoverField));

		HorizontalLayout spectateHL = new HorizontalLayout(takeoverLabel, takeoverField, takeoverButton);
		spectateHL.setDefaultVerticalComponentAlignment(Alignment.CENTER);
		add(spectateHL);
	}

	private void takeoverAction(IntegerField takeoverField) {
		UserProvider userProvider = table.getUserProvider();
		Integer takeoverCode = takeoverField.getValue();

		boolean success = userProvider.takeoverUser(takeoverCode);

		if (success) {
			navigateToTable(BoardView.ROUTE_LOCATION);
		} else {
			UiUtil.invalidateFieldWithFocus(takeoverField, "Wrong code");
		}
	}

	private void addNotificationCountdownTask(long remainingDurationInSeconds, Button notifyButton) {
		CountdownTask countdownTask = new CountdownTask(remainingDurationInSeconds, broadcaster, this) {

			@Override
			protected void eachRun() {
				String newNotifyButtonText = NOTIFY_BUTTON_TEXT + getFormattedCountdown();
				access(notifyButton, () -> notifyButton.setText(newNotifyButtonText));
			}

			@Override
			protected void finalRun() {
				access(notifyButton, () -> {
					notifyButton.setText(NOTIFY_BUTTON_TEXT);
					notifyButton.setEnabled(true);
				});
			}

			@Override
			protected boolean isCanceled() {
				Game game = table.getGame();
				return !game.isGameInProgress();
			}
		};

		countdownService.addCountdownTask(countdownTask);
	}

	private void createPasswordAction(PasswordField createPasswordField, PasswordField confirmPasswordField) {
		String password1 = createPasswordField.getValue();
		String password2 = confirmPasswordField.getValue();

		if (password1.equals(password2)) {
			tableProvider.add(table);
			table.setPasswordHash(password1.hashCode());

			UserProvider userProvider = table.getUserProvider();
			userProvider.logInCurrentSession();

			broadcast();
		} else {
			UiUtil.invalidateFieldWithFocus(confirmPasswordField, "Passwords don't match");
		}
	}

	private void loginToTableAction(PasswordField passwordField) {
		int passwordHash = passwordField.getValue().hashCode();
		boolean passwordHashIsCorrect = table.verifyPasswordHash(passwordHash);

		if (passwordHashIsCorrect) {
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
		createPasswordField.setAutofocus(true);
		add(createPasswordField);

		PasswordField confirmPasswordField = new PasswordField();
		confirmPasswordField.setMinWidth(minimalWidth);
		confirmPasswordField.setPlaceholder("Confirm password");

		confirmPasswordField.addBlurListener(blurEvent -> UiUtil.makeFieldValid(confirmPasswordField));

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
		passwordField.setAutofocus(true);
		passwordField.addKeyUpListener(Key.ENTER, event -> loginToTableAction(passwordField));
		passwordField.addInputListener(inputEvent -> UiUtil.makeFieldValid(passwordField));

		Button okButton = new Button(Constant.OK_LABEL);
		okButton.addClickListener(click -> loginToTableAction(passwordField));

		HorizontalLayout passwordHL = new HorizontalLayout(passwordField, okButton);
		passwordHL.setDefaultVerticalComponentAlignment(Alignment.BASELINE);
		add(passwordHL);
	}
}
