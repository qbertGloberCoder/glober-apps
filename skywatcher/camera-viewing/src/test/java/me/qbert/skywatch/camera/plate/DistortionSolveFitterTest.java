package me.qbert.skywatch.camera.plate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.render.CameraProjector;
import me.qbert.skywatch.camera.watch.WatchedObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Phase B (sprint Item 2) - fits barrel-distortion coefficients with orientation/zoom held fixed.
class DistortionSolveFitterTest {

	private static final long T0 = 1_723_161_600_000L; // 2024-08-09T00:00:00Z - fixed, reproducible.
	private static final int CANVAS_WIDTH = 800;
	private static final int CANVAS_HEIGHT = 600;
	private static final Orientation FIXED_ORIENTATION = new Orientation(89.0, 0.0, 5.0);

	@Test
	void residualIsNearZeroForAPerfectFit() throws Exception {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		DistortionSolveFitter.Coefficients truth = new DistortionSolveFitter.Coefficients(-0.01, -0.02, 0.001, 1.05);
		FisheyeProjection projection = fisheye();
		projection.setDistortionCoefficients(truth.getA(), truth.getB(), truth.getC(), truth.getD());
		projection.setDistortionEnabled(true);

		PlateSolveMarkSet marks = new PlateSolveMarkSet();
		marks.add(markAt(projection, WatchedObject.sun(), T0, location));

		// residual() itself is private - exercised indirectly via fit() with 0 iterations, which just
		// evaluates the initial guess and returns it unchanged.
		DistortionSolveFitter.Result result = DistortionSolveFitter.fit(marks, location, FIXED_ORIENTATION, projection,
				truth, CANVAS_WIDTH, CANVAS_HEIGHT, 0L, 0);

		assertTrue(result.getResidualPixels() < 1e-6,
				"a mark generated from the exact candidate should have ~0 residual, was " + result.getResidualPixels());
	}

	// Deliberately many marks (up to 12 - sun+moon across 6 timestamps spanning several hours,
	// matching this technique's own real usage pattern - CLAUDE.md's "a few hours' worth" of marks)
	// rather than just a handful. Confirmed directly (a standalone diagnostic, not guessed): with
	// only 4 marks this fit is genuinely UNDERDETERMINED - a/b/c/d can trade off against each other
	// to reach similarly-low residuals from very different coefficient combinations (the search
	// converged to consistently-different, consistently-WRONG answers across many random seeds, not
	// noise) - the same "more unknowns than well-spread constraints" hazard CLAUDE.md's own plate-
	// solving section already flags for the EQ-mount-epoch case. Marks that land off-canvas (still
	// within the lens's max angle, per CameraProjector's own generous bound, but outside the actual
	// frame) are skipped rather than passed to PlateSolveMark, matching a real user's own marking
	// workflow - they can only click on what the photo actually shows.
	@Test
	void fitConvergesTowardAKnownPlantedAnswer() throws Exception {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		DistortionSolveFitter.Coefficients truth = new DistortionSolveFitter.Coefficients(-0.015, -0.026, 0.0009, 1.058);
		FisheyeProjection projection = fisheye();
		projection.setDistortionCoefficients(truth.getA(), truth.getB(), truth.getC(), truth.getD());
		projection.setDistortionEnabled(true);

		PlateSolveMarkSet marks = new PlateSolveMarkSet();
		for (long offsetMillis = 0; offsetMillis <= 18_000_000L; offsetMillis += 3_600_000L) {
			addMarkIfOnCanvas(marks, projection, WatchedObject.sun(), T0 + offsetMillis, location);
			addMarkIfOnCanvas(marks, projection, WatchedObject.moon(), T0 + offsetMillis, location);
		}
		assertTrue(marks.size() >= 6, "test setup error: expected several on-canvas marks, got " + marks.size());

		DistortionSolveFitter.Coefficients initialGuess = new DistortionSolveFitter.Coefficients(0.0, 0.0, 0.0, 1.0);

		DistortionSolveFitter.Result result = DistortionSolveFitter.fit(marks, location, FIXED_ORIENTATION, projection,
				initialGuess, CANVAS_WIDTH, CANVAS_HEIGHT, 42L, 20000);

		assertTrue(result.getResidualPixels() < 3.0,
				"expected the search to converge close to the exact planted answer, residual was " + result.getResidualPixels());
	}

