package com.lafi.cardgame.nazdarbaby.mcts;

import static com.lafi.cardgame.nazdarbaby.provider.Table.MAXIMUM_USERS;
import static com.lafi.cardgame.nazdarbaby.provider.Table.MINIMUM_USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PayoffTest {

	@Nested
	class NormalizedTest {

		@Test
		void soleWinner_scoresOne() {
			for (int players = MINIMUM_USERS; players <= MAXIMUM_USERS; players++) {
				assertThat(Payoff.normalized(players, 1, true)).as("%d players", players).isEqualTo(1.0);
			}
		}

		@Test
		void soleLoser_scoresZero() {
			for (int players = MINIMUM_USERS; players <= MAXIMUM_USERS; players++) {
				assertThat(Payoff.normalized(players, players - 1, false)).as("%d players", players).isEqualTo(0.0);
			}
		}

		@Test
		void nobodyWins_scoresHalf() {
			for (int players = MINIMUM_USERS; players <= MAXIMUM_USERS; players++) {
				assertThat(Payoff.normalized(players, 0, false)).as("%d players", players).isEqualTo(0.5);
			}
		}

		@Test
		void scalesTheSetPointsIntoTheSoleWinnerRange() {
			// 3 players: a sole winner gets 10, so points map from [-10, 10] onto [0, 1]
			assertThat(Payoff.normalized(3, 2, true)).isEqualTo(0.75); // +5
			assertThat(Payoff.normalized(3, 1, false)).isEqualTo(0.25); // -5
		}

		@Test
		void winner_scoresLessTheMorePlayersShareTheWin() {
			for (int players = MINIMUM_USERS; players <= MAXIMUM_USERS; players++) {
				for (int winners = 1; winners < players - 1; winners++) {
					assertThat(Payoff.normalized(players, winners, true))
							.as("%d players, %d winners", players, winners)
							.isGreaterThan(Payoff.normalized(players, winners + 1, true));
				}
			}
		}

		@Test
		void loser_scoresLessTheMorePlayersWin() {
			for (int players = MINIMUM_USERS; players <= MAXIMUM_USERS; players++) {
				for (int winners = 1; winners < players - 1; winners++) {
					assertThat(Payoff.normalized(players, winners, false))
							.as("%d players, %d winners", players, winners)
							.isGreaterThan(Payoff.normalized(players, winners + 1, false));
				}
			}
		}

		@Test
		void losingWhileSomeoneWins_scoresBelowNobodyWinning() {
			for (int players = MINIMUM_USERS; players <= MAXIMUM_USERS; players++) {
				assertThat(Payoff.normalized(players, 1, false)).as("%d players", players).isLessThan(0.5);
			}
		}

		@Test
		void everyoneWinning_scoresAsIfOnePlayerLost() {
			for (int players = MINIMUM_USERS; players <= MAXIMUM_USERS; players++) {
				assertThat(Payoff.normalized(players, players, true))
						.as("%d players", players)
						.isEqualTo(Payoff.normalized(players, players - 1, true));
			}
		}

		@Test
		void staysWithinZeroAndOne() {
			for (int players = MINIMUM_USERS; players <= MAXIMUM_USERS; players++) {
				for (int winners = 0; winners < players; winners++) {
					assertThat(Payoff.normalized(players, winners, false))
							.as("%d players, %d winners, lost", players, winners)
							.isBetween(0.0, 1.0);
				}
				for (int winners = 1; winners <= players; winners++) {
					assertThat(Payoff.normalized(players, winners, true))
							.as("%d players, %d winners, won", players, winners)
							.isBetween(0.0, 1.0);
				}
			}
		}

		@Test
		void unsupportedNumberOfPlayers_isRejected() {
			assertThatThrownBy(() -> Payoff.normalized(MINIMUM_USERS - 1, 1, true))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> Payoff.normalized(MAXIMUM_USERS + 1, 1, true))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void winningPlayerWithNoWinners_isRejected() {
			assertThatThrownBy(() -> Payoff.normalized(3, 0, true))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("playerWon = true with winCount = 0");
		}

		@Test
		void losingPlayerWhileEveryoneWins_isRejected() {
			assertThatThrownBy(() -> Payoff.normalized(3, 3, false))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("playerWon = false with winCount = 3");
		}

		@Test
		void moreWinnersThanPlayers_isRejected() {
			assertThatThrownBy(() -> Payoff.normalized(3, 4, true))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("winCount = 4");
		}

		@Test
		void negativeWinCount_isRejected() {
			assertThatThrownBy(() -> Payoff.normalized(3, -1, false))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("numberOfWinners = -1");
		}
	}
}
