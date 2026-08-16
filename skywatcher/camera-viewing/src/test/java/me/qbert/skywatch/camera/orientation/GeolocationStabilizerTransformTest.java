package me.qbert.skywatch.camera.orientation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.model.ObjectDirectionAltAz;

class GeolocationStabilizerTransformTest {

	@Test
	void notEngagedPassesTheBaseOrientationThrough() {
		GeolocationStabilizerTransform stabilizer = new GeolocationStabilizerTransform();
		Orientation base = new Orientation(10.0, 20.0, 3.0);

		assertFalse(stabilizer.isEngaged());
		assertSame(base, stabilizer.compute(null, null, base));
	}

	@Test
	void evaluatingAtTheEngagementLocationReturnsTheLockedOrientationUnchanged() {
		GeolocationStabilizerTransform stabilizer = new GeolocationStabilizerTransform();
		stabilizer.setRaLocked(true);
		stabilizer.setDecLocked(true);
		stabilizer.setRollLocked(true);

		Orientation locked = new Orientation(0.0, 90.0, 0.0);
		stabilizer.engage(locked, 45.0, -75.0);

		Orientation result = stabilizer.computeEngagedOrientation(45.0, -75.0);
		assertEquals(0.0, result.getAltitude(), 0.0001);
		assertEquals(90.0, result.getAzimuth(), 0.0001);
		assertEquals(0.0, result.getBarrelRoll(), 0.0001);
	}

	// CLAUDE.md: "with all three axes unlocked, the stabilizer is engaged but produces no visible
	// effect - this is expected, not a bug to guard against."
	@Test
	void allThreeAxesUnlockedProducesNoVisibleEffectRegardlessOfLocationChange() {
		GeolocationStabilizerTransform stabilizer = new GeolocationStabilizerTransform();
		Orientation locked = new Orientation(15.0, 200.0, 4.0);
		stabilizer.engage(locked, 45.0, -75.0);

		Orientation result = stabilizer.computeEngagedOrientation(-30.0, 120.0); // very different location

		assertEquals(locked.getAltitude(), result.getAltitude(), 0.0001);
		assertEquals(locked.getAzimuth(), result.getAzimuth(), 0.0001);
		assertEquals(locked.getBarrelRoll(), result.getBarrelRoll(), 0.0001);
	}

	// A real user report caught this mapping was backwards from the natural celestial-sphere analogy
	// (declination IS celestial latitude) - swapped a round after these tests were first written; see
	// GeolocationStabilizerTransform's own class comment for the full writeup. Dec-lock now responds
	// to latitude, RA-lock now responds to longitude - the reverse of this test's own original name/
	// assertions, which is why it (and its sibling below) were renamed rather than just re-pointed.
	@Test
	void decLockRespondsToLatitudeChangeButNotLongitude() {
		GeolocationStabilizerTransform stabilizer = new GeolocationStabilizerTransform();
		stabilizer.setDecLocked(true);

		// Note: az=90 (due east) at alt=0 is a degenerate/special-case input for this geometry -
		// altAzToXYZ(0, 90) always lands exactly on the X rotation axis, which transformXAxis
		// leaves invariant regardless of angle. That's not a bug: an object exactly on the
		// celestial equator genuinely rises due east at altitude 0 for every observer latitude, so
		// "no change" would be the astronomically correct answer for that specific case too. Using
		// az=45 here to exercise the general (non-degenerate) mechanism instead.
		Orientation offEquatorial = new Orientation(0.0, 45.0, 0.0);
		stabilizer.engage(offEquatorial, 0.0, 0.0); // equator

		Orientation afterLatitudeChange = stabilizer.computeEngagedOrientation(30.0, 0.0);
		assertTrue(Math.abs(afterLatitudeChange.getAltitude() - offEquatorial.getAltitude()) > 1.0,
				"Dec-locked altitude should respond to a latitude change");

		Orientation afterLongitudeOnlyChange = stabilizer.computeEngagedOrientation(0.0, 90.0);
		assertEquals(offEquatorial.getAltitude(), afterLongitudeOnlyChange.getAltitude(), 0.0001,
				"Dec-locked-alone should be unaffected by a longitude-only change");
		assertEquals(offEquatorial.getAzimuth(), afterLongitudeOnlyChange.getAzimuth(), 0.0001);
	}

