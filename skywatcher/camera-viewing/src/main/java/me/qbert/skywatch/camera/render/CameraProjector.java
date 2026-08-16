package me.qbert.skywatch.camera.render;

import java.awt.geom.Point2D;

import me.qbert.skywatch.camera.orientation.BoresightAngles;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;

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

// Ties BoresightAngles' lens-agnostic (theta, phi) decomposition together with a CameraProjection's
// lens-specific theta -> sensor-millimeter mapping and a canvas-pixel scale, to answer "where on
// screen does this target direction render."
//
// The pixel-scale convention is the user's own explicit instruction: the virtual reference sensor
// is ALWAYS treated as CameraProjection.SENSOR_WIDTH_MILLIMETERS (36mm, "35mm-equivalent") wide,
// regardless of the actual canvas/screen resolution - a 1920-pixel-wide canvas is just as much a
// "36mm sensor" as a 640-pixel-wide one, exactly the same convention real SLR-class cameras use
// when reporting zoom as a 35mm-equivalent focal length in EXIF. There is deliberately no separate
// "declared FOV fills the canvas" calibration step - canvas width in pixels is simply divided by
// 36mm. Canvas *height* is not independently calibrated - it falls out of the aspect ratio implied
// by treating width as the fixed 36mm reference.
public final class CameraProjector {
	private CameraProjector() {
	}

	public static double pixelsPerMillimeter(int canvasWidthPixels) {
		if (canvasWidthPixels <= 0)
			throw new IllegalArgumentException("canvasWidthPixels must be positive");
		return canvasWidthPixels / CameraProjection.SENSOR_WIDTH_MILLIMETERS;
	}

	// null if the target falls outside what this lens can represent at all - either theta beyond
	// projection.getMaxAngleRadians() (the lens's hard, focal-length-independent asymptote, 89
	// degrees for a rectilinear lens), OR theta beyond this specific canvas's own ideal corner angle
	// (see idealCornerAngleRadians(...) below). The second check exists for a real, confirmed reason,
	// not as a duplicate of isOnCanvas(...): CameraProjection.sensorRadiusMillimeters(...) applies
	// this projection's barrel/pincushion distortion correction unconditionally when non-identity
	// coefficients are set, and that correction is only ever calibrated near the lens's REAL usable
	// edge - evaluating it at a theta far beyond that edge (e.g. 70+ degrees on a 21mm lens whose
	// true edge is ~40 degrees) extrapolates the correction quartic wildly outside the domain it was
	// fit for. A real user report (with real, previously-calibrated distortion coefficients) showed
	// this extrapolation is NOT just "a bit off" - the quartic can fold back down for large enough
	// normalized radius, producing an artificially SMALL, bogus radius that happens to land back
	// inside canvas bounds. isOnCanvas(...) cannot catch this: it only ever inspects the FINAL x/y,
	// which by that point has already been computed from the corrupted radius - by the time
	// isOnCanvas(...) runs, the damage is done. Rejecting by angle BEFORE ever calling
	// sensorRadiusMillimeters(...) is the only way to keep the distortion polynomial from being
	// evaluated out-of-domain in the first place.
	public static Point2D.Double projectToPixels(CameraProjection projection, Orientation cameraOrientation,
			double targetAltitude, double targetAzimuth, int canvasWidthPixels, int canvasHeightPixels) {
		if (projection == null)
			throw new IllegalArgumentException("projection must not be null");
		if (canvasHeightPixels <= 0)
			throw new IllegalArgumentException("canvasHeightPixels must be positive");

		BoresightAngles.Angles angles = BoresightAngles.decompose(cameraOrientation, targetAltitude, targetAzimuth);
		if (angles.getThetaRadians() > projection.getMaxAngleRadians())
			return null;
		if (angles.getThetaRadians() > idealCornerAngleRadians(projection, canvasWidthPixels, canvasHeightPixels))
			return null;

		double radiusMillimeters = projection.sensorRadiusMillimeters(angles.getThetaRadians());
		double radiusPixels = radiusMillimeters * pixelsPerMillimeter(canvasWidthPixels);

		double centerX = canvasWidthPixels / 2.0;
		double centerY = canvasHeightPixels / 2.0;

		// Screen Y grows downward; phi's "up" (see BoresightAngles) is positive in standard math
		// convention, so it is negated here to land in the correct screen direction.
		double x = centerX + radiusPixels * Math.cos(angles.getPhiRadians());
		double y = centerY - radiusPixels * Math.sin(angles.getPhiRadians());

		return new Point2D.Double(x, y);
	}

	// The angular distance from boresight to THIS canvas's own corner, computed from the IDEAL
	// (undistorted) lens geometry - deliberately never the distorted formula, since using a
	// possibly-distorted inverse here would face the exact extrapolation problem this check exists
	// to avoid (evaluating distortion at an out-of-domain theta to decide whether that same theta is
	// out of domain). Real barrel/pincushion correction is a modest perturbation near the calibrated
	// edge, not a multiple-factor change, so the ideal corner angle is a safe, sound bound for "is
	// this even worth trying to project."
	//
	// The reference sensor's half-width is always the fixed 18mm (half of
	// CameraProjection.SENSOR_WIDTH_MILLIMETERS) - the same convention pixelsPerMillimeter(...) uses.
	// Half-height is derived by scaling that MILLIMETER distance by the canvas aspect ratio (not by
	// scaling an angle - angles don't combine via Pythagoras, but the sensor plane is genuinely
	// Euclidean in millimeters, so the corner radius does). The corner angle is then this
	// projection's own ideal inverse of that corner radius -
	// CameraProjection.angleForSensorRadiusMillimeters(...) is always the undistorted formula (see
	// AbstractCameraProjection), so no new interface method is needed here.
	private static double idealCornerAngleRadians(CameraProjection projection, int canvasWidthPixels, int canvasHeightPixels) {
		double halfWidthMillimeters = CameraProjection.SENSOR_WIDTH_MILLIMETERS / 2.0;
		double halfHeightMillimeters = halfWidthMillimeters * canvasHeightPixels / canvasWidthPixels;
		double cornerRadiusMillimeters = Math.sqrt(halfWidthMillimeters * halfWidthMillimeters
				+ halfHeightMillimeters * halfHeightMillimeters);
		return projection.angleForSensorRadiusMillimeters(cornerRadiusMillimeters);
	}

	// A real user report (with a target well within BOTH of projectToPixels(...)'s rejections above)
	// found that non-null does not automatically mean on-canvas: even after the ideal-corner-angle
	// check, ordinary floating-point/rounding behavior near the true edge can still land a point a
	// pixel or two outside [0, width] x [0, height]. Callers that draw a PERMANENT, point-like glyph
	// or marker (a filled/bounding circle, a crosshair) - as opposed to a connected line/path, where
	// Graphics2D's own clipping already correctly handles a segment that crosses through the frame
	// even with an off-canvas endpoint - must check this in addition to a non-null
	// projectToPixels(...) result before drawing anything. A strict bounds check on the point itself,
	// no margin - matching the user's own exact specification: "if x,y is inside the image
	// coordinate, it's good, outside, don't try to render it or its label."
	public static boolean isOnCanvas(Point2D.Double point, int canvasWidthPixels, int canvasHeightPixels) {
		return point.x >= 0.0 && point.x <= canvasWidthPixels && point.y >= 0.0 && point.y <= canvasHeightPixels;
	}
}
