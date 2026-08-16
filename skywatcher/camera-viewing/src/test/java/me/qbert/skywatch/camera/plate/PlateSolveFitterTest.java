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
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.CameraProjector;
import me.qbert.skywatch.camera.watch.WatchedObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Task 7.2b's automated plate-solve fitter.
class PlateSolveFitterTest {

	private static final long T0 = 1_723_161_600_000L; // 2024-08-09T00:00:00Z - fixed, not "now", so
	// the test is reproducible regardless of when it actually runs (see the flaky
	// SaveLatestCommandTest note in docs/tasks.md for why real "now"-derived positions were flagged
	// as a hazard elsewhere in this module).
	private static final int CANVAS_WIDTH = 800;
	private static final int CANVAS_HEIGHT = 600;

	@Test
	void residualIsNearZeroForAPerfectFit() throws Exception {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		PlateSolveFitter.Candidate truth = new PlateSolveFitter.Candidate(new Orientation(89.0, 0.0, 0.0), 10.0);

		PlateSolveMarkSet marks = new PlateSolveMarkSet();
		marks.add(markFromTruth(truth, WatchedObject.sun(), T0, location));

		double residual = PlateSolveFitter.residual(marks, location, PlateSolveFitterTest::fisheye, truth, CANVAS_WIDTH,
				CANVAS_HEIGHT);

		assertTrue(residual < 1e-6, "a mark generated from the exact candidate should have ~0 residual, was " + residual);
	}

	@Test
	void residualAppliesTheOffLensPenaltyForAnUnrepresentableMark() throws Exception {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		ObjectDirectionAltAz sunAltAz = WatchedObject.sun().resolveAltAz(observationTimeAt(T0), location);

		// A narrow rectilinear lens aimed EXACTLY opposite the sun's real position (theta = 180
		// degrees, always well beyond any rectilinear lens's max angle, which is inherently < 90
		// degrees) - guaranteed unrepresentable regardless of where the sun actually is.
		double antipodeAltitude = -sunAltAz.getAltitude();
		double antipodeAzimuth = normalizeAzimuth(sunAltAz.getAzimuth() + 180.0);
		PlateSolveFitter.Candidate narrowLens = new PlateSolveFitter.Candidate(
				new Orientation(antipodeAltitude, antipodeAzimuth, 0.0), 200.0);

		PlateSolveMarkSet marks = new PlateSolveMarkSet();
		marks.add(new PlateSolveMark(T0, WatchedObject.sun(), 0.5, 0.5));

		double residual = PlateSolveFitter.residual(marks, location, PlateSolveFitterTest::rectilinear, narrowLens,
				CANVAS_WIDTH, CANVAS_HEIGHT);

		assertEquals(PlateSolveFitter.OFF_LENS_PENALTY_PIXELS, residual, 0.0001);
	}

	@Test
	void fitConvergesTowardAKnownPlantedAnswer() throws Exception {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		PlateSolveFitter.Candidate truth = new PlateSolveFitter.Candidate(new Orientation(89.0, 0.0, 5.0), 10.0);

		PlateSolveMarkSet marks = new PlateSolveMarkSet();
		marks.add(markFromTruth(truth, WatchedObject.sun(), T0, location));
		marks.add(markFromTruth(truth, WatchedObject.moon(), T0, location));
		marks.add(markFromTruth(truth, WatchedObject.sun(), T0 + 3_600_000L, location));
		marks.add(markFromTruth(truth, WatchedObject.moon(), T0 + 7_200_000L, location));

		PlateSolveFitter.Candidate initialGuess = new PlateSolveFitter.Candidate(
				new Orientation(truth.getOrientation().getAltitude() - 3.0, truth.getOrientation().getAzimuth() + 4.0,
						truth.getOrientation().getBarrelRoll() - 2.0),
				truth.getFocalLengthMillimeters() + 5.0);
		double initialResidual = PlateSolveFitter.residual(marks, location, PlateSolveFitterTest::fisheye, initialGuess,
				CANVAS_WIDTH, CANVAS_HEIGHT);

		PlateSolveFitter.Result result = PlateSolveFitter.fit(marks, location, PlateSolveFitterTest::fisheye,
				initialGuess, CANVAS_WIDTH, CANVAS_HEIGHT, 42L, 4000);

		assertTrue(result.getResidualPixels() < initialResidual,
				"the fit must actually improve on the initial guess (" + initialResidual + " -> " + result.getResidualPixels() + ")");
		assertTrue(result.getResidualPixels() < 3.0,
				"expected the search to converge close to the exact planted answer, residual was " + result.getResidualPixels());
	}

