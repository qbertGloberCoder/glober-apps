package me.qbert.skywatch.camera.orientation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.GeoCalculator;
import me.qbert.skywatch.model.GeoLocation;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.model.ObjectDirectionRaDec;

class EquatorialMountTransformTest {

	private static final long HOUR_MILLIS = 3_600_000L;

	@Test
	void unlockedTransformPassesTheBaseOrientationThrough() {
		EquatorialMountTransform mount = new EquatorialMountTransform();
		Orientation base = new Orientation(12.0, 90.0, 5.0);

		assertFalse(mount.isLocked());
		assertSame(base, mount.compute(null, null, base));
	}

	@Test
	void evaluatingExactlyAtTheLockInstantReturnsTheLockedOrientationUnchanged() {
		EquatorialMountTransform mount = new EquatorialMountTransform();
		Orientation locked = new Orientation(0.0, 90.0, 0.0);
		long lockEpoch = 1_700_000_000_000L;

		mount.lock(locked, 45.0, lockEpoch);

		Orientation result = mount.computeLockedOrientation(lockEpoch);
		assertEquals(0.0, result.getAltitude(), 0.0001);
		assertEquals(90.0, result.getAzimuth(), 0.0001);
		assertEquals(0.0, result.getBarrelRoll(), 0.0001);
	}

	// spec §11's demo case, encoded as a behavioral test before the implementation existed (see
	// docs/tasks.md task 3.1): "a camera pointed level, facing east... on a real equatorial mount,
	// the camera slowly rotates from upright to upside-down over the course of a day while showing
	// the same stars in the same orientation throughout." Tested qualitatively/robustly rather than
	// pinning an exact roll value at the halfway point: this class's real geometry couples
	// altitude/azimuth/roll together for an object away from the pole (an object near the
	// celestial equator physically transits across the sky over the day, not just "rolls in
	// place"), so asserting one precise intermediate roll number would encode a guess, not a
	// verified physical fact. What's directly verifiable: roll changes substantially and smoothly
	// (no jumps/NaN) across the day, and a full sidereal day returns arbitrarily close to the
	// start (see the next test) - together these match "slowly rotates... over the course of a
	// day", the part of the demo description that's actually checkable without a full renderer.
	@Test
	void levelCameraFacingEastSweepsSmoothlyAndSubstantiallyOverTheDay() {
		EquatorialMountTransform mount = new EquatorialMountTransform();
		mount.setTrackingRate(TrackingRate.SIDEREAL);

		Orientation level = new Orientation(0.0, 90.0, 0.0);
		long lockEpoch = 1_700_000_000_000L;
		mount.lock(level, 45.0, lockEpoch);

		double previousRoll = level.getBarrelRoll();
		double totalAbsoluteRollChange = 0.0;
		int sampleCount = 24;

		for (int hour = 1; hour <= sampleCount; hour++) {
			Orientation sample = mount.computeLockedOrientation(lockEpoch + hour * HOUR_MILLIS);

			assertFalse(Double.isNaN(sample.getAltitude()) || Double.isNaN(sample.getAzimuth())
					|| Double.isNaN(sample.getBarrelRoll()), "hour " + hour + " produced NaN");

			double step = Math.abs(normalizeTo180(sample.getBarrelRoll() - previousRoll));
			// A real bug (e.g. a wraparound or degenerate-latitude error) would show up as either
			// a near-zero step (stuck) or a near-180 step (a wrap/sign-flip glitch) at every hour;
			// smooth continuous motion keeps each hourly step comfortably inside that range.
			assertTrue(step < 90.0, "hour " + hour + " roll jumped by " + step + " degrees - not smooth");

			totalAbsoluteRollChange += step;
			previousRoll = sample.getBarrelRoll();
		}

		assertTrue(totalAbsoluteRollChange > 90.0,
				"expected substantial accumulated roll motion over a full day, got " + totalAbsoluteRollChange);
	}

