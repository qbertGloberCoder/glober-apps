package me.qbert.skywatch.camera.orientation;

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

// The lens-agnostic half of the camera projection model [the user's own design]: decomposes a
// target direction, relative to where the camera is currently pointing, into two angles -
// theta ("concentric ring" angle, the angular distance from the camera's own boresight/pointing
// direction, 0 at dead center and increasing outward) and phi ("radar sweep" angle, the bearing
// around that ring). This step is the same regardless of lens type; me.qbert.skywatch.camera.
// projection.CameraProjection is the lens-specific second step that turns theta into an actual
// image-plane radius.
//
// phi's zero reference is the camera's own "right" direction (after barrel roll is applied), with
// positive phi sweeping toward "up" - i.e. standard atan2(up, right) convention, not a
// compass-style bearing. Lives in the orientation package (rather than projection) specifically to
// reuse RotationVector's altAzToXYZ without widening that package-private class's visibility.
public final class BoresightAngles {
	private BoresightAngles() {
	}

	public static final class Angles {
		private final double thetaRadians;
		private final double phiRadians;

		private Angles(double thetaRadians, double phiRadians) {
			this.thetaRadians = thetaRadians;
			this.phiRadians = phiRadians;
		}

		public double getThetaRadians() {
			return thetaRadians;
		}

		public double getPhiRadians() {
			return phiRadians;
		}
	}

	public static Angles decompose(Orientation cameraOrientation, double targetAltitude, double targetAzimuth) {
		if (cameraOrientation == null)
			throw new IllegalArgumentException("cameraOrientation must not be null");

		double[] boresight = RotationVector.altAzToXYZ(cameraOrientation.getAltitude(), cameraOrientation.getAzimuth());
		double[] target = RotationVector.altAzToXYZ(targetAltitude, targetAzimuth);

		double cosTheta = clampToUnitRange(dot(boresight, target));
		double theta = Math.acos(cosTheta);

		double[] up = upBasis(cameraOrientation, boresight);
		double[] right = cross(boresight, up);

		double[] targetPerpendicular = subtract(target, scale(boresight, cosTheta));
		double x = dot(targetPerpendicular, right);
		double y = dot(targetPerpendicular, up);
		double phi = Math.atan2(y, x);

		return new Angles(theta, phi);
	}

	// The inverse of decompose(...) - given theta/phi relative to a camera's boresight, reconstruct
	// the absolute altitude/azimuth of that direction. Needed by anything that walks the image
	// plane rather than the sky (the ground fill's horizon test, a future plate-solve click-to-sky
	// lookup) - the two are genuine inverses of the same orthonormal (boresight, right, up) basis,
	// not independently-derived math: target = boresight*cos(theta) + right*sin(theta)*cos(phi) +
	// up*sin(theta)*sin(phi).
	public static ObjectDirectionAltAz reconstructAltAz(Orientation cameraOrientation, double thetaRadians,
			double phiRadians) {
		if (cameraOrientation == null)
			throw new IllegalArgumentException("cameraOrientation must not be null");

		double[] boresight = RotationVector.altAzToXYZ(cameraOrientation.getAltitude(), cameraOrientation.getAzimuth());
		double[] up = upBasis(cameraOrientation, boresight);
		double[] right = cross(boresight, up);

		double sinTheta = Math.sin(thetaRadians);
		double[] target = add(scale(boresight, Math.cos(thetaRadians)),
				add(scale(right, sinTheta * Math.cos(phiRadians)), scale(up, sinTheta * Math.sin(phiRadians))));

		return RotationVector.xyzToAltAz(target);
	}

	// The "no roll" up direction is the true zenith projected perpendicular to the boresight, then
	// rotated around the boresight by the camera's own barrel roll.
	private static double[] upBasis(Orientation cameraOrientation, double[] boresight) {
		return upVectorFromRoll(boresight, cameraOrientation.getBarrelRoll());
	}

