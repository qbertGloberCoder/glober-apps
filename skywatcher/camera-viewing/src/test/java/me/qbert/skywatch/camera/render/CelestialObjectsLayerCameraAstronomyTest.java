package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.astro.CameraAstronomy;
import me.qbert.skywatch.camera.catalog.StarCatalogTier;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Proves the new CameraAstronomy-backed overloads (Item 0's shared-instance architecture) render
// PIXEL-IDENTICAL output to the original ObservationTime/ObserverLocation-based overloads for the
// same underlying time/location/orientation - the whole point of these overloads is to eliminate
// per-call object construction, not to change what gets drawn. Real end-to-end comparisons (both
// paths go through the actual CameraProjector/ArcRenderer pipeline), not formula-level checks.
class CelestialObjectsLayerCameraAstronomyTest {

	private static final int CANVAS_SIZE = 200;
	private static final long TEST_EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z
	private static final double LATITUDE = 45.0;
	private static final double LONGITUDE = -75.0;

	private static List<StarCoordinate> sampleCatalog() {
		List<StarCoordinate> stars = new ArrayList<StarCoordinate>();
		stars.add(new StarCoordinate("Test Star", "TS1", 1.0, 83.822, -5.391, 1, true));
		stars.add(new StarCoordinate("Other Star", "TS2", 3.0, 88.793, 7.407, 1, true));
		return stars;
	}

	private static CameraAstronomy astronomyAt(long epochMillis, double latitude, double longitude) throws Exception {
		CameraAstronomy astronomy = new CameraAstronomy(TimeZone.getTimeZone("UTC"), sampleCatalog());
		astronomy.setStarMode(StarCatalogTier.ALL);
		astronomy.applyTimeAndLocation(epochMillis, latitude, longitude);
		return astronomy;
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

	private static BufferedImage blankImage() {
		BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();
		return image;
	}

	private static int[] pixels(BufferedImage image) {
		int[] pixels = new int[CANVAS_SIZE * CANVAS_SIZE];
		image.getRGB(0, 0, CANVAS_SIZE, CANVAS_SIZE, pixels, 0, CANVAS_SIZE);
		return pixels;
	}

	@Test
	void paintSunMatchesTheOriginalOverloadExactly() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(LATITUDE, LONGITUDE);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		BufferedImage original = blankImage();
		Graphics2D g1 = original.createGraphics();
		CelestialObjectsLayer.paintSun(g1, time, location, projection, camera, ColorPresets.defaultScheme(),
				CANVAS_SIZE, CANVAS_SIZE, 5.0, true);
		g1.dispose();

		CameraAstronomy astronomy = astronomyAt(TEST_EPOCH_MILLIS, LATITUDE, LONGITUDE);
		BufferedImage viaAstronomy = blankImage();
		Graphics2D g2 = viaAstronomy.createGraphics();
		CelestialObjectsLayer.paintSun(g2, astronomy, projection, camera, ColorPresets.defaultScheme(), CANVAS_SIZE,
				CANVAS_SIZE, 5.0, true);
		g2.dispose();

		assertArrayEquals(pixels(original), pixels(viaAstronomy));
	}

	@Test
	void paintMoonMatchesTheOriginalOverloadExactly() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(35.0, 139.0);
		CelestialObject moon = me.qbert.skywatch.astro.impl.MoonObject.create()
				.setObserverLocation(location).setObserverTime(time).build();
		moon.recompute();
		ObjectDirectionAltAz moonAltAz = moon.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(moonAltAz.getAltitude(), moonAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		BufferedImage original = blankImage();
		Graphics2D g1 = original.createGraphics();
		CelestialObjectsLayer.paintMoon(g1, time, location, projection, camera, ColorPresets.defaultScheme(),
				CANVAS_SIZE, CANVAS_SIZE, 5.0, true);
		g1.dispose();

		CameraAstronomy astronomy = astronomyAt(TEST_EPOCH_MILLIS, 35.0, 139.0);
		BufferedImage viaAstronomy = blankImage();
		Graphics2D g2 = viaAstronomy.createGraphics();
		CelestialObjectsLayer.paintMoon(g2, astronomy, projection, camera, ColorPresets.defaultScheme(), CANVAS_SIZE,
				CANVAS_SIZE, 5.0, true);
		g2.dispose();

		assertArrayEquals(pixels(original), pixels(viaAstronomy));
	}

	@Test
	void paintPlanetsMatchesTheOriginalOverloadExactly() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(LATITUDE, LONGITUDE);
		me.qbert.skywatch.astro.impl.SolarObjects solarObjects = (me.qbert.skywatch.astro.impl.SolarObjects) me.qbert.skywatch.astro.impl.SolarObjects
				.create().setObserverLocation(location).setObserverTime(time).build();
		solarObjects.recompute();
		solarObjects.setObjectIndex(4); // Jupiter
		ObjectDirectionAltAz jupiterAltAz = solarObjects.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(jupiterAltAz.getAltitude(), jupiterAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		BufferedImage original = blankImage();
		Graphics2D g1 = original.createGraphics();
		CelestialObjectsLayer.paintPlanets(g1, time, location, projection, camera, ColorPresets.defaultScheme(),
				CANVAS_SIZE, CANVAS_SIZE, true);
		g1.dispose();

		CameraAstronomy astronomy = astronomyAt(TEST_EPOCH_MILLIS, LATITUDE, LONGITUDE);
		BufferedImage viaAstronomy = blankImage();
		Graphics2D g2 = viaAstronomy.createGraphics();
		CelestialObjectsLayer.paintPlanets(g2, astronomy, projection, camera, ColorPresets.defaultScheme(),
				CANVAS_SIZE, CANVAS_SIZE, true);
		g2.dispose();

		assertArrayEquals(pixels(original), pixels(viaAstronomy));
	}

	@Test
	void paintStarsMatchesTheOriginalOverloadExactly() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(LATITUDE, LONGITUDE);
		List<StarCoordinate> stars = sampleCatalog();

		me.qbert.skywatch.astro.CelestialObject starObject = me.qbert.skywatch.astro.impl.StarObject.create()
				.setStarLocation(address(stars.get(0))).setObserverLocation(location).setObserverTime(time).build();
		starObject.recompute();
		ObjectDirectionAltAz starAltAz = starObject.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(starAltAz.getAltitude(), starAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		BufferedImage original = blankImage();
		Graphics2D g1 = original.createGraphics();
		CelestialObjectsLayer.paintStars(g1, stars, time, location, projection, camera, CANVAS_SIZE, CANVAS_SIZE, true,
				true);
		g1.dispose();

		CameraAstronomy astronomy = astronomyAt(TEST_EPOCH_MILLIS, LATITUDE, LONGITUDE);
		BufferedImage viaAstronomy = blankImage();
		Graphics2D g2 = viaAstronomy.createGraphics();
		CelestialObjectsLayer.paintStars(g2, astronomy, projection, camera, CANVAS_SIZE, CANVAS_SIZE, true, true);
		g2.dispose();

		assertArrayEquals(pixels(original), pixels(viaAstronomy));
	}

	@Test
	void paintStarsRespectsWhicheverTierIsCurrentlyActive() throws Exception {
		CameraAstronomy astronomy = astronomyAt(TEST_EPOCH_MILLIS, LATITUDE, LONGITUDE);
		StarCoordinate testStar = sampleCatalog().get(0);
		CelestialObject referenceStar = me.qbert.skywatch.astro.impl.StarObject.create()
				.setStarLocation(address(testStar))
				.setObserverLocation(observerLocationAt(LATITUDE, LONGITUDE))
				.setObserverTime(observationTimeAt(TEST_EPOCH_MILLIS)).build();
		referenceStar.recompute();
		ObjectDirectionAltAz starAltAz = referenceStar.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(starAltAz.getAltitude(), starAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		astronomy.setStarMode(StarCatalogTier.ALL);
		BufferedImage withAllTier = blankImage();
		Graphics2D g1 = withAllTier.createGraphics();
		CelestialObjectsLayer.paintStars(g1, astronomy, projection, camera, CANVAS_SIZE, CANVAS_SIZE, false, true);
		g1.dispose();

		astronomy.setStarMode(StarCatalogTier.VISIBLE_ONLY);
		BufferedImage withVisibleOnlyTier = blankImage();
		Graphics2D g2 = withVisibleOnlyTier.createGraphics();
		CelestialObjectsLayer.paintStars(g2, astronomy, projection, camera, CANVAS_SIZE, CANVAS_SIZE, false, true);
		g2.dispose();

		// Both sample stars are visible=true, so VISIBLE_ONLY and ALL render identically here - the
		// real assertion is that switching tiers doesn't crash and both actually draw the star.
		assertArrayEquals(pixels(withAllTier), pixels(withVisibleOnlyTier));

		// cameraImageShown=true draws an UNFILLED bounding circle (its own center stays un-painted by
		// design - see CelestialObjectsLayerTest's own "must not fill its own center" case), so check
		// for the outline near the boresight instead of the center pixel itself.
		int centerX = CANVAS_SIZE / 2;
		int centerY = CANVAS_SIZE / 2;
		int blankRgb = blankImage().getRGB(centerX, centerY);
		boolean foundOutline = false;
		for (int x = centerX; x < CANVAS_SIZE; x++) {
			if (withAllTier.getRGB(x, centerY) != blankRgb) {
				foundOutline = true;
				break;
			}
		}
		assertTrue(foundOutline, "the star must actually render (as an outline) near the camera's boresight");
	}

	@Test
	void paintStarsIsANoOpForAnEmptyActiveTierRatherThanErroring() throws Exception {
		CameraAstronomy astronomy = new CameraAstronomy(TimeZone.getTimeZone("UTC"), Collections.emptyList());
		astronomy.applyTimeAndLocation(TEST_EPOCH_MILLIS, LATITUDE, LONGITUDE);
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);

		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();
		CelestialObjectsLayer.paintStars(g2d, astronomy, projection, camera, CANVAS_SIZE, CANVAS_SIZE, false, true);
		g2d.dispose();

		assertArrayEquals(pixels(blankImage()), pixels(image));
	}

	private me.qbert.skywatch.model.CelestialAddress address(StarCoordinate star) {
		me.qbert.skywatch.model.CelestialAddress address = new me.qbert.skywatch.model.CelestialAddress();
		address.setAddress(star.getRightAscension(), star.getDeclination());
		return address;
	}
}
