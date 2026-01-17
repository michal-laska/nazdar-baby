package com.lafi.cardgame.nazdarbaby;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@Push
@SpringBootApplication
public class Application extends SpringBootServletInitializer implements AppShellConfigurator {

	static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
