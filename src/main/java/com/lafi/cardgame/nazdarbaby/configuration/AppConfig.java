package com.lafi.cardgame.nazdarbaby.configuration;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.provider.TableProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

	@Bean
	public Broadcaster broadcaster() {
		return new Broadcaster();
	}

	@Bean
	public TableProvider tableProvider() {
		return new TableProvider();
	}
}