	private void addMarkIfOnCanvas(PlateSolveMarkSet marks, FisheyeProjection projection, WatchedObject object,
			long epochMillis, ObserverLocation location) throws Exception {
		ObservationTime time = observationTimeAt(epochMillis);
		ObjectDirectionAltAz altAz = object.resolveAltAz(time, location);
		Point2D.Double pixel = CameraProjector.projectToPixels(projection, FIXED_ORIENTATION, altAz.getAltitude(),
				altAz.getAzimuth(), CANVAS_WIDTH, CANVAS_HEIGHT);
		if (pixel == null || pixel.x < 0 || pixel.x > CANVAS_WIDTH || pixel.y < 0 || pixel.y > CANVAS_HEIGHT)
			return;
		marks.add(PlateSolveMark.fromPixelClick(epochMillis, object, pixel.x, pixel.y, CANVAS_WIDTH, CANVAS_HEIGHT));
	}

	// A real footgun a mutable-shared-projection design invites: the search must not leave the
	// caller's own projection instance mutated after it returns, regardless of outcome.
	@Test
	void fitRestoresTheProjectionsOriginalDistortionStateRegardlessOfOutcome() throws Exception {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		FisheyeProjection projection = fisheye();
		projection.setDistortionCoefficients(0.001, 0.002, 0.003, 1.01);
		projection.setDistortionEnabled(false);

		PlateSolveMarkSet marks = new PlateSolveMarkSet();
		marks.add(new PlateSolveMark(T0, WatchedObject.sun(), 0.5, 0.5));

		DistortionSolveFitter.Coefficients initialGuess = new DistortionSolveFitter.Coefficients(0.0, 0.0, 0.0, 1.0);
		DistortionSolveFitter.fit(marks, location, FIXED_ORIENTATION, projection, initialGuess, CANVAS_WIDTH,
				CANVAS_HEIGHT, 1L, 50);

		assertEquals(0.001, projection.getDistortionCoefficientA(), 1e-12);
		assertEquals(0.002, projection.getDistortionCoefficientB(), 1e-12);
		assertEquals(0.003, projection.getDistortionCoefficientC(), 1e-12);
		assertEquals(1.01, projection.getDistortionCoefficientD(), 1e-12);
		assertTrue(!projection.isDistortionEnabled(), "distortion-enabled flag must be restored to its original value");
	}

	@Test
	void rejectsAnEmptyMarkSet() {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		DistortionSolveFitter.Coefficients guess = new DistortionSolveFitter.Coefficients(0.0, 0.0, 0.0, 1.0);

		assertThrows(IllegalArgumentException.class, () -> DistortionSolveFitter.fit(new PlateSolveMarkSet(), location,
				FIXED_ORIENTATION, fisheye(), guess, CANVAS_WIDTH, CANVAS_HEIGHT, 0L, 10));
	}

	@Test
	void fitRejectsNegativeIterations() {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		DistortionSolveFitter.Coefficients guess = new DistortionSolveFitter.Coefficients(0.0, 0.0, 0.0, 1.0);
		PlateSolveMarkSet marks = new PlateSolveMarkSet();
		marks.add(new PlateSolveMark(T0, WatchedObject.sun(), 0.5, 0.5));

		assertThrows(IllegalArgumentException.class, () -> DistortionSolveFitter.fit(marks, location, FIXED_ORIENTATION,
				fisheye(), guess, CANVAS_WIDTH, CANVAS_HEIGHT, 0L, -1));
	}

	private PlateSolveMark markAt(FisheyeProjection projection, WatchedObject object, long epochMillis,
			ObserverLocation location) throws Exception {
		ObservationTime time = observationTimeAt(epochMillis);
		ObjectDirectionAltAz altAz = object.resolveAltAz(time, location);

		Point2D.Double pixel = CameraProjector.projectToPixels(projection, FIXED_ORIENTATION, altAz.getAltitude(),
				altAz.getAzimuth(), CANVAS_WIDTH, CANVAS_HEIGHT);
		if (pixel == null)
			throw new IllegalStateException(
					"test setup error: " + object.getDisplayName() + " at " + epochMillis + " is not representable");

		return PlateSolveMark.fromPixelClick(epochMillis, object, pixel.x, pixel.y, CANVAS_WIDTH, CANVAS_HEIGHT);
	}

	// A near-full-sphere fisheye, matching PlateSolveFitterTest's own precedent - keeps marks
	// representable regardless of the sun/moon's real altitude at the fixed test epochs.
	private static FisheyeProjection fisheye() {
		return new FisheyeProjection(10.0, Math.PI * 0.999);
	}

	private ObservationTime observationTimeAt(long epochMillis) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		return time;
	}

	private ObserverLocation observerLocationAt(double latitude, double longitude) {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);
		return location;
	}
}
