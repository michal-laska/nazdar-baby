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

	private static final SessionProvider SESSION_PROVIDER = SessionProvider.INSTANCE;

	private final Map<VaadinSession, User> sessionToUser = new HashMap<>();
	private final Set<VaadinSession> loggedInSessions = new HashSet<>();
	private final Set<String> userNames = new HashSet<>();

	UserProvider() {
	}

	public void addUser(String userName) {
		VaadinSession session = SESSION_PROVIDER.getSession();
		User user = new User(userName);

		sessionToUser.put(session, user);
		userNames.add(userName);
	}

	public void logInCurrentSession() {
		VaadinSession session = SESSION_PROVIDER.getSession();
		loggedInSessions.add(session);
	}

	public boolean isCurrentSessionLoggedIn() {
		VaadinSession session = SESSION_PROVIDER.getSession();
		return loggedInSessions.contains(session);
	}

	public User getCurrentUser() {
		VaadinSession session = SESSION_PROVIDER.getSession();
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