	@Test
	void rejectsAnEmptyMarkSet() {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		PlateSolveFitter.Candidate guess = new PlateSolveFitter.Candidate(new Orientation(0.0, 0.0, 0.0), 50.0);

		assertThrows(IllegalArgumentException.class, () -> PlateSolveFitter.fit(new PlateSolveMarkSet(), location,
				PlateSolveFitterTest::rectilinear, guess, CANVAS_WIDTH, CANVAS_HEIGHT, 0L, 10));
	}

	@Test
	void candidateRejectsANonPositiveFocalLength() {
		assertThrows(IllegalArgumentException.class, () -> new PlateSolveFitter.Candidate(new Orientation(0.0, 0.0, 0.0), 0.0));
		assertThrows(IllegalArgumentException.class, () -> new PlateSolveFitter.Candidate(new Orientation(0.0, 0.0, 0.0), -5.0));
	}

	@Test
	void fitRejectsNegativeIterations() {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		PlateSolveFitter.Candidate guess = new PlateSolveFitter.Candidate(new Orientation(0.0, 0.0, 0.0), 50.0);
		PlateSolveMarkSet marks = new PlateSolveMarkSet();
		marks.add(new PlateSolveMark(T0, WatchedObject.sun(), 0.5, 0.5));

		assertThrows(IllegalArgumentException.class, () -> PlateSolveFitter.fit(marks, location,
				PlateSolveFitterTest::rectilinear, guess, CANVAS_WIDTH, CANVAS_HEIGHT, 0L, -1));
	}

	// Builds a mark at the exact pixel position "truth" predicts for the given object/time -
	// simulating a perfect, noise-free click, so fitConvergesTowardAKnownPlantedAnswer can verify
	// the search actually recovers (a candidate close to) the planted answer.
	private PlateSolveMark markFromTruth(PlateSolveFitter.Candidate truth, WatchedObject object, long epochMillis,
			ObserverLocation location) throws Exception {
		ObservationTime time = observationTimeAt(epochMillis);
		ObjectDirectionAltAz altAz = object.resolveAltAz(time, location);
		CameraProjection projection = fisheye(truth.getFocalLengthMillimeters());

		Point2D.Double pixel = CameraProjector.projectToPixels(projection, truth.getOrientation(), altAz.getAltitude(),
				altAz.getAzimuth(), CANVAS_WIDTH, CANVAS_HEIGHT);
		if (pixel == null)
			throw new IllegalStateException("test setup error: " + object.getDisplayName() + " at " + epochMillis
					+ " is not representable by the truth candidate's lens");

		return PlateSolveMark.fromPixelClick(epochMillis, object, pixel.x, pixel.y, CANVAS_WIDTH, CANVAS_HEIGHT);
	}

	// A near-full-sphere fisheye (just short of PI) rather than an ordinary 180-degree one - used
	// for a boresight aimed near zenith so every mark stays representable regardless of whatever
	// the sun/moon's real altitude actually is at the fixed test epochs (including below the
	// horizon - this test only cares about the fitter recovering a planted answer, not about
	// modeling a real, physically sensible lens).
	private static CameraProjection fisheye(double focalLengthMillimeters) {
		return new FisheyeProjection(focalLengthMillimeters, Math.PI * 0.999);
	}

	private static CameraProjection rectilinear(double focalLengthMillimeters) {
		return new RectilinearProjection(focalLengthMillimeters);
	}

	private static double normalizeAzimuth(double azimuthDegrees) {
		double result = azimuthDegrees % 360.0;
		return result < 0.0 ? result + 360.0 : result;
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
