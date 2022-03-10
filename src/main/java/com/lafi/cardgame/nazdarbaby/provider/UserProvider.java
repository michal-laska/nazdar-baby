package com.lafi.cardgame.nazdarbaby.provider;

import com.lafi.cardgame.nazdarbaby.session.SessionProvider;
import com.lafi.cardgame.nazdarbaby.user.User;
import com.vaadin.flow.server.VaadinSession;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class UserProvider {

	private static final Map<String, UserProvider> TABLE_NAME_TO_USER_PROVIDER = new HashMap<>();

	private final SessionProvider sessionProvider = new SessionProvider();
	private final Map<VaadinSession, User> sessionToUser = new HashMap<>();
	private final Set<VaadinSession> loggedInSessions = new HashSet<>();
	private final Set<String> userNames = new HashSet<>();

	private UserProvider() {
	}

	static synchronized UserProvider get(String tableName) {
		return TABLE_NAME_TO_USER_PROVIDER.computeIfAbsent(tableName, s -> new UserProvider());
	}

	void delete(String tableName) {
		TABLE_NAME_TO_USER_PROVIDER.remove(tableName);
	}

	public User addUser(String userName) {
		VaadinSession session = sessionProvider.getSession();
		User user = new User(userName);

		sessionToUser.put(session, user);
		userNames.add(userName);

		return user;
	}

	public void logInCurrentSession() {
		VaadinSession session = sessionProvider.getSession();
		loggedInSessions.add(session);
	}

	public boolean isCurrentSessionLoggedIn() {
		VaadinSession session = sessionProvider.getSession();
		return loggedInSessions.contains(session);
	}

	public User getCurrentUser() {
		VaadinSession session = sessionProvider.getSession();
		return sessionToUser.get(session);
	}

	public List<User> getPlayingUsers() {
		return getAllUsers().stream()
				.filter(user -> !user.isLoggedOut())
				.collect(Collectors.toList());
	}

	public List<User> getAllUsers() {
		return sessionToUser.values().stream()
				.sorted(Comparator.comparing(User::getName))
				.collect(Collectors.toList());
	}

	public boolean usernameExist(String username) {
		return userNames.contains(username);
	}

	public boolean arePlayingUsersReady() {
		return getPlayingUsers().stream().allMatch(User::isReady);
	}

	public long getReadyUsersCount() {
		return getPlayingUsers().stream()
				.filter(User::isReady)
				.count();
	}
}
