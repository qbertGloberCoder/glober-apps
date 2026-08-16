package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.projection.RectilinearProjection;

class CameraProjectorTest {

	@Test
	void pixelsPerMillimeterIsAlwaysAgainstTheFixedThirtySixMillimeterSensorWidth() {
		// The user's own convention: a 1920-wide canvas is just as much a "36mm sensor" as a
		// 640-wide one - always divide by 36, never a separate FOV-calibration step.
		assertEquals(1920.0 / 36.0, CameraProjector.pixelsPerMillimeter(1920), 1e-9);
		assertEquals(640.0 / 36.0, CameraProjector.pixelsPerMillimeter(640), 1e-9);
	}

	@Test
	void rejectsNonPositiveCanvasWidth() {
		assertThrows(IllegalArgumentException.class, () -> CameraProjector.pixelsPerMillimeter(0));
	}

	@Test
	void aTargetAtTheBoresightProjectsToTheExactCanvasCenter() {
		RectilinearProjection projection = new RectilinearProjection(60.0);
		Orientation camera = new Orientation(20.0, 130.0, 0.0);

		Point2D.Double point = CameraProjector.projectToPixels(projection, camera, 20.0, 130.0, 1920, 1080);

		// A tiny floating-point epsilon in theta (acos of a dot product very close to but not
		// exactly 1.0) amplifies through tan() and the pixel scale - tolerate a fraction of a
		// pixel, not true exactness.
		assertEquals(960.0, point.x, 0.01);
		assertEquals(540.0, point.y, 0.01);
	}

	@Test
	void aKnownOffsetMatchesTheHandComputedPixelPosition() {
		// The user's own worked example: 1920x1080, a 60mm lens "to approximate human zoom level".
		// A level camera facing due north (az=0); a target 10 degrees higher at the same azimuth
		// appears directly above center (phi=90 degrees, per BoresightAnglesTest).
		RectilinearProjection projection = new RectilinearProjection(60.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);

		Point2D.Double point = CameraProjector.projectToPixels(projection, camera, 10.0, 0.0, 1920, 1080);

		double expectedRadiusMillimeters = 60.0 * Math.tan(Math.toRadians(10.0));
		double expectedRadiusPixels = expectedRadiusMillimeters * (1920.0 / 36.0);

		assertEquals(960.0, point.x, 0.5, "straight up in-frame must not drift horizontally");
		assertEquals(540.0 - expectedRadiusPixels, point.y, 0.5);
	}

	@Test
	void aTargetBeyondTheLensMaxAngleReturnsNull() {
		RectilinearProjection projection = new RectilinearProjection(60.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);

		// Directly behind the camera - theta = 180 degrees, far beyond any rectilinear lens's cap.
		Point2D.Double point = CameraProjector.projectToPixels(projection, camera, 0.0, 180.0, 1920, 1080);

		assertNull(point);
	}

	@Test
	void aFisheyeLensCanRepresentAnAngleTheRectilinearLensRejects() {
		RectilinearProjection rectilinear = new RectilinearProjection(8.0);
		FisheyeProjection fisheye = new FisheyeProjection(8.0, Math.PI);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);

