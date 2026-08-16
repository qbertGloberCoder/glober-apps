package me.qbert.skywatch.camera.clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimulatedClockTest {

	// A wall clock a test can advance deterministically, so this doesn't need real sleeps.
	private static final class FakeWallClock implements WallClock {
		private long millis;

		FakeWallClock(long startMillis) {
			this.millis = startMillis;
		}

		void advance(long deltaMillis) {
			millis += deltaMillis;
		}

		@Override
		public long currentTimeMillis() {
			return millis;
		}
	}

	@Test
	void startsPlayingAndAdvancesOneToOneWithRealTime() {
		FakeWallClock wallClock = new FakeWallClock(1_000_000L);
		SimulatedClock clock = new SimulatedClock(wallClock);

		assertTrue(clock.isPlaying());
		assertEquals(1_000_000L, clock.getCurrentTimeMillis());

		wallClock.advance(5_000L);
		assertEquals(1_005_000L, clock.getCurrentTimeMillis());
	}

	@Test
	void pauseFreezesTimeDespiteRealTimePassing() {
		FakeWallClock wallClock = new FakeWallClock(1_000_000L);
		SimulatedClock clock = new SimulatedClock(wallClock);

		wallClock.advance(3_000L);
		clock.pause();
		long pausedAt = clock.getCurrentTimeMillis();
		assertEquals(1_003_000L, pausedAt);
		assertFalse(clock.isPlaying());

		// Real time keeps passing while paused - the reported simulated time must not move.
		wallClock.advance(60_000L);
		assertEquals(pausedAt, clock.getCurrentTimeMillis(), "pause must freeze the simulated time");
	}

	@Test
	void resumeContinuesFromThePausedValueNotFromWallClockNow() {
		FakeWallClock wallClock = new FakeWallClock(1_000_000L);
		SimulatedClock clock = new SimulatedClock(wallClock);

		wallClock.advance(3_000L);
		clock.pause();

		// A large real-time gap passes while paused (e.g. the app sat idle).
		wallClock.advance(500_000L);

		clock.resume();
		assertTrue(clock.isPlaying());
		// Resuming should not have jumped to "now" - it continues from 1,003,000.
		assertEquals(1_003_000L, clock.getCurrentTimeMillis());

		wallClock.advance(2_000L);
		assertEquals(1_005_000L, clock.getCurrentTimeMillis());
	}

	@Test
	void setTimeJumpsWithoutChangingPlayState() {
		FakeWallClock wallClock = new FakeWallClock(1_000_000L);
		SimulatedClock clock = new SimulatedClock(wallClock);

		clock.setTime(50_000_000L);
		assertTrue(clock.isPlaying(), "setTime should not implicitly pause");
		assertEquals(50_000_000L, clock.getCurrentTimeMillis());

		wallClock.advance(1_000L);
		assertEquals(50_001_000L, clock.getCurrentTimeMillis());
	}

	@Test
	void setRateChangesHowFastSimulatedTimeAdvances() {
		FakeWallClock wallClock = new FakeWallClock(1_000_000L);
		SimulatedClock clock = new SimulatedClock(wallClock);

		clock.setRate(10.0);
		assertTrue(clock.isPlaying());
		assertEquals(10.0, clock.getRate());

		wallClock.advance(1_000L);
		assertEquals(1_010_000L, clock.getCurrentTimeMillis(), "10x rate should advance simulated time 10ms per 1ms real");
	}

	@Test
	void setRateReanchorsSoChangingRateMidFlightDoesNotJump() {
		FakeWallClock wallClock = new FakeWallClock(1_000_000L);
		SimulatedClock clock = new SimulatedClock(wallClock);

		clock.setRate(10.0);
		wallClock.advance(1_000L); // simulated time is now 1,010,000
		long beforeRateChange = clock.getCurrentTimeMillis();

		clock.setRate(2.0);
		assertEquals(beforeRateChange, clock.getCurrentTimeMillis(),
				"changing rate must not itself jump the simulated time");

		wallClock.advance(1_000L);
		assertEquals(beforeRateChange + 2_000L, clock.getCurrentTimeMillis());
	}

	@Test
	void setRateOfZeroBehavesLikePause() {
		FakeWallClock wallClock = new FakeWallClock(1_000_000L);
		SimulatedClock clock = new SimulatedClock(wallClock);

		wallClock.advance(3_000L);
		clock.setRate(0.0);
		long pausedAt = clock.getCurrentTimeMillis();
		assertFalse(clock.isPlaying());

		wallClock.advance(60_000L);
		assertEquals(pausedAt, clock.getCurrentTimeMillis());
	}

	@Test
	void negativeRatePlaysBackward() {
		FakeWallClock wallClock = new FakeWallClock(1_000_000L);
		SimulatedClock clock = new SimulatedClock(wallClock);

		clock.setRate(-5.0);
		wallClock.advance(1_000L);

		assertEquals(1_000_000L - 5_000L, clock.getCurrentTimeMillis());
	}

	@Test
	void resumeIsANoOpWhenAlreadyPlayingAtACustomRate() {
		FakeWallClock wallClock = new FakeWallClock(1_000_000L);
		SimulatedClock clock = new SimulatedClock(wallClock);

		clock.setRate(3.5);
		clock.resume();

		assertEquals(3.5, clock.getRate(), "resume() must not override an already-playing custom rate");
	}

	@Test
	void repeatedlySettingThePausedTimeToTheSameValueNeverAdvances() {
		// This is what makes ObservationTime.recompute()'s dedup-on-no-change work for free while
		// paused, per CLAUDE.md's note - the render loop can keep calling setUnixTime(...) with
		// the same paused value every frame with no effect.
		FakeWallClock wallClock = new FakeWallClock(1_000_000L);
		SimulatedClock clock = new SimulatedClock(wallClock);
		clock.pause();

		long value1 = clock.getCurrentTimeMillis();
		wallClock.advance(10_000L);
		long value2 = clock.getCurrentTimeMillis();

		assertEquals(value1, value2);
	}
}
