package me.qbert.skywatch.camera.render;

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

// The sun/moon on-screen sizing fix this whole rewrite was explicitly asked for - see
// CLAUDE.md's "The sun/moon on-screen sizing deficiency". The old prototype used a fixed pixel
// size calibrated for one specific zoom/canvas combination (see the ~line 6267 comment quoted in
// CLAUDE.md); this derives radius fresh from the current FOV/canvas size every time, the same
// shape of fix globe-wrapping-tool's PerspectiveObserverProjection used for its own analogous
// fixed-lens-constant bug.
public final class CelestialObjectSizing {
	private CelestialObjectSizing() {
	}

	// True ~0.52 degree angular diameter for both sun and moon [spec §5] - a deliberate
	// simplification (not distance-varying), not itself part of the sizing bug.
	public static final double SUN_MOON_ANGULAR_DIAMETER_DEGREES = 0.52;

	// canvasSpanPixels/fovDegrees together define the projection's pixels-per-degree at the
	// object's position; angularDiameterDegrees is the object's true angular size;
	// minRadiusPixels is the floor that keeps it visible/identifiable at wide FOV. No upper clamp -
	// at narrow FOV (zoomed in, or a real telephoto lens) the object renders correspondingly
	// larger with no ceiling.
	public static double radiusPixels(double angularDiameterDegrees, double fovDegrees,
			double canvasSpanPixels, double minRadiusPixels) {
		if (fovDegrees <= 0.0)
			throw new IllegalArgumentException("fovDegrees must be positive, was " + fovDegrees);
		if (canvasSpanPixels <= 0.0)
			throw new IllegalArgumentException("canvasSpanPixels must be positive, was " + canvasSpanPixels);
		if (minRadiusPixels < 0.0)
			throw new IllegalArgumentException("minRadiusPixels must not be negative, was " + minRadiusPixels);

		double pixelsPerDegree = canvasSpanPixels / fovDegrees;
		double trueRadiusPixels = (angularDiameterDegrees / 2.0) * pixelsPerDegree;

		return Math.max(trueRadiusPixels, minRadiusPixels);
	}
}
