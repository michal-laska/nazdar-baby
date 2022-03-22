package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.broadcast.BroadcastListener;
import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.counter.CountdownCounter;
import com.lafi.cardgame.nazdarbaby.points.Points;
import com.lafi.cardgame.nazdarbaby.user.User;
import com.lafi.cardgame.nazdarbaby.util.TimeUtil;
import com.lafi.cardgame.nazdarbaby.util.UiUtil;
import com.lafi.cardgame.nazdarbaby.view.BoardView;
import com.lafi.cardgame.nazdarbaby.view.TableView;
import com.vaadin.flow.component.checkbox.Checkbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class Table {

	public static final int MINIMUM_USERS = Collections.min(Points.NUMBER_OF_USERS_TO_WIN_MAP.keySet());
	public static final int MAXIMUM_USERS = Collections.max(Points.NUMBER_OF_USERS_TO_WIN_MAP.keySet());
	public static final int NOTIFICATION_DELAY_IN_MINUTES = 5;

	private final String tableName;
	private final Broadcaster broadcaster;
	private final UserProvider userProvider;
	private final Game game;

	private final List<Checkbox> countdownCheckboxes = new ArrayList<>();
	private ExecutorService newGameExecutorService;

	private Instant lastNotificationTime;
	private int nextButtonClickCounter;
	private Integer passwordHash;

	Table(String tableName, Broadcaster broadcaster) {
		this.tableName = tableName;
		this.broadcaster = broadcaster;

		userProvider = UserProvider.get(tableName);
		game = new Game(userProvider);

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
		return passwordHash != null && passwordHash != 0;
	}

	public void addCountdownCheckbox(Checkbox countdownCheckbox) {
		if (!isNewGameCountdownRunning()) {
			return;
		}

		List<User> users = game.isGameInProgress() ? game.getMatchUsers() : userProvider.getPlayingUsers();
		if (countdownCheckboxes.size() == users.size()) {
			countdownCheckboxes.remove(0);
		}

		countdownCheckboxes.add(countdownCheckbox);
	}

	public void startNewGameCountdown(BroadcastListener listener) {
		if (isNewGameCountdownRunning()) {
			return;
		}

		countdownCheckboxes.clear();

		long remainingDurationInSeconds = TimeUtil.getRemainingDurationInSeconds(1);

		CountdownCounter countdownCounter = createCountdownCounter(remainingDurationInSeconds, listener);
		newGameExecutorService = countdownCounter.start();
	}

	public void stopNewGameCountdown() {
		if (newGameExecutorService != null) {
			newGameExecutorService.shutdown();
		}

		if (game.isGameInProgress()) {
			game.getMatchUsers().forEach(User::resetAction);
			broadcaster.broadcast(BoardView.class, tableName);
		}
	}

	public boolean isNewGameCountdownRunning() {
		return newGameExecutorService != null && !newGameExecutorService.isShutdown();
	}

	public boolean increaseAndCheckNextButtonClickCounter() {
		return ++nextButtonClickCounter % game.getMatchUsers().size() == 0;
	}

	public String getInfo() {
		if (game.isGameInProgress()) {
			return "In progress, Playing = " + game.getMatchUsers().size();
		}

		int readyCounter = 0;
		int notReadyCounter = 0;
		for (User playingUser : userProvider.getPlayingUsers()) {
			if (playingUser.isReady()) {
				++readyCounter;
			} else {
				++notReadyCounter;
			}
		}

		return "Not started, Ready = " + readyCounter + ", Not ready = " + notReadyCounter;
	}

	public boolean isFull() {
		return game.isGameInProgress() && game.getMatchUsers().size() == MAXIMUM_USERS;
	}

	public void tryStartNewGame() {
		if (userProvider.arePlayingUsersReady()) {
			List<User> playingUsers = userProvider.getPlayingUsers();
			int numberOfPlayingUsers = playingUsers.size();

			if (numberOfPlayingUsers >= MINIMUM_USERS && numberOfPlayingUsers <= MAXIMUM_USERS) {
				stopNewGameCountdown();
				resetLastNotificationTime();

				nextButtonClickCounter = 0;
				game.setGameInProgress(true);
			}
		}
	}

	public boolean isCorrectPasswordHash(Integer passwordHash) {
		return Objects.equals(this.passwordHash, passwordHash);
	}

	public void setPasswordHash(int passwordHash) {
		this.passwordHash = passwordHash;
	}

	void delete() {
		userProvider.delete(tableName);
	}

	private CountdownCounter createCountdownCounter(long remainingDurationInSeconds, BroadcastListener listener) {
		return new CountdownCounter(remainingDurationInSeconds, broadcaster, listener) {

			@Override
			public void eachRun() {
				for (Checkbox countdownCheckbox : countdownCheckboxes) {
					String label = countdownCheckbox.getLabel();
					String[] splittedLabel = label.split(FORMATTED_COUNTDOWN_REGEX_SPLITTER);
					String originalLabel = splittedLabel[0];

					String newLabel = originalLabel + getFormattedCountdown();
					UiUtil.access(countdownCheckbox, () -> countdownCheckbox.setLabel(newLabel));
				}
			}

			@Override
			public void finalRun() {
				if (game.isGameInProgress()) {
					stopCurrentGame();
					broadcaster.broadcast(BoardView.class, tableName);
				} else {
					startNewGame();
				}

				// call it even if game is in progress - some players can be in "waiting room"
				broadcaster.broadcast(TableView.class, tableName);
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
	}

	private void resetLastNotificationTime() {
		long now = System.currentTimeMillis();
		long delay = TimeUnit.MINUTES.toMillis(NOTIFICATION_DELAY_IN_MINUTES);

		lastNotificationTime = Instant.ofEpochMilli(now - delay);
	}
}
