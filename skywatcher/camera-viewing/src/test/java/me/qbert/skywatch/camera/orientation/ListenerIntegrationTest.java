package me.qbert.skywatch.camera.orientation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;

// docs/tasks.md task 3.10: "Build/verify against direct ObserverLocation changes, not a UI...
// 3.5-3.9 can be fully implemented and unit-tested by changing ObserverLocation directly (a test
// harness, no interactive control needed) well before Phase 8.3's roaming UI exists." Unlike the
// other orientation tests in this package (which call computeEngagedOrientation/
// computeLockedOrientation with raw doubles), this exercises the real sw-base
// ObservationTime/ObserverLocation + addListener(...) wiring end to end, per CLAUDE.md's
// "Orientation-transformer listener architecture" - proving the transforms work as registered
// ObjectStateChangeListeners against the actual live classes, not just as pure functions in
// isolation.
class ListenerIntegrationTest {

	@Test
	void geolocationStabilizerRespondsToARealObserverLocationMutation() {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(0.0, 0.0);

		// Dec-lock responds to latitude (a real user report later swapped this mapping - see
		// GeolocationStabilizerTransform's own class comment; this test only needs SOME axis that
		// responds to the latitude-only mutation below, and Dec is that axis post-swap).
		GeolocationStabilizerTransform stabilizer = new GeolocationStabilizerTransform();
		stabilizer.setDecLocked(true);
		location.addListener(stabilizer);

		Orientation start = new Orientation(0.0, 45.0, 0.0);
		stabilizer.engage(start, location.getLatitude(), location.getLongitude());

		// Real mutation through sw-base's own API - this is what fires
		// ObserverLocation.settingsChanged() -> stabilizer.stateChanged(...).
		location.setGeoLocation(30.0, 0.0);

		Orientation afterMove = stabilizer.compute(null, location, start);

		assertTrue(Math.abs(afterMove.getAltitude() - start.getAltitude()) > 1.0,
				"compute() against the live, mutated ObserverLocation should reflect the new latitude");

		// Cross-check against the raw-double API this wraps, to confirm the two paths agree.
		Orientation viaRawApi = stabilizer.computeEngagedOrientation(30.0, 0.0);
		assertEquals(viaRawApi.getAltitude(), afterMove.getAltitude(), 0.0001);
		assertEquals(viaRawApi.getAzimuth(), afterMove.getAzimuth(), 0.0001);
	}

	@Test
	void equatorialMountRespondsToARealObservationTimeMutation() throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(1_700_000_000_000L);

		EquatorialMountTransform mount = new EquatorialMountTransform();
		mount.setTrackingRate(TrackingRate.SIDEREAL);
		time.addListener(mount);

		Orientation start = new Orientation(10.0, 45.0, 0.0);
		mount.lock(start, 45.0, time.getTime().getTimeInMillis());

		// Real mutation through sw-base's own API.
		time.setUnixTime(1_700_000_000_000L + 3L * 3_600_000L);

		Orientation afterTimePasses = mount.compute(time, null, start);
		Orientation viaRawApi = mount.computeLockedOrientation(1_700_000_000_000L + 3L * 3_600_000L);

		assertEquals(viaRawApi.getAltitude(), afterTimePasses.getAltitude(), 0.0001);
		assertEquals(viaRawApi.getAzimuth(), afterTimePasses.getAzimuth(), 0.0001);
		assertEquals(viaRawApi.getBarrelRoll(), afterTimePasses.getBarrelRoll(), 0.0001);
	}
}
