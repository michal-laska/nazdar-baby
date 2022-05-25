package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.user.User;
import com.vaadin.flow.server.VaadinSession;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UserProvider {

	private final Map<VaadinSession, User> sessionToUser = new HashMap<>();
	private final Map<String, User> botNameToBot = new HashMap<>();
	private final Set<VaadinSession> loggedInSessions = new HashSet<>();
	private final Set<Integer> takeoverCodes = new HashSet<>();

	UserProvider() {
	}

	public void addUser(String userName) {
		VaadinSession session = VaadinSession.getCurrent();
		User user = new User(userName, takeoverCodes);

		sessionToUser.put(session, user);
	}

	public boolean takeoverUser(Integer takeoverCode) {
		if (takeoverCode == null) {
			return false;
		}

		Optional<User> userOptional = getPlayingUsers().stream()
				.filter(user -> Objects.equals(user.getTakeoverCode(), takeoverCode))
				.findFirst();

		if (userOptional.isEmpty()) {
			return false;
		}

		User user = userOptional.get();

		VaadinSession oldSession = sessionToUser.entrySet().stream()
				.filter(entry -> entry.getValue().equals(user))
				.findFirst()
				.get()
				.getKey();
		sessionToUser.remove(oldSession);
		loggedInSessions.remove(oldSession);

		VaadinSession newSession = VaadinSession.getCurrent();
		sessionToUser.put(newSession, user);
		loggedInSessions.add(newSession);

		return true;
	}

	public boolean addBot(String botName) {
		if (botNameToBot.containsKey(botName)) {
			return false;
		}

		User bot = new User(botName);
		bot.setReady(true);

		botNameToBot.put(botName, bot);
		return true;
	}

	public void removeBot(User bot) {
		botNameToBot.remove(bot.getName());
	}

	public void logInCurrentSession() {
		VaadinSession session = VaadinSession.getCurrent();
		loggedInSessions.add(session);
	}

	public boolean isCurrentSessionLoggedIn() {
		VaadinSession session = VaadinSession.getCurrent();
		return loggedInSessions.contains(session);
	}

	public User getCurrentUser() {
		VaadinSession session = VaadinSession.getCurrent();
		return sessionToUser.get(session);
	}

	public List<User> getPlayingUsers() {
		return getAllUsers().stream()
				.filter(user -> !user.isLoggedOut())
				.collect(Collectors.toList());
	}

	public List<User> getAllUsers() {
		return getUserStream()
				.sorted(Comparator.comparing(User::getName))
				.toList();
	}

	public boolean userNameExist(String userName) {
		return getUserStream().anyMatch(user -> user.getName().equals(userName));
	}

	public boolean arePlayingUsersReady() {
		return getPlayingUsers().stream().allMatch(User::isReady);
	}

	public long getReadyUsersCount() {
		return getPlayingUsers().stream()
				.filter(User::isReady)
				.count();
	}

	private Stream<User> getUserStream() {
		Stream<User> userStream = sessionToUser.values().stream();
		Stream<User> botStream = botNameToBot.values().stream();

		return Stream.concat(userStream, botStream);
	}
}
