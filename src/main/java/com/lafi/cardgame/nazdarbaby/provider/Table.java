package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.broadcast.BroadcastListener;
import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.countdown.CountdownService;
import com.lafi.cardgame.nazdarbaby.countdown.CountdownTask;
import com.lafi.cardgame.nazdarbaby.point.PointProvider;
import com.lafi.cardgame.nazdarbaby.user.User;
import com.lafi.cardgame.nazdarbaby.util.TimeUtil;
import com.lafi.cardgame.nazdarbaby.util.UiUtil;
import com.lafi.cardgame.nazdarbaby.view.BoardView;
import com.lafi.cardgame.nazdarbaby.view.TableView;
import com.vaadin.flow.component.checkbox.Checkbox;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Table {

	public static final int MINIMUM_USERS = Collections.min(PointProvider.NUMBER_OF_USERS_TO_WINNER_MAP.keySet());
	public static final int MAXIMUM_USERS = Collections.max(PointProvider.NUMBER_OF_USERS_TO_WINNER_MAP.keySet());
	public static final int NOTIFICATION_DELAY_IN_MINUTES = 5;

	private final String tableName;
	private final Broadcaster broadcaster;
	private final CountdownService countdownService;
	private final UserProvider userProvider;
	private final Game game;

	private Instant lastNotificationTime;
	private Instant lastNewGameTime;
	private int passwordHash;

	Table(String tableName, Broadcaster broadcaster, CountdownService countdownService, PointProvider pointProvider) {
		this.tableName = tableName;
		this.broadcaster = broadcaster;
		this.countdownService = countdownService;

		userProvider = new UserProvider();
		game = new Game(userProvider, pointProvider);

		resetLastNotificationTime();
	}

	public String getTableName() {
		return tableName;
	}

	public UserProvider getUserProvider() {
		return userProvider;
	}

	public Game getGame() {
		return game;
	}

	public Instant getLastNotificationTime() {
		return lastNotificationTime;
	}

	public void setLastNotificationTimeToNow() {
		lastNotificationTime = Instant.now();
	}

	public boolean isPasswordProtected() {
		return passwordHash != 0;
	}

	public void addCountdownCheckbox(BroadcastListener listener, Checkbox countdownCheckbox) {
		if (lastNewGameTime == null) {
			lastNewGameTime = Instant.now();
		}

		long remainingDurationInSeconds = TimeUtil.getRemainingDurationInSeconds(lastNewGameTime, 1);
		addCountdownTask(remainingDurationInSeconds, listener, countdownCheckbox);
	}

	public void stopNewGameCountdown() {
		lastNewGameTime = null;

		if (game.isGameInProgress()) {
			game.getMatchUsers().stream()
					.filter(user -> !user.isReady())
					.forEach(User::resetAction);

			broadcaster.broadcast(BoardView.class, tableName);
		}
	}

	public boolean allUsersClickedNextMatchButton() {
		return game.getMatchUsers().stream()
				.filter(user -> !user.isBot())
				.allMatch(User::isReady);
	}

	public String getInfo() {
		int readyCounter = 0;
		int notReadyCounter = 0;
		int botCounter = 0;
		for (User playingUser : userProvider.getPlayingUsers()) {
			if (playingUser.isBot()) {
				++botCounter;
			} else if (playingUser.isReady()) {
				++readyCounter;
			} else {
				++notReadyCounter;
			}
		}

		if (game.isGameInProgress()) {
			return "In progress, Players = " + notReadyCounter + ", Bots = " + botCounter;
		}
		return "Not started, Ready = " + readyCounter + ", Not ready = " + notReadyCounter + ", Bots = " + botCounter;
	}

	public boolean isFull() {
		return game.isGameInProgress() && game.getMatchUsers().size() == MAXIMUM_USERS;
	}

	public void tryStartNewGame() {
		if (!userProvider.arePlayingUsersReady()) {
			return;
		}

		List<User> playingUsers = userProvider.getPlayingUsers();

		int numberOfPlayingUsers = playingUsers.size();
		if (numberOfPlayingUsers < MINIMUM_USERS || numberOfPlayingUsers > MAXIMUM_USERS) {
			return;
		}

		stopNewGameCountdown();

		boolean allBots = playingUsers.stream().allMatch(User::isBot);
		if (allBots) {
			return;
		}

		resetLastNotificationTime();
		game.setGameInProgress(true);
	}

	public boolean verifyPasswordHash(int passwordHash) {
		return this.passwordHash == passwordHash;
	}

	public void setPasswordHash(int passwordHash) {
		this.passwordHash = passwordHash;
	}

	private void addCountdownTask(long remainingDurationInSeconds, BroadcastListener listener, Checkbox countdownCheckbox) {
		CountdownTask countdownTask = new CountdownTask(remainingDurationInSeconds, broadcaster, listener) {

			@Override
			protected void eachRun() {
				String label = countdownCheckbox.getLabel();
				String[] splittedLabel = label.split(FORMATTED_COUNTDOWN_REGEX_SPLITTER);
				String originalLabel = splittedLabel[0];

				String newLabel = originalLabel + getFormattedCountdown();
				UiUtil.access(countdownCheckbox, () -> countdownCheckbox.setLabel(newLabel));
			}

			@Override
			protected void finalRun() {
				if (game.isGameInProgress()) {
					stopCurrentGame();
					broadcaster.broadcast(BoardView.class, tableName);
				} else {
					startNewGame();
				}

				// call it even if game is in progress - some players can be in "waiting room"
				broadcaster.broadcast(TableView.class, tableName);
			}

			@Override
			protected boolean isCanceled() {
				return lastNewGameTime == null;
			}

			private void stopCurrentGame() {
				game.getMatchUsers().stream()
						.filter(user -> !user.isLoggedOut())
						.forEach(user -> user.setNewGame(true));
				game.setGameInProgress(false);
			}

			private void startNewGame() {
				userProvider.getPlayingUsers().stream()
						.filter(user -> !user.isReady())
						.forEach(user -> user.setLoggedOut(true));
				tryStartNewGame();
			}
		};

		countdownService.addCountdownTask(countdownTask);
	}

	private void resetLastNotificationTime() {
		long now = System.currentTimeMillis();
		long delay = TimeUnit.MINUTES.toMillis(NOTIFICATION_DELAY_IN_MINUTES);

		lastNotificationTime = Instant.ofEpochMilli(now - delay);
	}
}