	// See the sibling test's own comment above for why this was renamed/swapped.
	@Test
	void raLockRespondsToLongitudeChangeButNotLatitude() {
		GeolocationStabilizerTransform stabilizer = new GeolocationStabilizerTransform();
		stabilizer.setRaLocked(true);

		Orientation level = new Orientation(0.0, 90.0, 0.0);
		stabilizer.engage(level, 0.0, 0.0);

		Orientation afterLongitudeChange = stabilizer.computeEngagedOrientation(0.0, 90.0);
		boolean altitudeChanged = Math.abs(afterLongitudeChange.getAltitude() - level.getAltitude()) > 1.0;
		boolean azimuthChanged = Math.abs(afterLongitudeChange.getAzimuth() - level.getAzimuth()) > 1.0;
		assertTrue(altitudeChanged || azimuthChanged, "RA-locked orientation should respond to a longitude change");

		Orientation afterLatitudeOnlyChange = stabilizer.computeEngagedOrientation(30.0, 0.0);
		assertEquals(level.getAltitude(), afterLatitudeOnlyChange.getAltitude(), 0.0001,
				"RA-locked-alone should be unaffected by a latitude-only change");
		assertEquals(level.getAzimuth(), afterLatitudeOnlyChange.getAzimuth(), 0.0001);
	}

	// CLAUDE.md: "Locking barrel roll specifically at alt=0, az=90/270... the ground appears to
	// rotate under the sight line as location changes... Leaving roll unlocked makes this axis's
	// effect invisible."
	@Test
	void rollLockRespondsToLocationWhilePointingStaysFixed() {
		GeolocationStabilizerTransform stabilizer = new GeolocationStabilizerTransform();
		stabilizer.setRollLocked(true);

		Orientation levelFacingEast = new Orientation(0.0, 90.0, 0.0);
		stabilizer.engage(levelFacingEast, 45.0, -75.0);

		Orientation moved = stabilizer.computeEngagedOrientation(10.0, -60.0);

		// Pointing (altitude/azimuth) must NOT change - only roll should be affected.
		assertEquals(levelFacingEast.getAltitude(), moved.getAltitude(), 0.0001);
		assertEquals(levelFacingEast.getAzimuth(), moved.getAzimuth(), 0.0001);
		assertTrue(Math.abs(moved.getBarrelRoll() - levelFacingEast.getBarrelRoll()) > 0.1,
				"roll should change as location changes when roll-locked");
	}

	// Same round, same root cause, as EquatorialMountTransform's own fix - see that class's class
	// comment and GeolocationStabilizerTransform's own class comment for the full writeup. Verified
	// with a rigorous ground-truth check before writing this test: with all three axes locked,
	// rotateForAxes(...) and rotateForRoll(...) become the SAME transform, so this is the exact
	// analog of EquatorialMountTransformTest's own star-invariance check - a reference star (offset
	// from the locked boresight) must stay at a perfectly constant screen position (theta AND phi)
	// as BOTH latitude and longitude vary together, since the whole camera frame (boresight + up) is
	// rotating rigidly.
	@Test
	void allThreeAxesLockedPreservesAFixedStarsScreenPositionAsLocationChanges() {
		GeolocationStabilizerTransform stabilizer = new GeolocationStabilizerTransform();
		stabilizer.setRaLocked(true);
		stabilizer.setDecLocked(true);
		stabilizer.setRollLocked(true);

		Orientation locked = new Orientation(0.0, 45.0, 0.0);
		double lockLatitude = 45.0;
		double lockLongitude = -75.0;
		stabilizer.engage(locked, lockLatitude, lockLongitude);

		double starLockAltitude = locked.getAltitude() + 5.0;
		double starLockAzimuth = locked.getAzimuth() + 5.0;
		double[] starLockXyz = RotationVector.altAzToXYZ(starLockAltitude, starLockAzimuth);

		BoresightAngles.Angles initialAngles = null;
		for (double delta = 0.0; delta <= 100.0; delta += 20.0) {
			double latitude = lockLatitude + delta * 0.3;
			double longitude = lockLongitude + delta;

			Orientation camera = stabilizer.computeEngagedOrientation(latitude, longitude);

			// The reference star must move the SAME rigid way the camera itself does - this test's
			// own independent computation of "where would a truly fixed star be now," not a call
			// into the class under test.
			double[] rotated = RotationVector.transformXAxis(starLockXyz, 90.0 - lockLatitude);
			rotated = RotationVector.transformZAxis(rotated, -(longitude - lockLongitude));
			rotated = RotationVector.transformXAxis(rotated, -90.0 + latitude);
			ObjectDirectionAltAz starNow = RotationVector.xyzToAltAz(rotated);

			BoresightAngles.Angles angles = BoresightAngles.decompose(camera, starNow.getAltitude(),
					starNow.getAzimuth());
			if (initialAngles == null) {
				initialAngles = angles;
			} else {
				assertEquals(Math.toDegrees(initialAngles.getThetaRadians()), Math.toDegrees(angles.getThetaRadians()),
						0.0001, "lat=" + latitude + " lon=" + longitude);
				assertEquals(Math.toDegrees(initialAngles.getPhiRadians()), Math.toDegrees(angles.getPhiRadians()),
						0.0001, "lat=" + latitude + " lon=" + longitude
								+ " - the star must stay at a fixed screen position, not visibly rotate");
			}
		}
	}

