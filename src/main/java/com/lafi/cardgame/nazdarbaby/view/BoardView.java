package com.lafi.cardgame.nazdarbaby.view;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.countdown.CountdownService;
import com.lafi.cardgame.nazdarbaby.countdown.CountdownTask;
import com.lafi.cardgame.nazdarbaby.exception.EndGameException;
import com.lafi.cardgame.nazdarbaby.provider.Game;
import com.lafi.cardgame.nazdarbaby.provider.TableProvider;
import com.lafi.cardgame.nazdarbaby.provider.UserProvider;
import com.lafi.cardgame.nazdarbaby.user.User;
import com.lafi.cardgame.nazdarbaby.util.Constant;
import com.lafi.cardgame.nazdarbaby.util.UiUtil;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.List;

@Route(BoardView.ROUTE_LOCATION)
public class BoardView extends ParameterizedView {

	static final String ROUTE_LOCATION = "board";

	private static final String NEW_GAME_LABEL = "New game";

	private static final String FONT_WEIGHT_STYLE = "fontWeight";
	private static final String BOLD = "bold";

	private static final String BORDER_STYLE = "border";
	private static final String ONE_PX_SOLID = "1px solid ";
	public static final String BLUE_COLOR = "blue";
	public static final String GREEN_COLOR = "green";

	private static final String NEXT_MATCH_BUTTON_TEXT = "Next match";
	private static final int AUTO_NEXT_DELAY_IN_SECONDS = 5;

	private final List<Label> cardPlaceholderLabels = new ArrayList<>();

	private IntegerField expectedTakesField;
	private boolean autoNextMatch = true;
	private Image preselectedCardImage;

	public BoardView(Broadcaster broadcaster, TableProvider tableProvider, CountdownService countdownService) {
		super(broadcaster, tableProvider, countdownService);
	}

	@Override
	public void receiveBroadcast(String message) {
		if (message == null) {
			access(this::showView);
		} else {
			access(() -> Notification.show(message));
		}
	}

	@Override
	void showView() {
		UserProvider userProvider = table.getUserProvider();
		User currentUser = userProvider.getCurrentUser();
		Game game = table.getGame();

		if (game.isGameInProgress()) {
			removeAll();

			addPointsHL();
			HorizontalLayout cardPlaceholdersHL = getAndAddCardPlaceholdersHL();

			List<User> matchUsers = game.getMatchUsers();

			if (matchUsers.contains(currentUser)) {
				addUserCardsHL(cardPlaceholdersHL);
			} else {
				List<Card> cards = matchUsers.get(0).getCards();
				long round = cards.size() - getNumberOfCardsLeft(cards);

				Label roundLabel = new Label("Round " + round + "/" + cards.size());
				add(roundLabel);

				if (game.setCanStart()) {
					add(getGameTypeLabel());
				}
			}
		} else if (currentUser == null) {
			removeAll();

			Label nonExistentBoardLabel = new Label("Board '" + getTableName() + "' doesn't exist");
			add(nonExistentBoardLabel);

			UiUtil.createNavigationToTablesView(this);
		} else if (currentUser.isLoggedOut()) {
			navigateToTablesView();
		} else {
			navigateToTableName(TableView.ROUTE_LOCATION);
		}
	}

	private void initExpectedTakesField() {
		UserProvider userProvider = table.getUserProvider();
		User currentUser = userProvider.getCurrentUser();
		if (currentUser.getExpectedTakes() != null) {
			return;
		}

		Game game = table.getGame();
		if (expectedTakesField == null) {
			expectedTakesField = new IntegerField();
			expectedTakesField.setPlaceholder("Your guess");
			expectedTakesField.addKeyPressListener(Key.ENTER, event -> expectedTakesFieldEnterAction());

			if (game.isActiveUser()) {
				expectedTakesField.focus();
			} else {
				expectedTakesField.addFocusListener(focusEvent -> notYourTurnAction());
				expectedTakesField.addBlurListener(blurEvent -> {
					if (expectedTakesField == null) {
						return;
					}

					if (expectedTakesField.getValue() == null) {
						makeExpectedTakesFieldValid();
					} else {
						notYourTurnAction();
					}
				});

				// to set value immediately (without pressing ENTER or losing focus)
				expectedTakesField.addKeyDownListener(press -> {
					if (expectedTakesField == null) {
						return;
					}

					expectedTakesField.blur();
					expectedTakesField.focus();
				});
			}
		} else if (expectedTakesField.getValue() != null) {
			expectedTakesFieldEnterAction();
		} else if (game.isActiveUser()) {
			makeExpectedTakesFieldValid();
			expectedTakesField.focus();
		}
	}

