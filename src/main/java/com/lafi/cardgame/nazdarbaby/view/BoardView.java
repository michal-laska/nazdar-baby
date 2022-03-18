package com.lafi.cardgame.nazdarbaby.view;

import com.lafi.cardgame.nazdarbaby.card.Card;
import com.lafi.cardgame.nazdarbaby.card.CardProvider;
import com.lafi.cardgame.nazdarbaby.card.Color;
import com.lafi.cardgame.nazdarbaby.counter.CountdownCounter;
import com.lafi.cardgame.nazdarbaby.exception.EndGameException;
import com.lafi.cardgame.nazdarbaby.provider.Game;
import com.lafi.cardgame.nazdarbaby.provider.UserProvider;
import com.lafi.cardgame.nazdarbaby.user.User;
import com.lafi.cardgame.nazdarbaby.util.Constant;
import com.lafi.cardgame.nazdarbaby.util.UiUtil;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Route(BoardView.ROUTE_LOCATION)
public class BoardView extends ParameterizedView {

	static final String ROUTE_LOCATION = "board";

	private static final Logger LOGGER = LoggerFactory.getLogger(BoardView.class);

	private static final String NEW_GAME_LABEL = "New game";

	private static final String FONT_WEIGHT_STYLE = "fontWeight";
	private static final String BOLD = "bold";

	private static final String BORDER_STYLE = "border";
	private static final String ONE_PX_SOLID = "1px solid ";
	public static final String BLUE_COLOR = "blue";
	public static final String GREEN_COLOR = "green";

	private static final String NEXT_BUTTON_TEXT = "Next";
	private static final int AUTO_NEXT_DELAY_IN_SECONDS = 5;

	private final List<Label> cardPlaceholderLabels = new ArrayList<>();

	private IntegerField expectedTakesField;
	private boolean autoNext = true;
	private Image preselectedCardImage;

	@Override
	public void receiveBroadcast(String message) {
		if (message == null) {
			access(this::showView);
		} else if (isInteger(message)) {
			access(() -> removeBoldFontWeightStyleFromCardPlaceholderLabel(message));
		} else {
			access(() -> Notification.show(message));
		}
	}

