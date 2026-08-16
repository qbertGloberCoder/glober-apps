package me.qbert.skywatch.camera.orientation;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.listeners.ObjectStateChangeListener;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

/*
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

// Ported from org.bluerock.astro.EquatorialMount.computeValues() - see CLAUDE.md's porting-notes
// table row. computeValuesiffy() is confirmed dead (an abandoned alternate attempt per the user)
// and was not ported. One deliberate cleanup versus the original: the ported method recomputed an
// identical "cameraCenter" block twice in a row for no reason (dead code, not a different
// calculation) - removed here rather than carried forward.
//
// A real bug found and fixed by the user directly, via a manual reproduction they built themselves
// (EquatorialMountTransformNoJunitTest, now migrated into EquatorialMountTransformTest): the ORIGINAL
// roll computation derived the "how has the local frame rotated" angle via a DIFFERENTIAL BEARING
// trick - rotate the boresight and a nearby (+0.01 degree offset) point by the same raDelta, then take
// atan2 of their (altitude, azimuth) DIFFERENCE. That's wrong because (altitude, azimuth) is not a
// flat/uniform coordinate space - azimuth compresses near the zenith exactly like longitude compresses
// near Earth's poles - so a naive atan2 of raw coordinate differences silently picks up a systematic
// error that grows with how far the boresight has swung in altitude. Confirmed both the bug and the
// fix with a rigorous ground-truth check (not just re-deriving the same kind of formula differently):
// decompose a reference "star" (a fixed offset from the locked boresight, rotated the same way a real
// star's alt/az would evolve) through orientation.BoresightAngles.decompose(...) - the SAME code the
// real renderer uses - at many points across a 12-hour track. Under the old formula, the star's screen
// bearing (phi) drifted by nearly 180 degrees over 12 hours (theta, the angular distance, stayed
// perfectly constant throughout - confirming the boresight's OWN tracking was always correct; only the
// roll compensation was wrong) - exactly the reported symptom: "the stars rotate around the sight-line
// axis."
//
// The fix: stop deriving roll from a differential bearing at all. Instead, rotate the camera's actual
// 3D "up" vector (BoresightAngles.upVectorFromRoll(...) - the SAME authoritative up-direction
// convention the renderer itself uses) by the EXACT SAME rigid rotation applied to the boresight, then
// read the new roll back out via BoresightAngles.rollDegreesFromUpVector(...) - the exact inverse.
// Since the camera is a rigid body attached to the mount, its boresight and up vector must rotate
// together under the identical transform; deriving roll this way is exact by construction (an
// orthogonal/rigid rotation preserves all angles between vectors it's applied to identically), not an
// approximation the way the differential-bearing trick was. Verified this is exact, not just "closer":
// the star's phi now stays bit-for-bit constant across the same 12-hour sweep that used to drift by
// 180 degrees.
//
// Restricted to Virtual cameras (Fixed or PTZ) - never Real - per CLAUDE.md's "Camera setup". The
// enable/disable state machine (lock()/unlock()) and its persist-on-disable behavior live in the
// camera config layer, not in this class - see CLAUDE.md's "Equatorial mount:
// activation/deactivation persistence".
public class EquatorialMountTransform implements OrientationTransformer, ObjectStateChangeListener {
	private double trackingRateDegreesPerHour = TrackingRate.SIDEREAL.getDegreesPerHour();

	private boolean locked = false;
	private double lockAltitude;
	private double lockAzimuth;
	private double lockBaseRoll;
	private double lockLatitude;
	private long lockEpochMillis;

	public void setTrackingRate(TrackingRate trackingRate) {
		if (trackingRate == null)
			throw new IllegalArgumentException("trackingRate must not be null");
		this.trackingRateDegreesPerHour = trackingRate.getDegreesPerHour();
	}

	// The raw custom rate escape hatch alongside the three presets - see CLAUDE.md.
	public void setTrackingRateDegreesPerHour(double trackingRateDegreesPerHour) {
		this.trackingRateDegreesPerHour = trackingRateDegreesPerHour;
	}

	public double getTrackingRateDegreesPerHour() {
		return trackingRateDegreesPerHour;
	}

	public boolean isLocked() {
		return locked;
	}

	// Captures the current orientation + timestamp as the reference lock point - equivalent to
	// the old EquatorialMount.setCameraLock(...). Each call to lock() is independent - re-locking
	// after being unlocked does not remember the previous lock, exactly like re-engaging a real
	// mount's clutch after manually repointing it.
	public void lock(Orientation currentOrientation, double observerLatitude, long epochMillis) {
		if (currentOrientation == null)
			throw new IllegalArgumentException("currentOrientation must not be null");

		this.lockAltitude = currentOrientation.getAltitude();
		this.lockAzimuth = currentOrientation.getAzimuth();
		this.lockBaseRoll = currentOrientation.getBarrelRoll();
		this.lockLatitude = observerLatitude;
		this.lockEpochMillis = epochMillis;
		this.locked = true;
	}

	public void unlock() {
		locked = false;
	}

	@Override
	public Orientation compute(ObservationTime time, ObserverLocation location, Orientation baseOrientation) {
		if (!locked)
			return baseOrientation;

		return computeLockedOrientation(time.getTime().getTimeInMillis());
	}

	// Exposed directly (not just via compute()) so callers/tests can evaluate the pure function at
	// an arbitrary timestamp without needing a live ObservationTime.
	public Orientation computeLockedOrientation(long targetEpochMillis) {
		if (!locked)
			throw new IllegalStateException("not locked - call lock(...) first");

		double hoursElapsed = (targetEpochMillis - lockEpochMillis) / 3_600_000.0;
		double raDelta = fixDegrees(hoursElapsed * trackingRateDegreesPerHour);

		double[] lockBoresight = RotationVector.altAzToXYZ(lockAltitude, lockAzimuth);
		double[] lockUp = BoresightAngles.upVectorFromRoll(lockBoresight, lockBaseRoll);

		double[] newBoresight = rotateXyz(lockBoresight, raDelta);
		double[] newUp = rotateXyz(lockUp, raDelta);

		ObjectDirectionAltAz center = RotationVector.xyzToAltAz(newBoresight);
		double computedRoll = BoresightAngles.rollDegreesFromUpVector(newBoresight, newUp);

		return new Orientation(center.getAltitude(), center.getAzimuth(), computedRoll);
	}

	@Override
	public void stateChanged(Object source, ObjectStateChangeListener listener) {
		// Notification-only hook satisfying the listener-registration contract (see CLAUDE.md's
		// "Orientation-transformer listener architecture"). compute()/computeLockedOrientation()
		// are pure given the current lock state and a target timestamp, so there is nothing to
		// cache here - callers driving a render loop can call compute() directly after an
		// ObservationTime change; this method exists so instances can still be registered via
		// ObservationTime.addListener(this) per the shared architecture, and so a UI layer has a
		// well-defined point to hook a repaint trigger onto if/when one exists.
	}

	// Rotates a raw XYZ unit vector by raDelta around the polar axis implied by lockLatitude - the
	// same rigid transform applied identically to the boresight AND (see computeLockedOrientation(...))
	// the up-vector, which is exactly what makes deriving roll from the rotated up-vector exact.
	private double[] rotateXyz(double[] xyz, double raDelta) {
		double[] rotated = RotationVector.transformXAxis(xyz, 90.0 - lockLatitude);
		rotated = RotationVector.transformZAxis(rotated, -raDelta);
		rotated = RotationVector.transformXAxis(rotated, -90.0 + lockLatitude);
		return rotated;
	}

	private static double fixDegrees(double degrees) {
		double result = degrees % 360.0;
		if (result < 0.0)
			result += 360.0;
		return result;
	}
}