	// CLAUDE.md's own worked example, made into a real regression test: "level camera facing east...
	// locking barrel roll... the ground will appear to rotate under your sight line as location
	// changes." RA/Dec deliberately left UNLOCKED here - the boresight itself must stay exactly
	// fixed (a passthrough, per this class's own documented semantics), while roll alone responds to
	// the changing location. The real, checkable invariant: a point that was exactly "straight down"
	// on screen at lock time must STAY straight down (same theta/phi) as latitude changes - if roll
	// is computed correctly, it exactly cancels the "ground rotating under the sight line" effect
	// from the camera's own point of view, even though the boresight direction itself never moves.
	@Test
	void rollOnlyLockedKeepsAFixedScreenPointFixedAsLatitudeChanges() {
		GeolocationStabilizerTransform stabilizer = new GeolocationStabilizerTransform();
		stabilizer.setRollLocked(true); // RA/Dec left unlocked

		Orientation locked = new Orientation(0.0, 90.0, 0.0); // level, facing east
		double lockLatitude = 45.0;
		double lockLongitude = -75.0;
		stabilizer.engage(locked, lockLatitude, lockLongitude);

		ObjectDirectionAltAz downAtLock = BoresightAngles.reconstructAltAz(locked, 0.05, -Math.PI / 2.0);
		double[] downLockXyz = RotationVector.altAzToXYZ(downAtLock.getAltitude(), downAtLock.getAzimuth());

		BoresightAngles.Angles initialAngles = null;
		for (double latitude = lockLatitude; latitude <= 75.0; latitude += 10.0) {
			Orientation camera = stabilizer.computeEngagedOrientation(latitude, lockLongitude);

			assertEquals(locked.getAltitude(), camera.getAltitude(), 0.0001, "boresight altitude must stay fixed - RA unlocked");
			assertEquals(locked.getAzimuth(), camera.getAzimuth(), 0.0001, "boresight azimuth must stay fixed - Dec unlocked");

			double[] rotated = RotationVector.transformXAxis(downLockXyz, 90.0 - lockLatitude);
			rotated = RotationVector.transformZAxis(rotated, -(lockLongitude - lockLongitude));
			rotated = RotationVector.transformXAxis(rotated, -90.0 + latitude);
			ObjectDirectionAltAz downNow = RotationVector.xyzToAltAz(rotated);

			BoresightAngles.Angles angles = BoresightAngles.decompose(camera, downNow.getAltitude(), downNow.getAzimuth());
			if (initialAngles == null) {
				initialAngles = angles;
			} else {
				assertEquals(Math.toDegrees(initialAngles.getThetaRadians()), Math.toDegrees(angles.getThetaRadians()),
						0.0001, "latitude=" + latitude);
				assertEquals(Math.toDegrees(initialAngles.getPhiRadians()), Math.toDegrees(angles.getPhiRadians()),
						0.0001, "latitude=" + latitude
								+ " - a fixed screen point must stay fixed even as the ground visibly rotates");
			}
		}
	}

	@Test
	void rollUnlockedLeavesRollUntouchedEvenWithOtherAxesLocked() {
		GeolocationStabilizerTransform stabilizer = new GeolocationStabilizerTransform();
		stabilizer.setRaLocked(true);
		stabilizer.setDecLocked(true);
		// rollLocked left false.

		Orientation start = new Orientation(20.0, 45.0, 7.0);
		stabilizer.engage(start, 45.0, -75.0);

		Orientation result = stabilizer.computeEngagedOrientation(-10.0, 30.0);

		assertEquals(start.getBarrelRoll(), result.getBarrelRoll(), 0.0001,
				"unlocked roll must stay exactly at its engagement value, not recomputed");
	}
}
