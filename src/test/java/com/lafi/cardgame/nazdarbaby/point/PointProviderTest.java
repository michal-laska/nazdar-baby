package com.lafi.cardgame.nazdarbaby.point;

import static com.lafi.cardgame.nazdarbaby.provider.Table.MAXIMUM_USERS;
import static com.lafi.cardgame.nazdarbaby.provider.Table.MINIMUM_USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PointProviderTest {

	private final PointProvider pointProvider = new PointProvider();

	@Nested
	class GetLoserPointsTest {

		@Test
		void losersPayExactlyWhatTheWinnersGain() {
			for (int users = MINIMUM_USERS; users <= MAXIMUM_USERS; users++) {
				for (int winners = 1; winners < users; winners++) {
					float gained = winners * pointProvider.getWinnerPoints(users, winners);
					float paid = (users - winners) * pointProvider.getLoserPoints(users, winners);

					assertThat(gained + paid).as("%d users, %d winners", users, winners).isCloseTo(0f, within(1e-4f));
				}
			}
		}

		@Test
		void losersSplitTheWinnersPoints() {
			// 3 users, 1 winner with 10 points: the 2 losers pay 5 each
			assertThat(pointProvider.getLoserPoints(3, 1)).isEqualTo(-5f);
			// 7 users, 1 winner with 15 points: the 6 losers pay 2.5 each
			assertThat(pointProvider.getLoserPoints(7, 1)).isEqualTo(-2.5f);
		}

		@Test
		void soleLoserPaysForEveryWinner() {
			// 3 users, 2 winners with 5 points each
			assertThat(pointProvider.getLoserPoints(3, 2)).isEqualTo(-10f);
		}

		@Test
		void nobodyWins_losersPayNothing() {
			for (int users = MINIMUM_USERS; users <= MAXIMUM_USERS; users++) {
				assertThat(pointProvider.getLoserPoints(users, 0)).as("%d users", users).isZero();
			}
		}

		@Test
		void everyoneWinning_isRejected() {
			assertThatThrownBy(() -> pointProvider.getLoserPoints(3, 3))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("numberOfWinners = 3");
		}

		@Test
		void unsupportedNumberOfUsers_isRejected() {
			assertThatThrownBy(() -> pointProvider.getLoserPoints(MAXIMUM_USERS + 1, 1))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("numberOfUsers = " + (MAXIMUM_USERS + 1));
		}
	}
}