	@Test
	void siderealRateCompletesAFullRotationInExactlyTwentyFourHours() {
		// Task 9.2's golden-value correction: SIDEREAL is now exactly 15.0 deg/hour (the user's
		// supplied standard-rates table, 54,000 arcsec/hour - the conventional "15 arcsec/second"
		// figure real mount hand controllers use), not the earlier round's more astronomically
		// precise 15.0410686 deg/hour (360.985647/23.934469h, the TRUE sidereal-day rate). That
		// changes what "one full rotation" means here: at exactly 15.0 deg/hour, 24 hours - not a
		// 23.934469-hour true sidereal day - is what brings the mount back to its start (15.0 x 24
		// = 360 exactly). See TrackingRateTest's equivalent arithmetic-only check.
		EquatorialMountTransform mount = new EquatorialMountTransform();
		mount.setTrackingRate(TrackingRate.SIDEREAL);

		Orientation start = new Orientation(20.0, 45.0, 3.0);
		long lockEpoch = 1_700_000_000_000L;
		mount.lock(start, 40.0, lockEpoch);

		long twentyFourHoursMillis = 24L * HOUR_MILLIS;
		Orientation oneDayLater = mount.computeLockedOrientation(lockEpoch + twentyFourHoursMillis);

		assertEquals(start.getAltitude(), oneDayLater.getAltitude(), 0.01);
		assertEquals(start.getAzimuth(), oneDayLater.getAzimuth(), 0.01);
		assertEquals(start.getBarrelRoll(), oneDayLater.getBarrelRoll(), 0.01);
	}

	@Test
	void reLockingIsIndependentOfThePreviousLock() {
		EquatorialMountTransform mount = new EquatorialMountTransform();
		mount.setTrackingRate(TrackingRate.SOLAR);

		mount.lock(new Orientation(0.0, 90.0, 0.0), 45.0, 1_700_000_000_000L);
		Orientation driftedAway = mount.computeLockedOrientation(1_700_000_000_000L + 5L * HOUR_MILLIS);

		// Re-engage from wherever it currently sits, at a new epoch - not relative to the
		// original 1_700_000_000_000L lock.
		mount.lock(driftedAway, 45.0, 2_000_000_000_000L);
		Orientation immediatelyAfterReLock = mount.computeLockedOrientation(2_000_000_000_000L);

		assertEquals(driftedAway.getAltitude(), immediatelyAfterReLock.getAltitude(), 0.0001);
		assertEquals(driftedAway.getAzimuth(), immediatelyAfterReLock.getAzimuth(), 0.0001);
		assertEquals(driftedAway.getBarrelRoll(), immediatelyAfterReLock.getBarrelRoll(), 0.0001);
	}

	// docs/tasks.md task 3.4: "confirm switching rate mid-simulation takes effect without needing
	// to re-lock."
	@Test
	void switchingTrackingRateMidSimulationTakesEffectImmediately() {
		EquatorialMountTransform mount = new EquatorialMountTransform();
		mount.setTrackingRate(TrackingRate.SIDEREAL);

		long lockEpoch = 1_700_000_000_000L;
		mount.lock(new Orientation(10.0, 90.0, 0.0), 45.0, lockEpoch);

		long threeHoursLater = lockEpoch + 3L * HOUR_MILLIS;
		Orientation withSidereal = mount.computeLockedOrientation(threeHoursLater);

		// No re-lock in between - just change the rate and evaluate the same elapsed time again.
		mount.setTrackingRate(TrackingRate.LUNAR);
		Orientation withLunar = mount.computeLockedOrientation(threeHoursLater);

		assertTrue(mount.isLocked(), "switching rate must not implicitly unlock");
		assertTrue(Math.abs(withSidereal.getAzimuth() - withLunar.getAzimuth()) > 0.01,
				"a different tracking rate must produce a different result for the same elapsed time");
	}

