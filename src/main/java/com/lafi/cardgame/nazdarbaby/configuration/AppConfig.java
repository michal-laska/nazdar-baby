package com.lafi.cardgame.nazdarbaby.configuration;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.countdown.CountdownService;
import com.lafi.cardgame.nazdarbaby.points.PointProvider;
import com.lafi.cardgame.nazdarbaby.provider.TableProvider;
import com.lafi.cardgame.nazdarbaby.session.SessionProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

	@Bean
	public Broadcaster broadcaster() {
		return new Broadcaster();
	}

	@Bean
	public CountdownService countdownService() {
		return new CountdownService();
	}

	@Bean
	public TableProvider tableProvider() {
		SessionProvider sessionProvider = new SessionProvider();
		PointProvider pointProvider = new PointProvider();

		return new TableProvider(sessionProvider, pointProvider);
	}
}
