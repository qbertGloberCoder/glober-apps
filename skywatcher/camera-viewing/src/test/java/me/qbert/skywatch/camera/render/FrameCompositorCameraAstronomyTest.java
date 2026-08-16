package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.astro.CameraAstronomy;
import me.qbert.skywatch.camera.catalog.StarCatalogTier;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.RectilinearProjection;

// Proves the CameraAstronomy-backed compose(...) overload (Item 0) renders PIXEL-IDENTICAL output
// to the original ObservationTime/ObserverLocation-based overload, with every celestial-object and
// graticule layer enabled at once - the point of threading astronomy through is to eliminate
// per-render object construction, not to change what gets composited.
class FrameCompositorCameraAstronomyTest {

	private static final int CANVAS_SIZE = 200;
	private static final long EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z
	private static final double LATITUDE = 45.0;
	private static final double LONGITUDE = -75.0;

	private static List<StarCoordinate> sampleCatalog() {
		List<StarCoordinate> stars = new ArrayList<StarCoordinate>();
		stars.add(new StarCoordinate("Test Star", "TS1", 1.0, 83.822, -5.391, 1, true));
		stars.add(new StarCoordinate("Other Star", "TS2", 3.0, 88.793, 7.407, 1, true));
		return stars;
	}

	private static ObservationTime observationTimeAt(long epochMillis) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		return time;
	}

	private static ObserverLocation observerLocationAt(double latitude, double longitude) {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);
		return location;
	}

	private static int[] pixels(BufferedImage image) {
		int[] pixels = new int[CANVAS_SIZE * CANVAS_SIZE];
		image.getRGB(0, 0, CANVAS_SIZE, CANVAS_SIZE, pixels, 0, CANVAS_SIZE);
		return pixels;
	}

	private static FrameCompositor.Options fullOptions(List<StarCoordinate> stars) {
		return new FrameCompositor.Options()
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(stars)
				.setShowSun(true)
				.setShowMoon(true)
				.setShowPlanets(true)
				.setShowStars(true)
				.setShowGraticule(true)
				.setShowCelestialOrigin(true)
				.setGraticuleStepDegrees(30.0, 15.0);
	}

	@Test
	void composeWithAstronomyMatchesTheOriginalOverloadExactlyEverythingEnabled() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(20.0);
		Orientation camera = new Orientation(20.0, 90.0, 0.0);

		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(LATITUDE, LONGITUDE);
		BufferedImage original = FrameCompositor.compose(projection, camera, time, location, fullOptions(sampleCatalog()));

		CameraAstronomy astronomy = new CameraAstronomy(TimeZone.getTimeZone("UTC"), sampleCatalog());
		astronomy.setStarMode(StarCatalogTier.ALL);
		astronomy.applyTimeAndLocation(EPOCH_MILLIS, LATITUDE, LONGITUDE);
		BufferedImage viaAstronomy = FrameCompositor.compose(projection, camera, astronomy.getObservationTime(),
				astronomy.getObserverLocation(), fullOptions(Collections.emptyList()), astronomy);

		assertArrayEquals(pixels(original), pixels(viaAstronomy));
	}

	// Item 7b - the astronomy-routed ecliptic/analemma path (via EclipticAnalemmaPath's own
	// CameraAstronomy overload) must also be pixel-identical to the original.
	@Test
	void composeWithAstronomyMatchesTheOriginalOverloadExactlyWithSunAndMoonPathsEnabled() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(20.0);
		Orientation camera = new Orientation(20.0, 90.0, 0.0);
		FrameCompositor.Options options = fullOptions(Collections.emptyList())
				.setSunPathMode(EclipticAnalemmaMode.ECLIPTIC)
				.setMoonPathMode(EclipticAnalemmaMode.ANALEMMA);

		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(LATITUDE, LONGITUDE);
		BufferedImage original = FrameCompositor.compose(projection, camera, time, location, options);

		CameraAstronomy astronomy = new CameraAstronomy(TimeZone.getTimeZone("UTC"), Collections.emptyList());
		astronomy.applyTimeAndLocation(EPOCH_MILLIS, LATITUDE, LONGITUDE);
		BufferedImage viaAstronomy = FrameCompositor.compose(projection, camera, astronomy.getObservationTime(),
				astronomy.getObserverLocation(), options, astronomy);

		assertArrayEquals(pixels(original), pixels(viaAstronomy));
	}
}
