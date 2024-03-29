package com.lafi.cardgame.nazdarbaby.util;

import com.lafi.cardgame.nazdarbaby.layout.VerticalLayoutWithBroadcast;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WebBrowser;

import java.net.URLEncoder;
import java.util.Optional;
import java.util.function.Consumer;

public final class UiUtil {

	private static final String LOCATION_SEPARATOR = "/";

	private UiUtil() {
	}

	public static void createNavigationToTablesView(VerticalLayoutWithBroadcast verticalLayout) {
		NativeLabel redirectLabel = new NativeLabel("Create a new table or join to another one");
		Button redirectButton = new Button(Constant.OK_LABEL);
		redirectButton.addClickListener(e -> verticalLayout.navigateToTablesView());

		HorizontalLayout redirectHorizontalLayout = new HorizontalLayout(redirectLabel, redirectButton);
		redirectHorizontalLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
		verticalLayout.add(redirectHorizontalLayout);
	}

	public static <T extends HasValidation & Focusable<? extends Component>> void invalidateFieldWithFocus(T field, String errorMessage) {
		invalidateField(field, errorMessage);
		field.focus();
	}

	public static <T extends HasValidation> void invalidateField(T field, String errorMessage) {
		field.setInvalid(true);
		field.setErrorMessage(errorMessage);
	}

	public static <T extends HasValidation> void makeFieldValid(T field) {
		field.setInvalid(false);
		field.setErrorMessage(null);
	}

	public static void access(Component component, Command command) {
		makeUIAction(component, ui -> ui.access(command));
	}

	public static void makeUIAction(Optional<UI> uiOptional, Consumer<UI> action) {
		uiOptional.ifPresent(action);
	}

	public static String createLocation(String firstPart, String secondPart) {
		String encodedSecondPart = URLEncoder.encode(secondPart, Constant.CHARSET);
		return firstPart + LOCATION_SEPARATOR + encodedSecondPart;
	}

	public static void focusForNonMobileDevice(Focusable<? extends Component> focusable) {
		if (!isMobileDevice()) {
			focusable.focus();
		}
	}

	private static void makeUIAction(Component component, Consumer<UI> action) {
		makeUIAction(component.getUI(), action);
	}

	private static boolean isMobileDevice() {
		VaadinSession session = VaadinSession.getCurrent();
		WebBrowser browser = session.getBrowser();

		return browser.isAndroid() || browser.isIPhone() || browser.isWindowsPhone();
	}
}
