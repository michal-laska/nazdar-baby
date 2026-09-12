package com.lafi.cardgame.nazdarbaby.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import com.vaadin.flow.component.page.Page;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UiUtilTest {

	@Mock
	private UI ui;
	@Mock
	private Page page;
	@Mock
	private ExtendedClientDetails clientDetails;

	private MockedStatic<UI> uiStatic;

	@BeforeEach
	void setUp() {
		uiStatic = mockStatic(UI.class);
		uiStatic.when(UI::getCurrent).thenReturn(ui);

		doReturn(page).when(ui).getPage();
		doReturn(clientDetails).when(page).getExtendedClientDetails();
	}

	@AfterEach
	void tearDown() {
		uiStatic.close();
	}

	private void givenTouchDevice(boolean touchDevice) {
		doReturn(touchDevice).when(clientDetails).isTouchDevice();
	}

	@Nested
	class IsTouchDeviceTest {

		@Test
		void returnsTrueWhenBrowserReportsTouchSupport() {
			givenTouchDevice(true);

			assertThat(UiUtil.isTouchDevice()).isTrue();
		}

		@Test
		void returnsFalseWhenBrowserReportsNoTouchSupport() {
			givenTouchDevice(false);

			assertThat(UiUtil.isTouchDevice()).isFalse();
		}
	}

	@Nested
	class FocusForNonTouchDeviceTest {

		@Mock
		private Focusable<Component> focusable;

		@Test
		void focusesOnNonTouchDevice() {
			givenTouchDevice(false);

			UiUtil.focusForNonTouchDevice(focusable);

			verify(focusable).focus();
		}

		@Test
		void doesNotFocusOnTouchDevice() {
			givenTouchDevice(true);

			UiUtil.focusForNonTouchDevice(focusable);

			verify(focusable, never()).focus();
		}
	}
}