		// theta = 120 degrees for this target - beyond the rectilinear lens's 89-degree cap, but
		// well within a 360-degree fisheye's range.
		assertNull(CameraProjector.projectToPixels(rectilinear, camera, -60.0, 180.0, 1920, 1080));
		assertNotNull(CameraProjector.projectToPixels(fisheye, camera, -60.0, 180.0, 1920, 1080));
	}

	// The user's own real, previously-calibrated coefficients for a south-facing camera (same values
	// as RectilinearProjectionTest/FisheyeProjectionTest) - real barrel distortion, not a synthetic
	// test fixture.
	private static final double CALIBRATED_A = -0.015173144276557696;
	private static final double CALIBRATED_B = -0.026200973539670214;
	private static final double CALIBRATED_C = 9.254249203305798E-4;
	private static final double CALIBRATED_D = 1.0578540561260015;

	// A real user report: a target at theta ~70 degrees (well within RectilinearProjection's
	// 89-degree getMaxAngleRadians() cap, but far beyond a 21mm lens's real ~40.6-degree edge) was
	// rendering ON canvas instead of being rejected. Root cause -
	// AbstractCameraProjection.sensorRadiusMillimeters(...) applies the distortion quartic
	// unconditionally, and at this theta the normalized radius (~3.17) is wildly outside the domain
	// the coefficients were calibrated for - the quartic folds back down instead of continuing to
	// grow, producing an artificially SMALL radius that happens to land back inside canvas bounds.
	// isOnCanvas(...) alone cannot catch this since it only inspects the final x/y, computed AFTER
	// the corrupted radius. This test pins the fix: projectToPixels(...) must reject by the ideal
	// (undistorted) corner angle BEFORE ever calling the distorted sensorRadiusMillimeters(...).
	@Test
	void aTargetBeyondTheRealEdgeIsRejectedEvenWithDistortionCoefficientsThatWouldOtherwiseFoldItBackOnCanvas() {
		RectilinearProjection projection = new RectilinearProjection(21.0);
		projection.setDistortionCoefficients(CALIBRATED_A, CALIBRATED_B, CALIBRATED_C, CALIBRATED_D);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);

		// theta ~70.4 degrees off boresight (in-lens-cap, off-real-edge) - level with the camera,
		// offset in azimuth only, matching the shape of the user's own real reproduction.
		Point2D.Double point = CameraProjector.projectToPixels(projection, camera, 0.0, 70.4, 2560, 1440);

		assertNull(point,
				"a target beyond the real (ideal) corner angle must be rejected before the distorted "
						+ "radius formula is ever evaluated, regardless of what that formula's own "
						+ "out-of-domain extrapolation would otherwise compute");
	}

	@Test
	void theIdealCornerRejectionDoesNotAffectAnUndistortedProjection() {
		// Same theta as above, but with identity (default) distortion - confirms the new corner
		// check itself, independent of the distortion-extrapolation bug, correctly rejects a target
		// beyond a 21mm lens's real edge on a 2560x1440 canvas.
		RectilinearProjection projection = new RectilinearProjection(21.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);

		Point2D.Double point = CameraProjector.projectToPixels(projection, camera, 0.0, 70.4, 2560, 1440);

		assertNull(point);
	}

	// The user's own mockup (src/test/.../CameraProjectorNotAJUnit.java, converted here): a general
	// sanity sweep for the whole class of bug the two tests above pin a single point of. A level
	// camera facing due south (alt=0, az=180); sweeping the target from zenith (alt=90) down to the
	// horizon (alt=0) at the SAME azimuth means theta decreases monotonically the whole way (theta
	// equals the altitude difference exactly, since target and boresight share an azimuth), so the
	// projected y coordinate must decrease (move toward center, then to it) just as monotonically for
	// every point actually reported on-canvas - any decrease-then-increase ("fold back") among
	// on-canvas points is exactly the distortion-extrapolation symptom the round-5 fix addresses,
	// whether or not it happens to reproduce at this exact theta/canvas/coefficient combination.
	// Points beyond the lens cap (null) or beyond the real edge but still off-canvas are skipped, not
	// asserted on - only the on-canvas subsequence needs to be monotonic.
	@Test
	void screenYNeverFoldsBackAsATargetSweepsMonotonicallyFromZenithToTheHorizon() {
		RectilinearProjection projection = new RectilinearProjection(21.0);
		projection.setDistortionCoefficients(CALIBRATED_A, CALIBRATED_B, CALIBRATED_C, CALIBRATED_D);
		Orientation camera = new Orientation(0.0, 180.0, 0.0);
		int canvasWidthPixels = 1920;
		int canvasHeightPixels = 1080;

		double lastOnCanvasY = Double.NEGATIVE_INFINITY;
		int onCanvasCount = 0;
		for (double altitude = 90.0; altitude >= 0.0; altitude -= 1.0) {
			Point2D.Double point = CameraProjector.projectToPixels(projection, camera, altitude, 180.0,
					canvasWidthPixels, canvasHeightPixels);
			if (point == null || !CameraProjector.isOnCanvas(point, canvasWidthPixels, canvasHeightPixels))
				continue;

			assertTrue(point.y >= lastOnCanvasY,
					"altitude=" + altitude + ": screen y (" + point.y + ") folded back below the previous "
							+ "on-canvas point (" + lastOnCanvasY + ") even though the target is monotonically "
							+ "approaching boresight - a sign the distorted radius formula was evaluated "
							+ "out of its calibrated domain");
			lastOnCanvasY = point.y;
			onCanvasCount++;
		}

		assertTrue(onCanvasCount > 0, "expected at least some altitudes in this sweep to land on canvas");
	}

	@Test
	void canvasHeightIsNotIndependentlyCalibrated() {
		// Only canvas width is tied to the 36mm reference - height falls out of whatever aspect
		// ratio the caller supplies. Confirm a taller canvas at the same width doesn't change the
		// horizontal pixel scale.
		RectilinearProjection projection = new RectilinearProjection(60.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);

		Point2D.Double wide = CameraProjector.projectToPixels(projection, camera, 0.0, 10.0, 1920, 1080);
		Point2D.Double square = CameraProjector.projectToPixels(projection, camera, 0.0, 10.0, 1920, 1920);

		assertEquals(wide.x - 960.0, square.x - 960.0, 1e-6, "horizontal offset from center must match regardless of canvas height");
	}
}