	private void notYourTurnAction() {
		if (expectedTakesField == null) {
			return;
		}

		Game game = table.getGame();
		if (!game.isActiveUser()) {
			UiUtil.invalidateField(expectedTakesField, "Warning: Not your turn yet");
		}
	}

	private void makeExpectedTakesFieldValid() {
		if (expectedTakesField != null) {
			UiUtil.makeFieldValid(expectedTakesField);
		}
	}

	private void expectedTakesFieldEnterAction() {
		Game game = table.getGame();

		if (!game.isActiveUser()) {
			return;
		}

		Integer expectedTakes = expectedTakesField.getValue();
		if (expectedTakes == null) {
			UiUtil.invalidateField(expectedTakesField, "Enter some number");
			return;
		}

		UserProvider userProvider = table.getUserProvider();
		User currentUser = userProvider.getCurrentUser();

		int maxExpectedTakes = currentUser.getCards().size();
		if (expectedTakes < 0 || expectedTakes > maxExpectedTakes) {
			UiUtil.invalidateFieldWithFocus(expectedTakesField, "Number has to be in <0, " + maxExpectedTakes + '>');
			return;
		}
		if (game.isLastUserWithInvalidExpectedTakes(expectedTakes)) {
			UiUtil.invalidateFieldWithFocus(expectedTakesField, "Cannot set " + expectedTakes);
			return;
		}

		currentUser.setExpectedTakes(expectedTakes);
		game.afterActiveUserSetExpectedTakes();
		expectedTakesField = null;

		broadcast();
	}

	private void addUserCardsHL(HorizontalLayout cardPlaceholdersHL) {
		HorizontalLayout userCardsHL = new HorizontalLayout();
		userCardsHL.setDefaultVerticalComponentAlignment(Alignment.CENTER);
		add(userCardsHL);

		addCurrentUserCardsTo(userCardsHL);

		VerticalLayout autoNextVL = new VerticalLayout();
		userCardsHL.add(autoNextVL);

		Game game = table.getGame();

		if (game.setCanStart()) {
			autoNextVL.add(getGameTypeLabel());
		} else {
			autoNextVL.add(getInProgressGameTypeLabel());

			initExpectedTakesField();
			if (expectedTakesField != null) {
				Button okButton = new Button(Constant.OK_LABEL);
				okButton.addClickListener(click -> expectedTakesFieldEnterAction());

				HorizontalLayout expectedTakesHL = new HorizontalLayout(expectedTakesField, okButton);
				autoNextVL.add(expectedTakesHL);
			}
		}
		autoNextVL.add(getAutoNextCheckbox());

		if (game.isEndOfMatch()) {
			Button nextMatchButton = handleEndOfMatch(cardPlaceholdersHL);
			autoNextVL.add(nextMatchButton);
		} else if (game.isActiveUser()) {
			addYourTurnGif();
		}
	}

	private void addCurrentUserCardsTo(HorizontalLayout userCardsHL) {
		UserProvider userProvider = table.getUserProvider();
		User currentUser = userProvider.getCurrentUser();
		List<Card> currentUserCards = currentUser.getCards();

		long numberOfCardsLeft = getNumberOfCardsLeft(currentUserCards);

		for (Card card : currentUserCards) {
			Image image = card.getImage();
			userCardsHL.add(image);

			if (card.isPlaceholder()) {
				continue;
			}

			if (numberOfCardsLeft == 1L) {
				cardImageClickAction(image, card, currentUserCards);
			}

			if (preselectedCardImage != null && preselectedCardImage.getSrc().equals(image.getSrc())) {
				cardImageClickAction(image, card, currentUserCards);
			}

			image.addClickListener(click -> cardImageClickAction(image, card, currentUserCards));
		}
	}

	private long getNumberOfCardsLeft(List<Card> cards) {
		return cards.stream()
				.filter(card -> !card.isPlaceholder())
				.count();
	}

