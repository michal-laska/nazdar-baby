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
import java.util.stream.Stream;

public final class UserProvider {

	private final Map<VaadinSession, User> sessionToUser = new HashMap<>();
	private final Set<VaadinSession> loggedInSessions = new HashSet<>();

	private final SessionProvider sessionProvider;

	UserProvider(SessionProvider sessionProvider) {
		this.sessionProvider = sessionProvider;
	}

	public void addUser(String userName) {
		VaadinSession session = sessionProvider.getSession();
		User user = new User(userName);

		sessionToUser.put(session, user);
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
		return getUserStream()
				.sorted(Comparator.comparing(User::getName))
				.collect(Collectors.toList());
	}

	public boolean userNameExist(String userName) {
		return getUserStream()
				.anyMatch(user -> user.getName().equals(userName));
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
		return sessionToUser.values().stream();
	}
}
