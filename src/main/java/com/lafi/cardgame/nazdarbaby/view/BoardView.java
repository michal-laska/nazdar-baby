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
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.NativeLabel;
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
    public static final String WHITE = "white";

    private static final String NEXT_TRICK_BUTTON_TEXT = "Next trick";
    private static final int AUTO_NEXT_DELAY_IN_SECONDS = 5;

    private final List<NativeLabel> cardPlaceholderLabels = new ArrayList<>();

    private IntegerField expectedTakesField;
    private boolean autoNextTrick = true;
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

            List<User> trickUsers = game.getTrickUsers();

            if (trickUsers.contains(currentUser)) {
                addUserCardsHL(cardPlaceholdersHL);
            } else {
                List<Card> cards = trickUsers.getFirst().getCards();
                long round = cards.size() - getNumberOfCardsLeft(cards);

                NativeLabel roundLabel = new NativeLabel("Round " + round + "/" + cards.size());
                add(roundLabel);

                if (game.setCanStart()) {
                    add(getGameTypeLabel());
                }
            }
        } else if (currentUser == null) {
            removeAll();

            NativeLabel nonExistentBoardLabel = new NativeLabel("Board '" + getTableName() + "' doesn't exist");
            add(nonExistentBoardLabel);

            UiUtil.createNavigationToTablesView(this);
        } else if (currentUser.isLoggedOut()) {
            navigateToTablesView();
        } else {
            navigateToTable(TableView.ROUTE_LOCATION);
        }
    }

    private void initExpectedTakesField() {
        var userProvider = table.getUserProvider();
        var currentUser = userProvider.getCurrentUser();
        if (currentUser.getExpectedTakes() != null) {
            return;
        }

        var game = table.getGame();
        if (expectedTakesField == null) {
            var botSimulator = game.getBotSimulator();
            var guess = botSimulator.guessExpectedTakes();

            expectedTakesField = new IntegerField();
            expectedTakesField.setPlaceholder(String.format("Your guess (%.2f)", guess));
            expectedTakesField.addKeyPressListener(Key.ENTER, keyPressEvent -> expectedTakesFieldEnterAction());

            if (game.isActiveUser()) {
                UiUtil.focusForNonMobileDevice(expectedTakesField);
                addExpectedTakesFieldBlinking();
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
            if (anyNewGameOrLogoutRequest()) {
                var whiteBorder = ONE_PX_SOLID + WHITE;
                expectedTakesField.getStyle().set(BORDER_STYLE, whiteBorder);
            } else {
                makeExpectedTakesFieldValid();
                UiUtil.focusForNonMobileDevice(expectedTakesField);
                addExpectedTakesFieldBlinking();
            }
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
        var userCardsHL = new HorizontalLayout();
        userCardsHL.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        add(userCardsHL);

        var mobileVl = addCurrentUserCardsTo(userCardsHL);

        var autoNextVL = new VerticalLayout();

        if (UiUtil.isMobileDevice()) {
            mobileVl.add(autoNextVL);
        } else {
            userCardsHL.add(autoNextVL);
        }

        var game = table.getGame();

        if (game.setCanStart()) {
            autoNextVL.add(getGameTypeLabel());
        } else {
            autoNextVL.add(getInProgressGameTypeLabel());

            initExpectedTakesField();
            if (expectedTakesField != null) {
                var okButton = new Button(Constant.OK_LABEL);
                okButton.addClickListener(click -> expectedTakesFieldEnterAction());

                var expectedTakesHL = new HorizontalLayout(expectedTakesField, okButton);
                autoNextVL.add(expectedTakesHL);
            }
        }
        autoNextVL.add(getAutoNextCheckbox());

        if (game.isEndOfTrick()) {
            var nextTrickButton = handleEndOfTrick(cardPlaceholdersHL);
            autoNextVL.add(nextTrickButton);
        } else if (game.isActiveUser() && expectedTakesField == null) {
            var userProvider = table.getUserProvider();
            var currentUser = userProvider.getCurrentUser();

            var trickUsers = game.getTrickUsers();
            var currentUserIndex = trickUsers.indexOf(currentUser);

            var cardPlaceholderVL = (VerticalLayout) cardPlaceholdersHL.getComponentAt(currentUserIndex);
            var image = (Image) cardPlaceholderVL.getComponentAt(1);

            addBlinking(image);
        }
    }

    private VerticalLayout addCurrentUserCardsTo(HorizontalLayout userCardsHL) {
        var mobileVl = new VerticalLayout();
        var mobileHl = new HorizontalLayout();
        if (UiUtil.isMobileDevice()) {
            userCardsHL.add(mobileVl);
            mobileVl.add(mobileHl);
        }

        var userProvider = table.getUserProvider();
        var currentUser = userProvider.getCurrentUser();
        var currentUserCards = currentUser.getCards();

        var numberOfCardsLeft = getNumberOfCardsLeft(currentUserCards);

        for (Card card : currentUserCards) {
            var image = card.getImage();

            if (UiUtil.isMobileDevice()) {
                mobileHl.add(image);

                if (mobileHl.getComponentCount() == 4) {
                    mobileHl = new HorizontalLayout();
                    mobileVl.add(mobileHl);
                }
            } else {
                userCardsHL.add(image);
            }

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

        return mobileVl;
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

            Card leadingCard = cardPlaceholders.getFirst();
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

            List<User> trickUsers = game.getTrickUsers();
            int currentUserIndex = trickUsers.indexOf(currentUser);
            cardPlaceholders.set(currentUserIndex, card);

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

    private NativeLabel getGameTypeLabel() {
        Game game = table.getGame();
        int trickCharacter = game.getTrickCharacter();

        String typeOfGameText = trickCharacter < 0 ? "SMALL GAME (" : "BIG GAME (+";
        typeOfGameText += trickCharacter + ")";

        return new NativeLabel(typeOfGameText);
    }

    private Checkbox getAutoNextCheckbox() {
        Checkbox autoNextTrickCheckbox = new Checkbox("Auto " + NEXT_TRICK_BUTTON_TEXT.toLowerCase(), autoNextTrick);

        autoNextTrickCheckbox.addClickListener(click -> {
            autoNextTrick = autoNextTrickCheckbox.getValue();

            UserProvider userProvider = table.getUserProvider();
            User currentUser = userProvider.getCurrentUser();
            currentUser.setReady(false);

            broadcast();
        });

        return autoNextTrickCheckbox;
    }

    private NativeLabel getInProgressGameTypeLabel() {
        Game game = table.getGame();

        int sumOfExpectedTakes = game.getSumOfExpectedTakes();
        int numberOfCards = game.getTrickUsers().getFirst().getCards().size();

        return new NativeLabel(sumOfExpectedTakes + "/" + numberOfCards);
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

            NativeLabel userLabel = new NativeLabel(user.getName());
            NativeLabel pointsLabel = new NativeLabel(user.getPoints() + Constant.POINTS_LABEL);
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

        if (isCurrentUser) {
            if (autoNextTrick) {
                boolean enabled = currentUser.isReady() || !game.isEndOfTrick();
                newGameCheckbox.setEnabled(enabled);
            } else {
                newGameCheckbox.setEnabled(true);
            }
        } else {
            newGameCheckbox.setEnabled(false);
        }

        if (user.isLoggedOut()) {
            newGameCheckbox.setLabel(Constant.LOGOUT_LABEL);
        } else if (user.wantNewGame()) {
            newGameCheckbox.setLabel(NEW_GAME_LABEL);
        } else {
            String newGameCheckboxLabel = NEW_GAME_LABEL + " / " + Constant.LOGOUT_LABEL;
            newGameCheckbox.setLabel(newGameCheckboxLabel);
        }

        if (isCurrentUser && anyNewGameOrLogoutRequest()) {
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
                broadcast(TableView.class);
            }

            broadcast();
        });

        return newGameCheckbox;
    }

    private boolean anyNewGameOrLogoutRequest() {
        Game game = table.getGame();
        return game.getSetUsers().stream().anyMatch(user -> user.wantNewGame() || user.isLoggedOut());
    }

    private HorizontalLayout getAndAddCardPlaceholdersHL() {
        HorizontalLayout cardPlaceholdersHL = new HorizontalLayout();
        cardPlaceholdersHL.setWidthFull();
        add(cardPlaceholdersHL);

        cardPlaceholderLabels.clear();

        Game game = table.getGame();
        List<Card> cardPlaceholders = game.getCardPlaceholders();
        List<User> trickUsers = game.getTrickUsers();
        for (int i = 0; i < cardPlaceholders.size(); ++i) {
            User user = trickUsers.get(i);
            NativeLabel cardPlaceholderLabel = new NativeLabel(user.toString());
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
        List<User> trickUsers = game.getTrickUsers();

        List<Card> cardPlaceholders = game.getCardPlaceholders();
        boolean isEndOfTrick = game.isEndOfTrick();

        for (int i = 0; i < trickUsers.size(); ++i) {
            User trickUser = trickUsers.get(i);

            Integer expectedTakes = trickUser.getExpectedTakes();
            int actualTakes = trickUser.getActualTakes();

            if (expectedTakes == null) {
                return;
            }

            NativeLabel cardPlaceholderLabel = cardPlaceholderLabels.get(i);
            Style style = cardPlaceholderLabel.getStyle();

            if (actualTakes == expectedTakes) {
                style.set(Constant.COLOR_STYLE, GREEN_COLOR);
            } else {
                List<Card> trickUserCards = trickUser.getCards();
                long numberOfCardsLeft = getNumberOfCardsLeft(trickUserCards);

                if (!isEndOfTrick && cardPlaceholders.get(i) != CardProvider.CARD_PLACEHOLDER) {
                    ++numberOfCardsLeft;
                }

                if (actualTakes > expectedTakes || actualTakes + numberOfCardsLeft < expectedTakes || game.isEndOfSet()) {
                    style.set(Constant.COLOR_STYLE, Constant.RED_COLOR);
                }
            }
        }
    }

    private Button handleEndOfTrick(HorizontalLayout cardPlaceholdersHL) {
        var game = table.getGame();

        var winnerIndex = game.getWinningIndex();
        var cardPlaceholderVL = (VerticalLayout) cardPlaceholdersHL.getComponentAt(winnerIndex);
        cardPlaceholderVL.getStyle().set(BORDER_STYLE, ONE_PX_SOLID + BLUE_COLOR);

        var nextTrickButton = new Button(NEXT_TRICK_BUTTON_TEXT);

        var userProvider = table.getUserProvider();
        var currentUser = userProvider.getCurrentUser();

        if (currentUser.isReady()) {
            disableNextTrickButton(nextTrickButton);
        } else if (autoNextTrick && NEXT_TRICK_BUTTON_TEXT.equals(nextTrickButton.getText())) {
            runAutoNextTimer(nextTrickButton);
        } else {
            addBlinking(nextTrickButton);
        }

        var trickUsers = game.getTrickUsers();
        for (var i = 0; i < trickUsers.size(); ++i) {
            var trickUser = trickUsers.get(i);
            if (!trickUser.isReady()) {
                var cardPlaceholderLabel = cardPlaceholderLabels.get(i);
                cardPlaceholderLabel.getStyle().set(FONT_WEIGHT_STYLE, BOLD);
            }
        }

        nextTrickButton.addClickListener(click -> nextTrickButtonClickAction(nextTrickButton));

        return nextTrickButton;
    }

    private void runAutoNextTimer(Button nextTrickButton) {
        Game game = table.getGame();
        long countdownInSeconds = game.isEndOfSet() ? 2 * AUTO_NEXT_DELAY_IN_SECONDS : AUTO_NEXT_DELAY_IN_SECONDS;

        addNextTrickCountdownTask(countdownInSeconds, nextTrickButton);
    }

    private void addNextTrickCountdownTask(long countdownInSeconds, Button nextTrickButton) {
        CountdownTask countdownTask = new CountdownTask(countdownInSeconds, broadcaster, this, true) {

            @Override
            protected void eachRun() {
                String nextTrickButtonText = NEXT_TRICK_BUTTON_TEXT + getFormattedCountdown();
                setNextTrickButtonText(nextTrickButtonText);
            }

            @Override
            protected void finalRun() {
                if (autoNextTrick) {
                    access(nextTrickButton, nextTrickButton::click);
                } else {
                    setNextTrickButtonText(NEXT_TRICK_BUTTON_TEXT);
                }
            }

            @Override
            protected boolean isCanceled() {
                return nextTrickButton.isDisableOnClick();
            }

            private void setNextTrickButtonText(String text) {
                access(nextTrickButton, () -> nextTrickButton.setText(text));
            }
        };

        countdownService.addCountdownTask(countdownTask);
    }

    private void addExpectedTakesFieldBlinking() {
        addBlinking(expectedTakesField);
    }

    private void addBlinking(Component component) {
        Style style = component.getStyle();
        String whiteBorder = ONE_PX_SOLID + WHITE;
        style.set(BORDER_STYLE, whiteBorder);

        CountdownTask countdownTask = new CountdownTask(Long.MAX_VALUE, broadcaster, this) {

            @Override
            protected void eachRun() {
                if (whiteBorder.equals(style.get(BORDER_STYLE))) {
                    access(component, () -> style.set(BORDER_STYLE, ONE_PX_SOLID + BLUE_COLOR));
                } else {
                    access(component, () -> style.set(BORDER_STYLE, whiteBorder));
                }
            }

            @Override
            protected void finalRun() {
            }

            @Override
            protected boolean isCanceled() {
                return anyNewGameOrLogoutRequest();
            }
        };

        countdownService.addCountdownTask(countdownTask);
    }

    private void nextTrickButtonClickAction(Button nextTrickButton) {
        //noinspection SynchronizeOnNonFinalField
        synchronized (table) {
            if (nextTrickButton.isDisableOnClick()) {
                return;
            }
            disableNextTrickButton(nextTrickButton);

            UserProvider userProvider = table.getUserProvider();
            User currentUser = userProvider.getCurrentUser();
            currentUser.setReady(true);

            if (table.allUsersClickedNextTrickButton()) {
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

    private void disableNextTrickButton(Button nextTrickButton) {
        nextTrickButton.setDisableOnClick(true);
        nextTrickButton.click();
    }
}