	private void cardImageClickAction(Image image, Card card, List<Card> cards) {
		UserProvider userProvider = table.getUserProvider();
		User currentUser = userProvider.getCurrentUser();

		Game game = table.getGame();
		if (game.isActiveUser() && game.setCanStart()) {
			preselectedCardImage = null;

			List<Card> cardPlaceholders = game.getCardPlaceholders();

			Card leadingCard = cardPlaceholders.get(0);
			if (!leadingCard.isPlaceholder()) {
				boolean userHasLeadingCardColor = currentUser.hasColor(leadingCard.getColor());
				if (userHasLeadingCardColor) {
					if (card.getColor() != leadingCard.getColor()) {
						showWrongCardNotification(leadingCard.getColor());
						return;
					}
				} else {
					boolean userHasHearts = currentUser.hasColor(Color.HEARTS);
					if (userHasHearts && card.getColor() != Color.HEARTS) {
						showWrongCardNotification(Color.HEARTS);
						return;
					}
				}
			}

			List<User> matchUsers = game.getMatchUsers();
			int matchUserIndex = matchUsers.indexOf(currentUser);
			cardPlaceholders.set(matchUserIndex, card);

			int cardIndex = cards.indexOf(card);
			cards.set(cardIndex, CardProvider.CARD_PLACEHOLDER);

			game.changeActiveUser();

			broadcast();
		} else if (currentUser.getExpectedTakes() != null) {
			if (preselectedCardImage != null) {
				preselectedCardImage.getStyle().remove(BORDER_STYLE);
			}

			//noinspection ObjectEquality
			if (preselectedCardImage == image) {
				preselectedCardImage = null;
			} else {
				preselectedCardImage = image;
				preselectedCardImage.getStyle().set(BORDER_STYLE, ONE_PX_SOLID + BLUE_COLOR);
			}
		}
	}

	private void showWrongCardNotification(Color color) {
		Notification.show("Cannot play this card - play some " + color);
	}

	private Label getGameTypeLabel() {
		Game game = table.getGame();
		int matchCharacter = game.getMatchCharacter();

		String typeOfGameText = matchCharacter < 0 ? "SMALL GAME (" : "BIG GAME (+";
		typeOfGameText += matchCharacter + ")";

		return new Label(typeOfGameText);
	}

	private Checkbox getAutoNextCheckbox() {
		Checkbox autoNextMatchCheckbox = new Checkbox("Auto " + NEXT_MATCH_BUTTON_TEXT.toLowerCase(), autoNextMatch);

		autoNextMatchCheckbox.addClickListener(click -> {
			autoNextMatch = autoNextMatchCheckbox.getValue();

			UserProvider userProvider = table.getUserProvider();
			User currentUser = userProvider.getCurrentUser();
			currentUser.setReady(false);

			broadcast();
		});

		return autoNextMatchCheckbox;
	}

	private Label getInProgressGameTypeLabel() {
		Game game = table.getGame();

		int sumOfExpectedTakes = game.getSumOfExpectedTakes();
		int numberOfCards = game.getMatchUsers().get(0).getCards().size();

		return new Label(sumOfExpectedTakes + "/" + numberOfCards);
	}

	private void addPointsHL() {
		HorizontalLayout pointsHL = new HorizontalLayout();
		pointsHL.setWidthFull();
		add(pointsHL);

		Game game = table.getGame();
		UserProvider userProvider = table.getUserProvider();
		User currentUser = userProvider.getCurrentUser();

		for (User user : game.getSetUsers()) {
			VerticalLayout pointsVL = new VerticalLayout();
			pointsVL.setDefaultHorizontalComponentAlignment(Alignment.CENTER);
			pointsHL.add(pointsVL);

			Boolean terminator = user.wasTerminator();
			Style style = pointsVL.getStyle();
			if (terminator == null) {
				style.set(BORDER_STYLE, ONE_PX_SOLID + Constant.RED_COLOR);
			} else if (terminator) {
				style.set(BORDER_STYLE, ONE_PX_SOLID + GREEN_COLOR);
			}

			if (!user.isBot()) {
				Checkbox newGameCheckbox = createNewGameCheckbox(user);
				pointsVL.add(newGameCheckbox);
			}

			Label userLabel = new Label(user.getName());
			Label pointsLabel = new Label(user.getPoints() + Constant.POINTS_LABEL);
			pointsVL.add(userLabel, pointsLabel);

			boolean isCurrentUser = user.equals(currentUser);
			if (isCurrentUser) {
				userLabel.getStyle().set(FONT_WEIGHT_STYLE, BOLD);
				pointsLabel.getStyle().set(FONT_WEIGHT_STYLE, BOLD);
			}
		}
	}

