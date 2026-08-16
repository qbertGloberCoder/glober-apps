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

// A standard (non-fisheye) lens - the user's own formula: treating the sensor as the "opposite
// side" of a right triangle whose "adjacent side" is the focal length, the off-boresight angle
// theta satisfies tan(theta) = sensorRadius / focalLength, i.e. sensorRadius = focalLength *
// tan(theta). Straight lines in the world stay straight in the image (the defining property of a
// rectilinear lens) - unlike FisheyeProjection's angle-linear mapping. This is the IDEAL formula -
// AbstractCameraProjection layers an optional barrel/pincushion correction on top of it.
public final class RectilinearProjection extends AbstractCameraProjection {
	// tan(theta) diverges to infinity as theta approaches 90 degrees - no real rectilinear lens
	// reaches a full 180-degree field of view, so cap comfortably short of the asymptote rather
	// than allowing an unbounded/infinite sensor radius.
	private static final double MAX_ANGLE_RADIANS = Math.toRadians(89.0);

	private final double focalLengthMillimeters;

	public RectilinearProjection(double focalLengthMillimeters) {
		if (focalLengthMillimeters <= 0.0)
			throw new IllegalArgumentException("focalLengthMillimeters must be positive");
		this.focalLengthMillimeters = focalLengthMillimeters;
	}

	public double getFocalLengthMillimeters() {
		return focalLengthMillimeters;
	}

	@Override
	protected double idealSensorRadiusMillimeters(double thetaRadians) {
		return focalLengthMillimeters * Math.tan(thetaRadians);
	}

	@Override
	protected double idealAngleForSensorRadiusMillimeters(double radiusMillimeters) {
		return Math.atan(radiusMillimeters / focalLengthMillimeters);
	}
	
	@Override
	public double getMaxAngleRadians() {
		return MAX_ANGLE_RADIANS;
	}

	// Preserves the CURRENT distortion coefficients (a zoom change shouldn't silently reset a
	// camera's own lens-distortion calibration) and the current distortionEnabled state (every real
	// render call site sets this explicitly right after calling withFocalLength(...) anyway, but
	// preserving it here means a caller that doesn't is left with the SOURCE instance's own choice
	// rather than always silently resetting to the default).
	@Override
	public CameraProjection withFocalLength(double newFocalLengthMillimeters) {
		RectilinearProjection copy = new RectilinearProjection(newFocalLengthMillimeters);
		copy.setDistortionCoefficients(getDistortionCoefficientA(), getDistortionCoefficientB(),
				getDistortionCoefficientC(), getDistortionCoefficientD());
		copy.setDistortionEnabled(isDistortionEnabled());
		return copy;
	}
}
