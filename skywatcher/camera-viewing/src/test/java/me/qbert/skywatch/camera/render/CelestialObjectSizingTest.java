package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// docs/tasks.md task 4.2: "Unit test across FOV extremes (~120 degree wide-field down to a tight
// telephoto-equivalent config): minimum-clamp holds at the wide end, true proportional scaling
// holds at the narrow end, no discontinuity in between."
class CelestialObjectSizingTest {

	private static final double MIN_RADIUS_PIXELS = 5.0;
	private static final double CANVAS_SPAN_PIXELS = 1080.0;

	@Test
	void wideFieldOfViewClampsToTheMinimumRadius() {
		// 120 degree FOV: true radius would be sub-pixel-scale, well under the floor.
		double radius = CelestialObjectSizing.radiusPixels(
				CelestialObjectSizing.SUN_MOON_ANGULAR_DIAMETER_DEGREES, 120.0, CANVAS_SPAN_PIXELS, MIN_RADIUS_PIXELS);

		assertEquals(MIN_RADIUS_PIXELS, radius, 0.0001,
				"at 120 degree FOV the true angular radius is far below the floor, so the floor must win");
	}

	@Test
	void narrowTelephotoFieldOfViewUsesTrueProportionalScalingNotTheFloor() {
		// A tight ~2 degree telephoto-equivalent FOV.
		double fovDegrees = 2.0;
		double radius = CelestialObjectSizing.radiusPixels(
				CelestialObjectSizing.SUN_MOON_ANGULAR_DIAMETER_DEGREES, fovDegrees, CANVAS_SPAN_PIXELS, MIN_RADIUS_PIXELS);

		double expectedTrueRadius = (CelestialObjectSizing.SUN_MOON_ANGULAR_DIAMETER_DEGREES / 2.0)
				* (CANVAS_SPAN_PIXELS / fovDegrees);

		assertTrue(radius > MIN_RADIUS_PIXELS * 10,
				"at a tight telephoto FOV the true size should be far larger than the wide-field floor");
		assertEquals(expectedTrueRadius, radius, 0.0001,
				"narrow FOV should use the true proportional size, not the floor");
	}

	@Test
	void noUpperClampAtEvenNarrowerFieldsOfView() {
		double radiusAt2Deg = CelestialObjectSizing.radiusPixels(
				CelestialObjectSizing.SUN_MOON_ANGULAR_DIAMETER_DEGREES, 2.0, CANVAS_SPAN_PIXELS, MIN_RADIUS_PIXELS);
		double radiusAt0Point5Deg = CelestialObjectSizing.radiusPixels(
				CelestialObjectSizing.SUN_MOON_ANGULAR_DIAMETER_DEGREES, 0.5, CANVAS_SPAN_PIXELS, MIN_RADIUS_PIXELS);

		assertTrue(radiusAt0Point5Deg > radiusAt2Deg * 3,
				"there is no ceiling - a real telephoto lens should keep growing the rendered size");
	}

	@Test
	void radiusIsMonotonicAndContinuousAcrossTheClampBoundary() {
		// Sweep from wide (120deg) to narrow (0.1deg) FOV in small steps and confirm the radius
		// never decreases as FOV narrows, and never jumps discontinuously across the point where
		// the floor stops binding. The function is genuinely steep at narrow FOV (radius is
		// proportional to 1/fov), so bounding "no discontinuity" by an absolute per-step pixel
		// delta is the wrong tool - a real, correct, continuous curve can still take large steps
		// there. Instead assert the closed-form definition directly at every sample: that's a
		// stronger and more accurate check than inferring smoothness from finite differences.
		double previousRadius = 0.0;

		for (double fov = 120.0; fov >= 0.1; fov -= 0.25) {
			double radius = CelestialObjectSizing.radiusPixels(
					CelestialObjectSizing.SUN_MOON_ANGULAR_DIAMETER_DEGREES, fov, CANVAS_SPAN_PIXELS, MIN_RADIUS_PIXELS);

			double trueRadius = (CelestialObjectSizing.SUN_MOON_ANGULAR_DIAMETER_DEGREES / 2.0) * (CANVAS_SPAN_PIXELS / fov);
			double expectedRadius = Math.max(trueRadius, MIN_RADIUS_PIXELS);
			assertEquals(expectedRadius, radius, 0.0001, "radius must match the closed-form definition exactly at FOV=" + fov);

			assertTrue(radius >= previousRadius - 0.0001,
					"radius must not decrease as FOV narrows (at FOV=" + fov + ")");

			previousRadius = radius;
		}
	}

	@Test
	void rejectsNonPositiveFovOrCanvasSpan() {
		assertThrows(IllegalArgumentException.class,
				() -> CelestialObjectSizing.radiusPixels(0.52, 0.0, CANVAS_SPAN_PIXELS, MIN_RADIUS_PIXELS));
		assertThrows(IllegalArgumentException.class,
				() -> CelestialObjectSizing.radiusPixels(0.52, 10.0, 0.0, MIN_RADIUS_PIXELS));
		assertThrows(IllegalArgumentException.class,
				() -> CelestialObjectSizing.radiusPixels(0.52, 10.0, CANVAS_SPAN_PIXELS, -1.0));
	}
}