	private Checkbox createNewGameCheckbox(User user) {
		UserProvider userProvider = table.getUserProvider();
		User currentUser = userProvider.getCurrentUser();
		boolean isCurrentUser = user.equals(currentUser);

		Game game = table.getGame();

		Checkbox newGameCheckbox = new Checkbox(user.wantNewGame());
		newGameCheckbox.setIndeterminate(user.isLoggedOut());

		boolean enabled = isCurrentUser;
		if (autoNextMatch) {
			enabled &= currentUser.isReady() || !game.isEndOfMatch();
		}
		newGameCheckbox.setEnabled(enabled);

		if (user.isLoggedOut()) {
			newGameCheckbox.setLabel(Constant.LOGOUT_LABEL);
		} else if (user.wantNewGame()) {
			newGameCheckbox.setLabel(NEW_GAME_LABEL);
		} else {
			String newGameCheckboxLabel = NEW_GAME_LABEL + " / " + Constant.LOGOUT_LABEL;
			newGameCheckbox.setLabel(newGameCheckboxLabel);
		}

		boolean newGameOrLogout = game.getSetUsers().stream().anyMatch(setUser -> setUser.wantNewGame() || setUser.isLoggedOut());
		if (isCurrentUser && newGameOrLogout) {
			table.addCountdownCheckbox(this, newGameCheckbox);
		}

		newGameCheckbox.addClickListener(click -> {
			if (user.isLoggedOut()) {
				newGameCheckbox.setValue(false);
				newGameCheckbox.setIndeterminate(false);
			} else if (user.wantNewGame()) {
				newGameCheckbox.setValue(true);
				newGameCheckbox.setIndeterminate(true);
			}

			if (newGameCheckbox.isIndeterminate()) {
				user.setLoggedOut(true);
			} else if (Boolean.TRUE.equals(newGameCheckbox.getValue())) {
				user.setNewGame(true);
			} else {
				user.resetAction();
				table.stopNewGameCountdown();
			}

			if (game.usersWantNewGame()) {
				game.setGameInProgress(false);
				table.stopNewGameCountdown();

				// some players can be in "waiting room"
				broadcast(TableView.class, null);
			}

			broadcast();
		});

		return newGameCheckbox;
	}

	private HorizontalLayout getAndAddCardPlaceholdersHL() {
		HorizontalLayout cardPlaceholdersHL = new HorizontalLayout();
		cardPlaceholdersHL.setWidthFull();
		add(cardPlaceholdersHL);

		cardPlaceholderLabels.clear();

		Game game = table.getGame();
		List<Card> cardPlaceholders = game.getCardPlaceholders();
		List<User> matchUsers = game.getMatchUsers();
		for (int i = 0; i < cardPlaceholders.size(); ++i) {
			User user = matchUsers.get(i);
			Label cardPlaceholderLabel = new Label(user.toString());
			Card cardPlaceholder = cardPlaceholders.get(i);

			VerticalLayout cardPlaceholderVL = new VerticalLayout(cardPlaceholderLabel, cardPlaceholder.getImage());
			cardPlaceholderVL.setDefaultHorizontalComponentAlignment(Alignment.CENTER);
			cardPlaceholdersHL.add(cardPlaceholderVL);

			cardPlaceholderLabels.add(cardPlaceholderLabel);
		}

		colorCardPlaceholderLabels();

		return cardPlaceholdersHL;
	}

	private void colorCardPlaceholderLabels() {
		Game game = table.getGame();
		List<User> matchUsers = game.getMatchUsers();

		List<Card> cardPlaceholders = game.getCardPlaceholders();
		boolean isEndOfMatch = game.isEndOfMatch();

		for (int i = 0; i < matchUsers.size(); ++i) {
			User matchUser = matchUsers.get(i);

			Integer expectedTakes = matchUser.getExpectedTakes();
			int actualTakes = matchUser.getActualTakes();

			if (expectedTakes == null) {
				return;
			}

			Label cardPlaceholderLabel = cardPlaceholderLabels.get(i);
			Style style = cardPlaceholderLabel.getStyle();

			if (actualTakes == expectedTakes) {
				style.set(Constant.COLOR_STYLE, GREEN_COLOR);
			} else {
				List<Card> matchUserCards = matchUser.getCards();
				long numberOfCardsLeft = getNumberOfCardsLeft(matchUserCards);

				if (!isEndOfMatch && cardPlaceholders.get(i) != CardProvider.CARD_PLACEHOLDER) {
					++numberOfCardsLeft;
				}

				if (actualTakes > expectedTakes || actualTakes + numberOfCardsLeft < expectedTakes || game.isEndOfSet()) {
					style.set(Constant.COLOR_STYLE, Constant.RED_COLOR);
				}
			}
		}

		if (everybodyLost()) {
			game.setEverybodyLost(true);
		}
	}

