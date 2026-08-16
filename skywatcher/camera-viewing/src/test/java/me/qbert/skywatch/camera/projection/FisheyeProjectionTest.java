package me.qbert.skywatch.camera.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FisheyeProjectionTest {

	@Test
	void rejectsNonPositiveFocalLength() {
		assertThrows(IllegalArgumentException.class, () -> new FisheyeProjection(0.0, Math.PI / 2.0));
	}

	@Test
	void rejectsAMaxAngleOutsideZeroToPi() {
		assertThrows(IllegalArgumentException.class, () -> new FisheyeProjection(10.0, 0.0));
		assertThrows(IllegalArgumentException.class, () -> new FisheyeProjection(10.0, Math.PI + 0.001));
	}

	@Test
	void radiusIsLinearInAngleUnlikeARectilinearLens() {
		FisheyeProjection projection = new FisheyeProjection(10.0, Math.PI / 2.0);

		double radiusAt10Degrees = projection.sensorRadiusMillimeters(Math.toRadians(10.0));
		double radiusAt20Degrees = projection.sensorRadiusMillimeters(Math.toRadians(20.0));
		double radiusAt40Degrees = projection.sensorRadiusMillimeters(Math.toRadians(40.0));

		assertEquals(radiusAt20Degrees, radiusAt10Degrees * 2.0, 1e-9);
		assertEquals(radiusAt40Degrees, radiusAt10Degrees * 4.0, 1e-9);
	}

	@Test
	void aOneEightyDegreeFisheyeCapsAtANinetyDegreeHalfAngle() {
		FisheyeProjection oneEighty = new FisheyeProjection(10.0, Math.PI / 2.0);

		assertEquals(Math.PI / 2.0, oneEighty.getMaxAngleRadians(), 1e-9);
	}

	@Test
	void aThreeSixtyDegreeFisheyeCapsAtAOneEightyDegreeHalfAngle() {
		FisheyeProjection threeSixty = new FisheyeProjection(10.0, Math.PI);

		assertEquals(Math.PI, threeSixty.getMaxAngleRadians(), 1e-9);
		// A genuinely finite radius even at the extreme edge - the whole point of the linear
		// mapping vs. a rectilinear lens's asymptote.
		double edgeRadius = threeSixty.sensorRadiusMillimeters(threeSixty.getMaxAngleRadians());
		assertEquals(10.0 * Math.PI, edgeRadius, 1e-9);
	}

	@Test
	void sensorRadiusAndAngleAreInverses() {
		FisheyeProjection projection = new FisheyeProjection(8.0, Math.PI);

		double theta = Math.toRadians(150.0);
		double radius = projection.sensorRadiusMillimeters(theta);
		double recoveredTheta = projection.angleForSensorRadiusMillimeters(radius);

		assertEquals(theta, recoveredTheta, 1e-9);
	}

	@Test
	void withFocalLengthKeepsTheMaxAngleButChangesTheFocalLength() {
		FisheyeProjection original = new FisheyeProjection(10.0, Math.PI / 2.0);

		CameraProjection zoomed = original.withFocalLength(20.0);

		assertEquals(20.0, ((FisheyeProjection) zoomed).getFocalLengthMillimeters(), 1e-9);
		assertEquals(Math.PI / 2.0, zoomed.getMaxAngleRadians(), 1e-9, "maxAngleRadians must be preserved, not reset");
		assertEquals(10.0, original.getFocalLengthMillimeters(), 1e-9, "the original instance must be unchanged");
	}

	// The user's own real, previously-calibrated coefficients for a south-facing camera - reused
	// here too, since AbstractCameraProjection's correction is lens-type-agnostic by design.
	private static final double CALIBRATED_A = -0.015173144276557696;
	private static final double CALIBRATED_B = -0.026200973539670214;
	private static final double CALIBRATED_C = 9.254249203305798E-4;
	private static final double CALIBRATED_D = 1.0578540561260015;

	@Test
	void defaultDistortionCoefficientsAreIdentity() {
		FisheyeProjection projection = new FisheyeProjection(10.0, Math.PI / 2.0);
		double theta = Math.toRadians(30.0);

		assertEquals(0.0, projection.getDistortionCoefficientA(), 1e-12);
		assertEquals(0.0, projection.getDistortionCoefficientB(), 1e-12);
		assertEquals(0.0, projection.getDistortionCoefficientC(), 1e-12);
		assertEquals(1.0, projection.getDistortionCoefficientD(), 1e-12);
		assertEquals(10.0 * theta, projection.sensorRadiusMillimeters(theta), 1e-9);
	}

	@Test
	void realCalibratedCoefficientsMeasurablyChangeTheSensorRadius() {
		FisheyeProjection projection = new FisheyeProjection(10.0, Math.PI / 2.0);
		double theta = Math.toRadians(40.0);
		double idealRadius = projection.sensorRadiusMillimeters(theta);

		projection.setDistortionCoefficients(CALIBRATED_A, CALIBRATED_B, CALIBRATED_C, CALIBRATED_D);
		double distortedRadius = projection.sensorRadiusMillimeters(theta);

		assertTrue(Math.abs(distortedRadius - idealRadius) > 1e-6,
				"the user's real barrel-distortion coefficients must actually change where a point lands");
	}

	// The inverse deliberately IGNORES distortion, always - see AbstractCameraProjection's own class
	// comment (same reasoning as RectilinearProjectionTest's matching case): GroundFill/
	// EquirectangularSceneRenderer never need it to match a real photo, and the FOV-sizing consumer
	// already tolerates the approximation.
	@Test
	void theInverseAlwaysIgnoresDistortionAndReturnsTheIdealAngle() {
		FisheyeProjection projection = new FisheyeProjection(8.0, Math.PI);
		double radius = 5.0;
		double idealAngle = radius / 8.0;

		double angleBeforeDistortion = projection.angleForSensorRadiusMillimeters(radius);
		projection.setDistortionCoefficients(CALIBRATED_A, CALIBRATED_B, CALIBRATED_C, CALIBRATED_D);
		double angleAfterDistortion = projection.angleForSensorRadiusMillimeters(radius);

		assertEquals(idealAngle, angleBeforeDistortion, 1e-12);
		assertEquals(idealAngle, angleAfterDistortion, 1e-12, "the inverse must not change when distortion is configured");
	}

	@Test
	void distortionEnabledDefaultsToTrue() {
		FisheyeProjection projection = new FisheyeProjection(10.0, Math.PI / 2.0);

		assertTrue(projection.isDistortionEnabled());
	}

	@Test
	void disablingDistortionMakesSensorRadiusIgnoreTheCoefficientsEvenWhenSet() {
		FisheyeProjection projection = new FisheyeProjection(10.0, Math.PI / 2.0);
		double theta = Math.toRadians(40.0);
		double idealRadius = projection.sensorRadiusMillimeters(theta);

		projection.setDistortionCoefficients(CALIBRATED_A, CALIBRATED_B, CALIBRATED_C, CALIBRATED_D);
		assertTrue(Math.abs(projection.sensorRadiusMillimeters(theta) - idealRadius) > 1e-6,
				"sanity check: distortion is actually active before disabling it");

		projection.setDistortionEnabled(false);
		assertEquals(idealRadius, projection.sensorRadiusMillimeters(theta), 1e-9,
				"disabled distortion must fall back to the ideal, un-distorted radius");
	}

	@Test
	void withFocalLengthPreservesTheCurrentDistortionCoefficientsAndEnabledState() {
		FisheyeProjection original = new FisheyeProjection(10.0, Math.PI / 2.0);
		original.setDistortionCoefficients(CALIBRATED_A, CALIBRATED_B, CALIBRATED_C, CALIBRATED_D);
		original.setDistortionEnabled(false);

		FisheyeProjection zoomed = (FisheyeProjection) original.withFocalLength(20.0);

		assertEquals(CALIBRATED_A, zoomed.getDistortionCoefficientA(), 1e-12);
		assertEquals(CALIBRATED_B, zoomed.getDistortionCoefficientB(), 1e-12);
		assertEquals(CALIBRATED_C, zoomed.getDistortionCoefficientC(), 1e-12);
		assertEquals(CALIBRATED_D, zoomed.getDistortionCoefficientD(), 1e-12);
		assertFalse(zoomed.isDistortionEnabled());
	}
}
