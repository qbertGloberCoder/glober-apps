package me.qbert.skywatch.camera.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RectilinearProjectionTest {

	@Test
	void rejectsNonPositiveFocalLength() {
		assertThrows(IllegalArgumentException.class, () -> new RectilinearProjection(0.0));
		assertThrows(IllegalArgumentException.class, () -> new RectilinearProjection(-50.0));
	}

	@Test
	void centerMapsToTheSensorsCenter() {
		RectilinearProjection projection = new RectilinearProjection(50.0);

		assertEquals(0.0, projection.sensorRadiusMillimeters(0.0), 1e-9);
	}

	@Test
	void aFiftyMillimeterLensMatchesItsKnownRealWorldFieldOfView() {
		// A 50mm lens on a 36mm-wide full-frame sensor has a well-known ~39.6-degree horizontal
		// field of view - confirms the formula against a real, independently-verifiable reference
		// point rather than only checking self-consistency.
		RectilinearProjection projection = new RectilinearProjection(50.0);

		double halfWidthMillimeters = CameraProjection.SENSOR_WIDTH_MILLIMETERS / 2.0;
		double halfFovDegrees = Math.toDegrees(projection.angleForSensorRadiusMillimeters(halfWidthMillimeters));

		assertEquals(19.8, halfFovDegrees, 0.1);
	}

	@Test
	void sensorRadiusAndAngleAreInverses() {
		RectilinearProjection projection = new RectilinearProjection(35.0);

		double theta = Math.toRadians(15.0);
		double radius = projection.sensorRadiusMillimeters(theta);
		double recoveredTheta = projection.angleForSensorRadiusMillimeters(radius);

		assertEquals(theta, recoveredTheta, 1e-9);
	}

	@Test
	void radiusGrowsWithoutBoundApproachingTheMaxAngle() {
		RectilinearProjection projection = new RectilinearProjection(50.0);

		double nearMax = projection.sensorRadiusMillimeters(projection.getMaxAngleRadians());
		double halfway = projection.sensorRadiusMillimeters(projection.getMaxAngleRadians() / 2.0);

		assertTrue(nearMax > halfway * 5.0, "tan(theta) grows much faster than linearly as theta approaches 90 degrees");
	}

	@Test
	void withFocalLengthReturnsAnIndependentInstanceAtTheNewFocalLength() {
		// A real user report: "zoom" had no effect anywhere in the rendering pipeline, because
		// nothing ever called this method at all before it existed - see CameraProjection.
		// withFocalLength(...)'s own class comment.
		RectilinearProjection original = new RectilinearProjection(50.0);

		CameraProjection zoomed = original.withFocalLength(100.0);

		assertEquals(100.0, ((RectilinearProjection) zoomed).getFocalLengthMillimeters(), 1e-9);
		assertEquals(50.0, original.getFocalLengthMillimeters(), 1e-9, "the original instance must be unchanged");
	}

	// The user's own real, previously-calibrated coefficients for a south-facing camera.
	private static final double CALIBRATED_A = -0.015173144276557696;
	private static final double CALIBRATED_B = -0.026200973539670214;
	private static final double CALIBRATED_C = 9.254249203305798E-4;
	private static final double CALIBRATED_D = 1.0578540561260015;

	@Test
	void defaultDistortionCoefficientsAreIdentity() {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		double theta = Math.toRadians(15.0);

		assertEquals(0.0, projection.getDistortionCoefficientA(), 1e-12);
		assertEquals(0.0, projection.getDistortionCoefficientB(), 1e-12);
		assertEquals(0.0, projection.getDistortionCoefficientC(), 1e-12);
		assertEquals(1.0, projection.getDistortionCoefficientD(), 1e-12);
		// The identity default must be a genuine no-op against the ideal (un-normalized) formula.
		assertEquals(50.0 * Math.tan(theta), projection.sensorRadiusMillimeters(theta), 1e-9);
	}

	@Test
	void realCalibratedCoefficientsMeasurablyChangeTheSensorRadius() {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		double theta = Math.toRadians(20.0);
		double idealRadius = projection.sensorRadiusMillimeters(theta);

		projection.setDistortionCoefficients(CALIBRATED_A, CALIBRATED_B, CALIBRATED_C, CALIBRATED_D);
		double distortedRadius = projection.sensorRadiusMillimeters(theta);

		assertTrue(Math.abs(distortedRadius - idealRadius) > 1e-6,
				"the user's real barrel-distortion coefficients must actually change where a point lands");
	}

	// The inverse deliberately IGNORES distortion, always - see AbstractCameraProjection's own class
	// comment: the two per-pixel consumers (GroundFill, EquirectangularSceneRenderer) never need it
	// to match a real photo, and the FOV-sizing consumer already tolerates the approximation. This is
	// NOT a round trip against sensorRadiusMillimeters(...) when distortion is non-identity - it
	// always recovers the IDEAL angle for a given radius, not the theta that actually produced that
	// (now-distorted) radius.
	@Test
	void theInverseAlwaysIgnoresDistortionAndReturnsTheIdealAngle() {
		RectilinearProjection projection = new RectilinearProjection(35.0);
		double radius = 10.0;
		double idealAngle = Math.atan(radius / 35.0);

		double angleBeforeDistortion = projection.angleForSensorRadiusMillimeters(radius);
		projection.setDistortionCoefficients(CALIBRATED_A, CALIBRATED_B, CALIBRATED_C, CALIBRATED_D);
		double angleAfterDistortion = projection.angleForSensorRadiusMillimeters(radius);

		assertEquals(idealAngle, angleBeforeDistortion, 1e-12);
		assertEquals(idealAngle, angleAfterDistortion, 1e-12, "the inverse must not change when distortion is configured");
	}

	@Test
	void distortionForwardInverseRoundTripRecoversTheOriginalThetaForIdentityCoefficientsOnly() {
		RectilinearProjection projection = new RectilinearProjection(35.0);
		double theta = Math.toRadians(12.0);

		double radius = projection.sensorRadiusMillimeters(theta);
		double recoveredTheta = projection.angleForSensorRadiusMillimeters(radius);

		assertEquals(theta, recoveredTheta, 1e-9);
	}

	@Test
	void distortionEnabledDefaultsToTrue() {
		RectilinearProjection projection = new RectilinearProjection(50.0);

		assertTrue(projection.isDistortionEnabled());
	}

	@Test
	void disablingDistortionMakesSensorRadiusIgnoreTheCoefficientsEvenWhenSet() {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		double theta = Math.toRadians(20.0);
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
		RectilinearProjection original = new RectilinearProjection(50.0);
		original.setDistortionCoefficients(CALIBRATED_A, CALIBRATED_B, CALIBRATED_C, CALIBRATED_D);
		original.setDistortionEnabled(false);

		RectilinearProjection zoomed = (RectilinearProjection) original.withFocalLength(100.0);

		assertEquals(CALIBRATED_A, zoomed.getDistortionCoefficientA(), 1e-12);
		assertEquals(CALIBRATED_B, zoomed.getDistortionCoefficientB(), 1e-12);
		assertEquals(CALIBRATED_C, zoomed.getDistortionCoefficientC(), 1e-12);
		assertEquals(CALIBRATED_D, zoomed.getDistortionCoefficientD(), 1e-12);
		assertFalse(zoomed.isDistortionEnabled());
	}
}
