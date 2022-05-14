package com.lafi.cardgame.nazdarbaby;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
@Push
@PWA(name = Application.APP_NAME, shortName = Application.APP_NAME)
public class Application extends SpringBootServletInitializer implements AppShellConfigurator {

	public static final String APP_NAME = "Nazdar baby";

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