	private Button handleEndOfMatch(HorizontalLayout cardPlaceholdersHL) {
		Game game = table.getGame();

		int winnerIndex = game.getWinnerIndex();
		VerticalLayout cardPlaceholderVL = (VerticalLayout) cardPlaceholdersHL.getComponentAt(winnerIndex);
		cardPlaceholderVL.getStyle().set(BORDER_STYLE, ONE_PX_SOLID + BLUE_COLOR);

		Button nextMatchButton = new Button();

		if (everybodyLost() && !game.isEndOfSet()) {
			nextMatchButton.setText("Next set - everybody lost");
		} else {
			nextMatchButton.setText(NEXT_MATCH_BUTTON_TEXT);
		}

		UserProvider userProvider = table.getUserProvider();
		User currentUser = userProvider.getCurrentUser();

		if (currentUser.isReady()) {
			disableNextMatchButton(nextMatchButton);
		} else if (autoNextMatch && NEXT_MATCH_BUTTON_TEXT.equals(nextMatchButton.getText())) {
			runAutoNextTimer(nextMatchButton);
		} else {
			addYourTurnGif();
		}

		List<User> matchUsers = game.getMatchUsers();
		for (int i = 0; i < matchUsers.size(); ++i) {
			User matchUser = matchUsers.get(i);
			if (!matchUser.isReady()) {
				Label cardPlaceholderLabel = cardPlaceholderLabels.get(i);
				cardPlaceholderLabel.getStyle().set(FONT_WEIGHT_STYLE, BOLD);
			}
		}

		nextMatchButton.addClickListener(click -> nextMatchButtonClickAction(nextMatchButton));

		return nextMatchButton;
	}

	private void runAutoNextTimer(Button nextMatchButton) {
		Game game = table.getGame();
		long countdownInSeconds = game.isEndOfSet() ? 2 * AUTO_NEXT_DELAY_IN_SECONDS : AUTO_NEXT_DELAY_IN_SECONDS;

		CountdownTask countdownTask = createCountdownTask(countdownInSeconds, nextMatchButton);
		countdownService.addCountdownTask(countdownTask);
	}

	private boolean everybodyLost() {
		return cardPlaceholderLabels.stream()
				.map(HasStyle::getStyle)
				.allMatch(style -> Constant.RED_COLOR.equals(style.get(Constant.COLOR_STYLE)));
	}

	private CountdownTask createCountdownTask(long countdownInSeconds, Button nextMatchButton) {
		return new CountdownTask(countdownInSeconds, broadcaster, this, true) {

			@Override
			protected void eachRun() {
				String nextMatchButtonText = NEXT_MATCH_BUTTON_TEXT + getFormattedCountdown();
				setNextMatchButtonText(nextMatchButtonText);
			}

			@Override
			protected void finalRun() {
				if (autoNextMatch) {
					access(nextMatchButton, nextMatchButton::click);
				} else {
					setNextMatchButtonText(NEXT_MATCH_BUTTON_TEXT);
				}
			}

			@Override
			protected boolean isCanceled() {
				return nextMatchButton.isDisableOnClick();
			}

			private void setNextMatchButtonText(String text) {
				access(nextMatchButton, () -> nextMatchButton.setText(text));
			}
		};
	}

	private void nextMatchButtonClickAction(Button nextMatchButton) {
		//noinspection SynchronizeOnNonFinalField
		synchronized (table) {
			if (nextMatchButton.isDisableOnClick()) {
				return;
			}
			disableNextMatchButton(nextMatchButton);

			UserProvider userProvider = table.getUserProvider();
			User currentUser = userProvider.getCurrentUser();
			currentUser.setReady(true);

			if (table.allUsersClickedNextMatchButton()) {
				Game game = table.getGame();
				try {
					game.changeActiveUser();
				} catch (EndGameException e) {
					game.startNewGame();
				}
			}

			broadcast();
		}
	}

	private void disableNextMatchButton(Button nextMatchButton) {
		nextMatchButton.setDisableOnClick(true);
		nextMatchButton.click();
	}

	private void addYourTurnGif() {
		Image yourTurnGif = new Image();

		yourTurnGif.setSrc("gif/your_turn.gif");
		yourTurnGif.setHeight(Constant.IMAGE_HEIGHT);

		add(yourTurnGif);
	}
}