	// A real bug, found and diagnosed directly by the user via their own manual reproduction
	// (originally EquatorialMountTransformNoJunitTest.java - a standalone main() program, migrated
	// here as a real, permanent, always-run test per their explicit instruction). An ideal
	// equatorial mount tracking at sidereal rate must preserve the camera's FULL orientation
	// relative to the celestial sphere - not just where the boresight points, but which way "down"
	// appears on screen relative to the stars too. See EquatorialMountTransform's own class comment
	// for the full root-cause writeup (the old roll formula used a differential-bearing trick in
	// (altitude, azimuth) coordinates, which are not a flat space, so it silently accumulated a real
	// error).
	//
	// This reuses the user's own independent verification tool - sw-base's GeoCalculator, a
	// completely separate RA/Dec-style conversion from this class's own RotationVector-based
	// tracking math, making it a genuine cross-check rather than re-deriving the same formula a
	// different way - but measures a point straight DOWN FROM CENTER **ON SCREEN**
	// (BoresightAngles.reconstructAltAz, which is roll-aware) rather than a raw altitude offset from
	// the boresight. That distinction matters: the user's original methodology offset only in raw
	// altitude, which never involves barrelRoll at all - traced through directly, that quantity is
	// NOT expected to stay constant even under a CORRECT mount (a real equatorial mount genuinely
	// does let the un-rolled local "down" direction drift relative to the sky - compensating for
	// exactly that drift is what barrelRoll is FOR), so porting it literally would fail regardless of
	// whether this bug is fixed. Measuring the actual on-screen "down" direction (which DOES depend
	// on barrelRoll) is what the bug report was actually about: "when I simulate the view from the EQ
	// mount, the stars rotate around the sight-line axis."
	@Test
	void trackingPreservesTheCameraLocalFrameOrientationRelativeToTheSky() throws Exception {
		EquatorialMountTransform mount = new EquatorialMountTransform();
		mount.setTrackingRate(TrackingRate.SIDEREAL);

		Orientation locked = new Orientation(0.0, 90.0, 0.0);
		long lockEpoch = 1_700_000_000_000L;
		double observerLatitude = 45.0;
		mount.lock(locked, observerLatitude, lockEpoch);

		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(observerLatitude, 0.0);
		GeoCalculator geoCalc = new GeoCalculator();

		double initialBearing = screenDownCelestialBearing(geoCalc, location, locked);

		for (int hour = 1; hour <= 12; hour++) {
			Orientation sample = mount.computeLockedOrientation(lockEpoch + hour * HOUR_MILLIS);
			double bearing = screenDownCelestialBearing(geoCalc, location, sample);

			assertEquals(initialBearing, bearing, 0.5,
					"hour " + hour + ": a point straight down from center on screen must keep the same "
							+ "bearing relative to the sky throughout tracking - a drift here is exactly the "
							+ "reported symptom (stars visibly rotating around the sight line)");
		}
	}

	// A small, fixed angular offset (theta) straight down (phi = -90 degrees, BoresightAngles' own
	// "up is positive phi" convention) from the camera's current boresight, converted to the
	// user's own GeoCalculator-based "celestial bearing" between that point and the boresight
	// itself - see the test above for why this must be roll-aware to actually exercise the bug.
	private double screenDownCelestialBearing(GeoCalculator geoCalc, ObserverLocation location, Orientation camera) {
		ObjectDirectionAltAz down = BoresightAngles.reconstructAltAz(camera, 0.0001, -Math.PI / 2.0);

		ObjectDirectionAltAz boresight = new ObjectDirectionAltAz();
		boresight.setAltitude(camera.getAltitude());
		boresight.setAzimuth(camera.getAzimuth());

		ObjectDirectionRaDec boresightRaDec = geoCalc.altAzToRaDec(boresight, location);
		ObjectDirectionRaDec downRaDec = geoCalc.altAzToRaDec(down, location);

		GeoLocation boresightGeo = new GeoLocation();
		boresightGeo.setLatitude(boresightRaDec.getDeclination());
		boresightGeo.setLongitude(boresightRaDec.getRightAscension());
		GeoLocation downGeo = new GeoLocation();
		downGeo.setLatitude(downRaDec.getDeclination());
		downGeo.setLongitude(downRaDec.getRightAscension());

		return GeoCalculator.getGlobeBearing(boresightGeo, downGeo);
	}

	private static double normalizeTo180(double degrees) {
		double result = degrees % 360.0;
		if (result > 180.0)
			result -= 360.0;
		if (result < -180.0)
			result += 360.0;
		return result;
	}
}
