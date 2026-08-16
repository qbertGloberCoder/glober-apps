package me.qbert.skywatch.camera.projection;

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

// Barrel/pincushion distortion correction, built directly into the lens hierarchy [the user's own
// architecture, replacing an earlier "wrapper around CameraProjector" design that was explicitly
// rejected during design discussion]. Each subclass supplies its own ideal, undistorted angle<->
// radius formula (idealSensorRadiusMillimeters/idealAngleForSensorRadiusMillimeters - the exact
// pre-existing RectilinearProjection/FisheyeProjection math, moved here unchanged); this class
// applies a quartic radial correction on top, mirroring render.BarrelDistortion.convert(...)'s own
// polynomial (sourceRadius = a*r^4 + b*r^3 + c*r^2 + d*r) - the SAME formula and SAME identity
// shortcut (a==0 && b==0 && c==0), so a real, previously-calibrated set of coefficients (e.g. from
// the old prototype's own BarrelDistortion dialog) reproduces the identical correction here.
//
// Coefficients are mutable, plain instance state (getDistortionCoefficientA/B/C/D() +
// setDistortionCoefficients(...)) rather than a config.CameraConfig field - they live ON the
// CameraProjection instance itself, the same place focal length already lives, reusing the existing
// projection persistence path (config.CameraConfigStore) instead of a parallel one.
//
// Numerically anchored to a FIXED 18mm half-sensor constant (CameraProjection.
// SENSOR_WIDTH_MILLIMETERS / 2.0), not the real canvas size render.CameraProjector happens to be
// using - because render.CameraProjector's own pixelsPerMillimeter(canvasWidthPixels) =
// canvasWidthPixels / 36.0 convention means half the reference sensor ALWAYS corresponds to exactly
// half the canvas width, for any canvas size, so normalizing by 18mm here produces the identical
// normalized fraction the old prototype's own render.BarrelDistortion produced by normalizing a real
// photo's pixel radius by max(halfWidth, halfHeight) - confirmed algebraically, not just assumed,
// during this round's design discussion. This is the concrete payoff of anchoring the correction
// here rather than in render.CameraProjector: zero changes needed outside this package, since
// CameraProjector already only ever calls sensorRadiusMillimeters(theta) through the interface.
//
// angleForSensorRadiusMillimeters(...) (the INVERSE direction) deliberately never applies distortion
// at all, even when non-identity coefficients are set - it always delegates straight to the
// subclass's own ideal formula. This is a deliberate design decision, not an oversight, reached by
// tracing every real caller of the inverse: render.GroundFill's per-pixel horizon test and render.
// EquirectangularSceneRenderer's per-pixel panorama sampling (both O(width*height) loops) are the
// only two consumers of the inverse besides render.CelestialObjectsLayer's own FOV-for-sizing call
// (which already tolerates a pixel or two of error by its own pre-existing comment). Both of the
// per-pixel consumers are structurally confined to situations where matching a real photo's actual
// lens distortion is moot: GroundFill never runs at all while this camera's own image occupies
// Layer 1 (render.LayerVisibility.isGroundForcedOff(...) is an unconditional rule, not a default a
// manual toggle can override), and EquirectangularSceneRenderer only ever renders a PTZ Virtual
// camera's own live view - PTZ is exclusively Virtual (real cameras are "always Fixed, never PTZ"),
// and distortion correction is meaningless for a Virtual camera's synthetic image in the first place
// (see isDistortionEnabled() below). A quartic has no general closed-form inverse; numerically
// inverting it via bisection (an earlier version of this class did exactly that) would have added
// real per-pixel cost in both hot loops for a correction that provably never needs to be exact in
// either of them.
public abstract class AbstractCameraProjection implements CameraProjection {
	private static final double HALF_SENSOR_WIDTH_MILLIMETERS = SENSOR_WIDTH_MILLIMETERS / 2.0;

	private double distortionA;
	private double distortionB;
	private double distortionC;
	private double distortionD = 1.0;

	// Distortion correction only ever makes sense for a Real camera's live/archived photo, and only
	// while that photo is actually the thing being visually compared against (direct user
	// instruction) - a Virtual camera's image isn't a photograph of anything real, so there's no lens
	// flaw to correct for (pan/tilt/zoom on a PTZ Virtual camera curving straight lines convex-to-
	// concave "adds no visual benefit," in the user's own words), and a Real camera with its image
	// HIDDEN has no photo on screen to align the overlay against either. Defaults to true - the
	// coefficients themselves (identity by default) are the PRIMARY gate for the common case; this
	// flag is the render-call-site override for the specific contexts above. Deliberately NOT
	// persisted via config.CameraConfigStore - this is a per-render decision, not a saved camera
	// property, so every render call site sets it explicitly rather than relying on whatever a
	// freshly-loaded/copied instance happens to default to.
	private boolean distortionEnabled = true;

	// The subclass's own pre-existing, un-distorted formula - RectilinearProjection's
	// focalLength*tan(theta) or FisheyeProjection's focalLength*theta, unchanged.
	protected abstract double idealSensorRadiusMillimeters(double thetaRadians);

	// The exact inverse of idealSensorRadiusMillimeters(...).
	protected abstract double idealAngleForSensorRadiusMillimeters(double radiusMillimeters);

	public double getDistortionCoefficientA() {
		return distortionA;
	}

	public double getDistortionCoefficientB() {
		return distortionB;
	}

	public double getDistortionCoefficientC() {
		return distortionC;
	}

	public double getDistortionCoefficientD() {
		return distortionD;
	}

	// Bundled together, matching render.BarrelDistortion.setCoefficients(...)'s own bundled shape -
	// the four coefficients only make sense set together, not independently.
	public final void setDistortionCoefficients(double a, double b, double c, double d) {
		this.distortionA = a;
		this.distortionB = b;
		this.distortionC = c;
		this.distortionD = d;
	}

	public boolean isDistortionEnabled() {
		return distortionEnabled;
	}

	public void setDistortionEnabled(boolean distortionEnabled) {
		this.distortionEnabled = distortionEnabled;
	}

	private boolean isIdentity() {
		// Matches render.BarrelDistortion's own identity shortcut exactly - it checks only a/b/c,
		// not d, a deliberate carried-over convention rather than an oversight in either class.
		return distortionA == 0.0 && distortionB == 0.0 && distortionC == 0.0;
	}

	private double distort(double normalizedRadius) {
		double r = normalizedRadius;
		return distortionA * r * r * r * r + distortionB * r * r * r + distortionC * r * r + distortionD * r;
	}

	@Override
	public final double sensorRadiusMillimeters(double thetaRadians) {
		double idealRadiusMillimeters = idealSensorRadiusMillimeters(thetaRadians);
		if (!distortionEnabled || isIdentity())
			return idealRadiusMillimeters;
		double normalizedRadius = idealRadiusMillimeters / HALF_SENSOR_WIDTH_MILLIMETERS;
		return distort(normalizedRadius) * HALF_SENSOR_WIDTH_MILLIMETERS;
	}

	// See this class's own comment for why this never applies distortion, regardless of coefficients
	// or isDistortionEnabled() - always the subclass's plain ideal inverse.
	@Override
	public final double angleForSensorRadiusMillimeters(double radiusMillimeters) {
		return idealAngleForSensorRadiusMillimeters(radiusMillimeters);
	}
}
