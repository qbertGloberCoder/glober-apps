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

// Ported from org.bluerock.astro.AstroCoordinates' altAzToXYZ/transformXAxis/transformZAxis/
// xyzToAltAz (see CLAUDE.md's porting-notes table row for astro.EquatorialMount) - the XYZ unit-
// vector rotation math EquatorialMountTransform needs. Confirmed before porting: sw-base's
// GeoCalculator does alt-az/RA-Dec conversion via direct spherical trig, not via a rotation-vector
// approach, and has no equivalent of this utility anywhere in this reactor. Cleaned up into plain
// static methods (the old class carried these as instance methods on a much larger,
// stateful AstroCoordinates god-adjacent class for no reason relevant here) - the math itself is
// unchanged from the original.
final class RotationVector {
	private RotationVector() {
	}

	static double[] altAzToXYZ(double altitude, double azimuth) {
		double[] xyz = new double[3];

		xyz[0] = Math.cos(Math.toRadians(altitude)) * Math.cos(Math.toRadians(90.0 - azimuth));
		xyz[1] = Math.cos(Math.toRadians(altitude)) * Math.sin(Math.toRadians(90.0 - azimuth));
		xyz[2] = Math.sin(Math.toRadians(altitude));

		return xyz;
	}

	static double[] transformXAxis(double[] xyz, double angle) {
		if (angle == 0)
			return xyz;

		double radius = Math.sqrt(xyz[1] * xyz[1] + xyz[2] * xyz[2]);
		double angleRad = Math.atan2(xyz[2], xyz[1]) + Math.toRadians(angle);

		double[] newXyz = new double[3];
		newXyz[1] = radius * Math.cos(angleRad);
		newXyz[2] = radius * Math.sin(angleRad);
		newXyz[0] = xyz[0];

		return newXyz;
	}

	static double[] transformZAxis(double[] xyz, double angle) {
		if (angle == 0)
			return xyz;

		double radius = Math.sqrt(xyz[1] * xyz[1] + xyz[0] * xyz[0]);
		double angleRad = Math.atan2(xyz[1], xyz[0]) + Math.toRadians(angle);

		double[] newXyz = new double[3];
		newXyz[0] = radius * Math.cos(angleRad);
		newXyz[1] = radius * Math.sin(angleRad);
		newXyz[2] = xyz[2];

		return newXyz;
	}

	static ObjectDirectionAltAz xyzToAltAz(double[] xyz) {
		double altitude = Math.toDegrees(Math.atan2(xyz[2], Math.sqrt(xyz[0] * xyz[0] + xyz[1] * xyz[1])));
		double azimuth = fixDegrees(450.0 - Math.toDegrees(Math.atan2(xyz[1], xyz[0])));

		ObjectDirectionAltAz altAz = new ObjectDirectionAltAz();
		altAz.setAltitude(altitude);
		altAz.setAzimuth(azimuth);
		return altAz;
	}

	private static double fixDegrees(double degrees) {
		double result = degrees % 360.0;
		if (result < 0.0)
			result += 360.0;
		return result;
	}
}
