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

// An equidistant fisheye lens - the user's own description: "the concentric circle angle is a
// linear relationship with the diameter of the circle", i.e. sensorRadius = focalLength * theta
// (theta in radians), unlike RectilinearProjection's tan(theta). This linear relationship is what
// lets a fisheye represent up to a full 180-degree (maxAngleRadians = PI/2, a hemisphere mapped to
// one image circle) or even 360-degree (maxAngleRadians = PI) field of view without the radius
// diverging - straight lines off-center are rendered curved, the defining trade-off of a fisheye.
// This is the IDEAL formula - AbstractCameraProjection layers an optional barrel/pincushion
// correction on top of it.
public final class FisheyeProjection extends AbstractCameraProjection {
	private final double focalLengthMillimeters;
	private final double maxAngleRadians;

	public FisheyeProjection(double focalLengthMillimeters, double maxAngleRadians) {
		if (focalLengthMillimeters <= 0.0)
			throw new IllegalArgumentException("focalLengthMillimeters must be positive");
		if (maxAngleRadians <= 0.0 || maxAngleRadians > Math.PI)
			throw new IllegalArgumentException("maxAngleRadians must be in (0, PI] - PI/2 for a 180-degree fisheye, PI for a 360-degree one");
		this.focalLengthMillimeters = focalLengthMillimeters;
		this.maxAngleRadians = maxAngleRadians;
	}

	public double getFocalLengthMillimeters() {
		return focalLengthMillimeters;
	}

	@Override
	protected double idealSensorRadiusMillimeters(double thetaRadians) {
		return focalLengthMillimeters * thetaRadians;
	}

	@Override
	protected double idealAngleForSensorRadiusMillimeters(double radiusMillimeters) {
		return radiusMillimeters / focalLengthMillimeters;
	}

	@Override
	public double getMaxAngleRadians() {
		return maxAngleRadians;
	}

	// Preserves the CURRENT distortion coefficients (a zoom change shouldn't silently reset a
	// camera's own lens-distortion calibration) and the current distortionEnabled state - see
	// RectilinearProjection.withFocalLength(...)'s matching comment for the full reasoning.
	@Override
	public CameraProjection withFocalLength(double newFocalLengthMillimeters) {
		FisheyeProjection copy = new FisheyeProjection(newFocalLengthMillimeters, maxAngleRadians);
		copy.setDistortionCoefficients(getDistortionCoefficientA(), getDistortionCoefficientB(),
				getDistortionCoefficientC(), getDistortionCoefficientD());
		copy.setDistortionEnabled(isDistortionEnabled());
		return copy;
	}
}
