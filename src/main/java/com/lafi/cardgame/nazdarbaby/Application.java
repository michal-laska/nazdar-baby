package com.lafi.cardgame.nazdarbaby;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;

import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Push
@SpringBootApplication
@StyleSheet(Lumo.STYLESHEET)
public class Application implements AppShellConfigurator {

	static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