	@Override
	void showView() {
		UserProvider userProvider = table.getUserProvider();
		User currentUser = userProvider.getCurrentUser();
		Game game = table.getGame();

		if (currentUser == null) {
			removeAll();

			Label nonExistentBoardLabel = new Label("Board '" + getTableName() + "' doesn't exist");
			add(nonExistentBoardLabel);

			UiUtil.createNavigationToTablesView(this);
		} else if (game.isGameInProgress()) {
			removeAll();

			addPointsHL();
			HorizontalLayout cardPlaceholdersHL = getAndAddCardPlaceholdersHL();
			addUserCardsHL(cardPlaceholdersHL);
		} else if (currentUser.isLoggedOut()) {
			navigate(TablesView.ROUTE_LOCATION);
		} else {
			String location = UiUtil.createLocation(TableView.ROUTE_LOCATION, getTableName());
			navigate(location);
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

		if (game.isLastUser()) {
			game.resetActiveUser();
		} else {
			game.changeActiveUser();
		}

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
			handleEndOfMatch(cardPlaceholdersHL, autoNextVL);
		} else if (game.isActiveUser()) {
			add(getYourTurnGif());
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

			Card firstCard = cardPlaceholders.get(0);
			if (!firstCard.isPlaceholder()) {
				boolean userHasFirstCardColor = currentUser.hasColor(firstCard.getColor());
				if (userHasFirstCardColor) {
					if (card.getColor() != firstCard.getColor()) {
						showWrongCardNotification(firstCard.getColor());
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
		Checkbox autoNextCheckbox = new Checkbox("Auto " + NEXT_BUTTON_TEXT.toLowerCase(), autoNext);
		autoNextCheckbox.addClickListener(click -> autoNext = autoNextCheckbox.getValue());

		return autoNextCheckbox;
	}

	private Label getInProgressGameTypeLabel() {
		Game game = table.getGame();

		int sumOfExpectedTakes = game.getSumOfExpectedTakes();
		int sumOfCards = game.getMatchUsers().get(0).getCards().size();

		return new Label(sumOfExpectedTakes + "/" + sumOfCards);
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

			Checkbox newGameCheckbox = new Checkbox(user.wantNewGame());
			pointsVL.add(newGameCheckbox);

			boolean isCurrentUser = user.equals(currentUser);

			newGameCheckbox.setEnabled(isCurrentUser);
			newGameCheckbox.setIndeterminate(user.isLoggedOut());

			if (user.isLoggedOut()) {
				newGameCheckbox.setLabel(Constant.LOGOUT_LABEL);
			} else if (user.wantNewGame() || table.isNewGameCountdownRunning()) {
				newGameCheckbox.setLabel(NEW_GAME_LABEL);
			} else {
				String newGameCheckboxLabel = NEW_GAME_LABEL + " / " + Constant.LOGOUT_LABEL;
				newGameCheckbox.setLabel(newGameCheckboxLabel);
			}

			if (isCurrentUser) {
				table.addCountdownCheckbox(newGameCheckbox);
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
					table.startNewGameCountdown(this);
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

			Label userLabel = new Label(user.getName());
			Label pointsLabel = new Label(user.getPoints() + Constant.POINTS_LABEL);
			pointsVL.add(userLabel, pointsLabel);

			if (isCurrentUser) {
				userLabel.getStyle().set(FONT_WEIGHT_STYLE, BOLD);
				pointsLabel.getStyle().set(FONT_WEIGHT_STYLE, BOLD);
			}
		}
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
	}

	private void handleEndOfMatch(HorizontalLayout cardPlaceholdersHL, VerticalLayout autoNextVL) {
		Game game = table.getGame();

		int winnerIndex = game.getWinnerIndex();
		VerticalLayout cardPlaceholderVL = (VerticalLayout) cardPlaceholdersHL.getComponentAt(winnerIndex);
		cardPlaceholderVL.getStyle().set(BORDER_STYLE, ONE_PX_SOLID + BLUE_COLOR);

		Button nextButton = new Button(NEXT_BUTTON_TEXT);
		autoNextVL.add(nextButton);

		Image yourTurnGif = getYourTurnGif();

		if (autoNext) {
			runAutoNextTimer(nextButton);
		} else {
			add(yourTurnGif);
		}

		cardPlaceholderLabels.forEach(label -> label.getStyle().set(FONT_WEIGHT_STYLE, BOLD));

		nextButton.addClickListener(click -> nextButtonClickAction(autoNextVL, nextButton, yourTurnGif));
	}

	private void runAutoNextTimer(Button nextButton) {
		Game game = table.getGame();
		long countdownInSeconds = game.isEndOfSet() ? 2 * AUTO_NEXT_DELAY_IN_SECONDS : AUTO_NEXT_DELAY_IN_SECONDS;

		CountdownCounter countdownCounter = new CountdownCounter(this, countdownInSeconds) {

			@Override
			public void eachRun() {
				String nextButtonText = NEXT_BUTTON_TEXT + getFormattedCountdown();
				access(nextButton, () -> nextButton.setText(nextButtonText));
			}

			@Override
			public void finalRun() {
				if (autoNext) {
					access(nextButton, nextButton::click);
				} else {
					access(nextButton, () -> nextButton.setText(NEXT_BUTTON_TEXT));
				}
			}
		};
		countdownCounter.start();
	}

	private void nextButtonClickAction(VerticalLayout autoNextVL, Button nextButton, Image yourTurnGif) {
		//noinspection SynchronizeOnNonFinalField
		synchronized (table) {
			autoNextVL.remove(nextButton);
			remove(yourTurnGif);

			Game game = table.getGame();
			if (table.increaseAndCheckNextButtonClickCounter()) {
				try {
					game.changeActiveUser();
				} catch (EndGameException e) {
					game.startNewGame();
				}

				broadcast();
			} else {
				UserProvider userProvider = table.getUserProvider();
				User currentUser = userProvider.getCurrentUser();

				List<User> matchUsers = game.getMatchUsers();
				int indexOfCurrentUser = matchUsers.indexOf(currentUser);

				String indexOfCurrentUserStr = String.valueOf(indexOfCurrentUser);
				broadcast(indexOfCurrentUserStr);
			}
		}
	}

	private Image getYourTurnGif() {
		Image yourTurnGif = new Image();

		yourTurnGif.setSrc("gif/your_turn.gif");
		yourTurnGif.setHeight(Constant.IMAGE_HEIGHT);

		return yourTurnGif;
	}

	private boolean isInteger(String maybeInt) {
		try {
			Integer.parseInt(maybeInt);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private void removeBoldFontWeightStyleFromCardPlaceholderLabel(String indexStr) {
		try {
			int index = Integer.parseInt(indexStr);

			Label cardPlaceholderLabel = cardPlaceholderLabels.get(index);
			cardPlaceholderLabel.getStyle().remove(FONT_WEIGHT_STYLE);
		} catch (IndexOutOfBoundsException e) {
			LOGGER.warn("Known race condition problem - not a big deal");
		}
	}
}
