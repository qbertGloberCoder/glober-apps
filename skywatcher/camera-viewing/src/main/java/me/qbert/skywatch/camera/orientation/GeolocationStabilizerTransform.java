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

// New - supersedes spec §7.3's "lock modes" entirely (see CLAUDE.md's "Geolocation stabilizer
// transformer"). The location-side mirror of EquatorialMountTransform: EQ mount corrects for time
// at a fixed location (rotates the locked pointing around the polar axis by elapsed-time x rate);
// this corrects for location at a fixed time (rotates the locked pointing around the same polar
// axis by a LONGITUDE delta instead, and additionally re-tilts using the CURRENT latitude instead
// of always the lock latitude). Reuses RotationVector directly - the same XYZ rotation primitives
// ported for the equatorial mount - rather than a separate implementation, since the two
// transforms are genuinely the same underlying geometry parameterized differently.
//
// Three independently toggleable axes (RA/Dec/roll - see CLAUDE.md), confirmed semantics:
// a locked axis is pinned to its celestial-frame value at engagement and recomputed on every
// location change; an unlocked axis is a passthrough, left exactly as it was at engagement, never
// recomputed.
//
// RA/Dec-to-coordinate mapping, corrected a round after the swap-in-doubt round above (found via a
// real user report: verified numerically that the code matched its OWN documented mapping exactly,
// but that mapping - "RA lock ~ latitude-driven... Dec lock ~ longitude-driven" - was backwards from
// the natural celestial-sphere analogy declination IS the celestial equivalent of latitude, right
// ascension IS the celestial equivalent of longitude/hour-angle. Confirmed with the user directly
// and swapped: Dec-lock now responds to LATITUDE changes, RA-lock now responds to LONGITUDE changes.
// This was a genuine, deliberate interpretive-judgment correction (the original mapping was flagged
// as unconfirmed when first built), not a mechanical bug - the UI's checkbox labels/wiring never
// needed to change, only which physical rotation each flag gates internally, below.
//
// Roll fix (same round as EquatorialMountTransform's identical fix - see that class's own comment
// for the full root-cause writeup): the old rollDerivative(...) used the SAME flawed differential-
// bearing trick the EQ mount had - atan2 of raw (altitude, azimuth) coordinate differences, which
// silently accumulates error since that's not a flat coordinate space. Fixed the same way: rotate
// the camera's actual 3D "up" vector (BoresightAngles.upVectorFromRoll(...)) by the exact same
// rotateForRoll(...) transform already applied to points, then read roll back out via
// BoresightAngles.rollDegreesFromUpVector(...). One extra subtlety here versus the EQ mount: roll's
// own rotateForRoll(...) transform is DELIBERATELY independent of whatever rotateForAxes(...) used
// for the reported altitude/azimuth (see that method's own comment - roll must respond to ANY
// location change even when RA/Dec are both unlocked and the boresight itself stays fixed, per
// CLAUDE.md's own worked example). rollDegreesFromUpVector(...) handles this correctly by
// PROJECTING the rotated up-vector onto the plane perpendicular to whatever boresight is actually
// being reported, rather than requiring the two to already be exactly perpendicular - exactly the
// right operation, since "roll" is precisely "how far has up rotated around this specific
// boresight," which remains well-defined even when the up-vector's own rotation was driven by a
// different transform than the boresight's.
public class GeolocationStabilizerTransform implements OrientationTransformer, ObjectStateChangeListener {
	private boolean engaged = false;
	private double lockAltitude;
	private double lockAzimuth;
	private double lockBaseRoll;
	private double lockLatitude;
	private double lockLongitude;

	private boolean raLocked = false;
	private boolean decLocked = false;
	private boolean rollLocked = false;

	public void setRaLocked(boolean raLocked) {
		this.raLocked = raLocked;
	}

	public boolean isRaLocked() {
		return raLocked;
	}

	public void setDecLocked(boolean decLocked) {
		this.decLocked = decLocked;
	}

	public boolean isDecLocked() {
		return decLocked;
	}

	public void setRollLocked(boolean rollLocked) {
		this.rollLocked = rollLocked;
	}

	public boolean isRollLocked() {
		return rollLocked;
	}

	public boolean isEngaged() {
		return engaged;
	}

