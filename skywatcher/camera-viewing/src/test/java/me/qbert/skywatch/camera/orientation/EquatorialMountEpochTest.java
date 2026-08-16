package me.qbert.skywatch.camera.orientation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// docs/tasks.md task 3.3b: "set an epoch, scrub to several different frames, confirm each computed
// orientation matches hand-calculated computeValues() output for that frame's elapsed time since
// the epoch."
class EquatorialMountEpochTest {

	private static final long HOUR_MILLIS = 3_600_000L;

	@Test
	void startsWithNoEpochSet() {
		EquatorialMountEpoch epoch = new EquatorialMountEpoch();

		assertFalse(epoch.hasEpoch());
		assertThrows(IllegalStateException.class, () -> epoch.orientationForFrame(0L));
	}

	@Test
	void scrubbingToSeveralFramesMatchesTheUnderlyingTransformDirectly() {
		Orientation firstFrameOrientation = new Orientation(15.0, 200.0, 1.5);
		long epochFrameEpochMillis = 1_699_000_000_000L;
		double observerLatitude = 51.5;

		EquatorialMountEpoch epoch = new EquatorialMountEpoch();
		epoch.setTrackingRate(TrackingRate.SIDEREAL);
		epoch.setEpoch(firstFrameOrientation, observerLatitude, epochFrameEpochMillis);
		assertTrue(epoch.hasEpoch());

		// Independent reference computed directly via the transform this class wraps - if these
		// two disagree, the wrapper isn't doing a plain pass-through.
		EquatorialMountTransform reference = new EquatorialMountTransform();
		reference.setTrackingRate(TrackingRate.SIDEREAL);
		reference.lock(firstFrameOrientation, observerLatitude, epochFrameEpochMillis);

		long[] frameOffsetsHours = {0L, 1L, 3L, -2L, 10L};
		for (long offsetHours : frameOffsetsHours) {
			long frameEpochMillis = epochFrameEpochMillis + offsetHours * HOUR_MILLIS;

			Orientation viaEpoch = epoch.orientationForFrame(frameEpochMillis);
			Orientation viaTransform = reference.computeLockedOrientation(frameEpochMillis);

			assertEquals(viaTransform.getAltitude(), viaEpoch.getAltitude(), 0.0001, "offset " + offsetHours + "h");
			assertEquals(viaTransform.getAzimuth(), viaEpoch.getAzimuth(), 0.0001, "offset " + offsetHours + "h");
			assertEquals(viaTransform.getBarrelRoll(), viaEpoch.getBarrelRoll(), 0.0001, "offset " + offsetHours + "h");
		}
	}

	@Test
	void repeatedScrubbingNeverMutatesTheEpochReference() {
		EquatorialMountEpoch epoch = new EquatorialMountEpoch();
		Orientation epochOrientation = new Orientation(0.0, 90.0, 0.0);
		long epochMillis = 1_700_000_000_000L;
		epoch.setEpoch(epochOrientation, 45.0, epochMillis);

		Orientation firstRead = epoch.orientationForFrame(epochMillis + 5L * HOUR_MILLIS);
		// Scrub elsewhere and back - a stateful/caching bug would show up as drift here.
		epoch.orientationForFrame(epochMillis + 20L * HOUR_MILLIS);
		epoch.orientationForFrame(epochMillis - 3L * HOUR_MILLIS);
		Orientation secondRead = epoch.orientationForFrame(epochMillis + 5L * HOUR_MILLIS);

		assertEquals(firstRead.getAltitude(), secondRead.getAltitude(), 0.0001);
		assertEquals(firstRead.getAzimuth(), secondRead.getAzimuth(), 0.0001);
		assertEquals(firstRead.getBarrelRoll(), secondRead.getBarrelRoll(), 0.0001);
	}
}