	// Package-visible so orientation-transformer classes (EquatorialMountTransform,
	// GeolocationStabilizerTransform) can rotate a camera's FULL 3-axis orientation rigidly - boresight
	// AND up-vector, both rotated by the identical 3D transform - instead of re-deriving roll via an
	// error-prone differential-bearing trick evaluated in (altitude, azimuth) coordinates (which are
	// NOT a flat/uniform space - azimuth compresses near the zenith exactly like longitude compresses
	// near Earth's poles, and a naive atan2 of raw alt/az differences ignores that entirely). This is
	// the AUTHORITATIVE definition of what "barrel roll" means throughout this module - the "no roll"
	// up direction is the true zenith projected perpendicular to the boresight, then rotated around
	// the boresight by roll degrees. See EquatorialMountTransform's own class comment for the concrete
	// bug this fixed (barrel roll drifting out of spec, causing stars to visibly rotate around the
	// sight line under equatorial-mount tracking).
	static double[] upVectorFromRoll(double[] boresight, double barrelRollDegrees) {
		double[] zenith = RotationVector.altAzToXYZ(90.0, 0.0);
		double zenithDotBoresight = dot(zenith, boresight);
		double[] upNoRoll;

		if (Math.abs(zenithDotBoresight) > 0.999999) {
			// Camera pointing straight up or down - zenith is degenerate as an "up" reference here.
			// Fall back to due north at the horizon, an arbitrary but stable substitute.
			double[] north = RotationVector.altAzToXYZ(0.0, 0.0);
			double northDotBoresight = dot(north, boresight);
			upNoRoll = normalize(subtract(north, scale(boresight, northDotBoresight)));
		} else {
			upNoRoll = normalize(subtract(zenith, scale(boresight, zenithDotBoresight)));
		}

		double[] rightNoRoll = cross(boresight, upNoRoll);
		double rollRadians = Math.toRadians(barrelRollDegrees);

		return add(scale(upNoRoll, Math.cos(rollRadians)), scale(rightNoRoll, Math.sin(rollRadians)));
	}

	// The exact inverse of upVectorFromRoll(...): given a boresight and an actual "up" 3D vector
	// (e.g. the lock-time up vector, rigidly rotated by the same transform applied to the boresight),
	// find the barrel-roll value (degrees) that reproduces it relative to THIS boresight's own local
	// "no roll" (zenith-projected) reference. upVector is assumed already perpendicular to boresight
	// and unit length (true whenever it was itself produced by rotating a valid upVectorFromRoll(...)
	// result via an orthogonal transform, which is the only way callers use this).
	static double rollDegreesFromUpVector(double[] boresight, double[] upVector) {
		double[] zenith = RotationVector.altAzToXYZ(90.0, 0.0);
		double zenithDotBoresight = dot(zenith, boresight);
		double[] upNoRoll;

		if (Math.abs(zenithDotBoresight) > 0.999999) {
			double[] north = RotationVector.altAzToXYZ(0.0, 0.0);
			double northDotBoresight = dot(north, boresight);
			upNoRoll = normalize(subtract(north, scale(boresight, northDotBoresight)));
		} else {
			upNoRoll = normalize(subtract(zenith, scale(boresight, zenithDotBoresight)));
		}

		double[] rightNoRoll = cross(boresight, upNoRoll);
		double cosRoll = dot(upVector, upNoRoll);
		double sinRoll = dot(upVector, rightNoRoll);

		return Math.toDegrees(Math.atan2(sinRoll, cosRoll));
	}

	private static double clampToUnitRange(double value) {
		return Math.max(-1.0, Math.min(1.0, value));
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {
				a[1] * b[2] - a[2] * b[1],
				a[2] * b[0] - a[0] * b[2],
				a[0] * b[1] - a[1] * b[0]
		};
	}

	private static double[] add(double[] a, double[] b) {
		return new double[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
	}

	private static double[] subtract(double[] a, double[] b) {
		return new double[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
	}

	private static double[] scale(double[] a, double factor) {
		return new double[] { a[0] * factor, a[1] * factor, a[2] * factor };
	}

	private static double[] normalize(double[] a) {
		double length = Math.sqrt(dot(a, a));
		return scale(a, 1.0 / length);
	}
}
