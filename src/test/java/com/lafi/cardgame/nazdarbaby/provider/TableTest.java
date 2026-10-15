package com.lafi.cardgame.nazdarbaby.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.lafi.cardgame.nazdarbaby.broadcast.Broadcaster;
import com.lafi.cardgame.nazdarbaby.countdown.CountdownService;
import com.lafi.cardgame.nazdarbaby.point.PointProvider;
import com.lafi.cardgame.nazdarbaby.view.BoardView;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TableTest {

	@Nested
	class BotMoveTest {

		@Test
		void appliedBotMove_updatesTheTablesBoards() {
			Broadcaster broadcaster = mock(Broadcaster.class);
			Table table = new Table("lounge", broadcaster, mock(CountdownService.class), new PointProvider());
			for (String botName : new String[]{"bot1", "bot2", "bot3"}) {
				table.getUserProvider().addBot(botName);
			}

			table.getGame().setGameInProgress(true);

			verify(broadcaster, timeout(30_000)).broadcast(BoardView.class, "lounge");
		}
	}

	@Nested
	class BotMoveExecutorTest {

		@Test
		void botMove_runsOnItsOwnPlatformThread() throws Exception {
			CompletableFuture<Thread> botMoveThread = new CompletableFuture<>();

			Table.botMoveExecutor("lounge").execute(() -> botMoveThread.complete(Thread.currentThread()));

			Thread thread = botMoveThread.get(10, TimeUnit.SECONDS);
			assertThat(thread.isVirtual()).isFalse();
			assertThat(thread.isDaemon()).isTrue();
			assertThat(thread.getName()).isEqualTo("bot-move-lounge");
			assertThat(thread).isNotSameAs(Thread.currentThread());
		}
	}
}