	// Captures the current orientation + location as the reference point every locked axis is
	// held against. With all three axes unlocked, the stabilizer is engaged but produces no
	// visible effect - a valid, expected state, not an error case (see CLAUDE.md).
	public void engage(Orientation currentOrientation, double latitude, double longitude) {
		if (currentOrientation == null)
			throw new IllegalArgumentException("currentOrientation must not be null");

		this.lockAltitude = currentOrientation.getAltitude();
		this.lockAzimuth = currentOrientation.getAzimuth();
		this.lockBaseRoll = currentOrientation.getBarrelRoll();
		this.lockLatitude = latitude;
		this.lockLongitude = longitude;
		this.engaged = true;
	}

	public void disengage() {
		engaged = false;
	}

	@Override
	public Orientation compute(ObservationTime time, ObserverLocation location, Orientation baseOrientation) {
		if (!engaged)
			return baseOrientation;

		return computeEngagedOrientation(location.getLatitude(), location.getLongitude());
	}

	// Exposed directly so callers/tests can evaluate at an arbitrary location without a live
	// ObserverLocation.
	public Orientation computeEngagedOrientation(double currentLatitude, double currentLongitude) {
		if (!engaged)
			throw new IllegalStateException("not engaged - call engage(...) first");

		double[] lockBoresightXyz = RotationVector.altAzToXYZ(lockAltitude, lockAzimuth);

		double altitude = lockAltitude;
		double azimuth = lockAzimuth;
		double[] centerXyz = lockBoresightXyz;

		if (raLocked || decLocked) {
			centerXyz = rotateForAxes(lockBoresightXyz, currentLatitude, currentLongitude);
			ObjectDirectionAltAz center = RotationVector.xyzToAltAz(centerXyz);
			altitude = center.getAltitude();
			azimuth = center.getAzimuth();
		}

		double roll = lockBaseRoll;
		if (rollLocked) {
			double[] lockUpXyz = BoresightAngles.upVectorFromRoll(lockBoresightXyz, lockBaseRoll);
			double[] newUpXyz = rotateForRoll(lockUpXyz, currentLatitude, currentLongitude);
			roll = BoresightAngles.rollDegreesFromUpVector(centerXyz, newUpXyz);
		}

		return new Orientation(altitude, azimuth, roll);
	}

	@Override
	public void stateChanged(Object source, ObjectStateChangeListener listener) {
		// Notification-only hook, same rationale as EquatorialMountTransform.stateChanged() -
		// compute()/computeEngagedOrientation() are pure given the current lock state and a
		// target location, so there's nothing to cache here.
	}

	// Dec-lock: exits the polar frame using the CURRENT latitude (so altitude responds to latitude
	// changes - declination is the celestial-sphere analog of latitude). RA-lock: rotates around the
	// polar axis by the longitude delta before exiting (so azimuth/altitude respond to longitude
	// changes - right ascension is the celestial-sphere analog of longitude/hour angle) - see the
	// class comment on why this mapping was swapped from an earlier round. Returns raw XYZ (not
	// alt/az) so the identical transform can be reused for rotating either a point or the up-vector.
	private double[] rotateForAxes(double[] xyz, double currentLatitude, double currentLongitude) {
		double[] rotated = RotationVector.transformXAxis(xyz, 90.0 - lockLatitude);

		if (raLocked)
			rotated = RotationVector.transformZAxis(rotated, -(currentLongitude - lockLongitude));

		double exitLatitude = decLocked ? currentLatitude : lockLatitude;
		rotated = RotationVector.transformXAxis(rotated, -90.0 + exitLatitude);

		return rotated;
	}

	// Roll's own rotation always applies both the latitude and longitude deltas, independent of
	// whether RA/Dec are separately locked - "locking barrel roll... the ground will appear to
	// rotate under your sight line as location changes" (CLAUDE.md) describes a response to
	// location generally, not gated on the other two axes. Returns raw XYZ - see
	// computeEngagedOrientation(...)'s own comment on why this is now applied to the up-vector
	// rather than a synthetic differential-offset point.
	private double[] rotateForRoll(double[] xyz, double currentLatitude, double currentLongitude) {
		double[] rotated = RotationVector.transformXAxis(xyz, 90.0 - lockLatitude);
		rotated = RotationVector.transformZAxis(rotated, -(currentLongitude - lockLongitude));
		rotated = RotationVector.transformXAxis(rotated, -90.0 + currentLatitude);
		return rotated;
	}
}
