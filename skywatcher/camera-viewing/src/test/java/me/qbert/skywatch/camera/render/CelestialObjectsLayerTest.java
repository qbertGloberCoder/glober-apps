package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SolarObjects;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Integration tests against the real sw-base astro classes (SunObject/StarObject), the real
// CameraProjector, and the real ga-base ArcRenderer/BufferedImage pipeline - not just formula math
// in isolation, matching this module's established testing convention (see
// CelestialObjectSizingRenderingIntegrationTest).
class CelestialObjectsLayerTest {

	private static final int CANVAS_SIZE = 200;
	private static final long TEST_EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

	@Test
	void sunRendersAsAFilledDiscAtItsComputedPosition() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		// Point the camera exactly at the sun's own computed position, so it must render dead
		// center regardless of the exact ephemeris values - a robust test that doesn't need to
		// hand-verify a real sun position. Must call recompute() explicitly - build() alone leaves
		// the object at its degenerate default position (see CelestialObjectsLayer's gotcha note);
		// skipping this originally let this test pass vacuously against that default in both places.
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);

		RectilinearProjection projection = new RectilinearProjection(50.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		CelestialObjectsLayer.paintSun(g2d, time, location, projection, camera, ColorPresets.defaultScheme(),
				CANVAS_SIZE, CANVAS_SIZE, 5.0);
		g2d.dispose();

		assertEquals(rgbNoAlpha(ColorPresets.defaultScheme().getSunColor()), rgbNoAlpha(image, CANVAS_SIZE / 2, CANVAS_SIZE / 2),
				"the sun should render its own color exactly at its own computed screen position");
	}

	@Test
	void moonRendersAsAFilledDiscInItsOwnColor() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(35.0, 139.0);

		CelestialObject moon = me.qbert.skywatch.astro.impl.MoonObject.create()
				.setObserverLocation(location).setObserverTime(time).build();
		moon.recompute();
		ObjectDirectionAltAz moonAltAz = moon.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(moonAltAz.getAltitude(), moonAltAz.getAzimuth(), 0.0);

		RectilinearProjection projection = new RectilinearProjection(50.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		CelestialObjectsLayer.paintMoon(g2d, time, location, projection, camera, ColorPresets.defaultScheme(),
				CANVAS_SIZE, CANVAS_SIZE, 5.0);
		g2d.dispose();

		assertEquals(rgbNoAlpha(ColorPresets.defaultScheme().getMoonColor()), rgbNoAlpha(image, CANVAS_SIZE / 2, CANVAS_SIZE / 2));
	}

	@Test
	void anObjectBeyondTheLensMaxAngleIsSkippedNotErrored() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		// Point the camera at the sun's antipode - well beyond a rectilinear lens's 89-degree cap.
		Orientation camera = new Orientation(-sunAltAz.getAltitude(), (sunAltAz.getAzimuth() + 180.0) % 360.0, 0.0);

		RectilinearProjection projection = new RectilinearProjection(50.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		CelestialObjectsLayer.paintSun(g2d, time, location, projection, camera, ColorPresets.defaultScheme(),
				CANVAS_SIZE, CANVAS_SIZE, 5.0);
		g2d.dispose();

		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertNotEquals(rgbNoAlpha(ColorPresets.defaultScheme().getSunColor()), rgbNoAlpha(image, x, y),
						"the sun must not appear anywhere on canvas when it's behind the camera");
	}

	// A real user report, precisely diagnosed by the user themselves: an object whose theta was
	// comfortably within the lens's 89-degree cap (so projectToPixels(...) returned a non-null point)
	// still resolved to a screen position thousands of pixels outside the actual canvas - and,
	// contrary to what would seem obvious, this WAS visibly drawn (the exact two-step rejection gap
	// the user named: "compute the angle separation... reject if greater than the corner edge. for
	// those not rejected, compute the local transformation to get x,y and re-evaluate again... if
	// x,y is inside the image coordinate, it's good, outside, don't try to render it").
	//
	// A camera orientation exactly 60 degrees from the real object is constructed via
	// BoresightAngles.reconstructAltAz(...) - pointing a "reference" orientation directly at the
	// object's own real (ephemeris-derived) alt/az, then reconstructing a real alt/az exactly
	// thetaRadians=60deg/phi=0 away from THAT reference, and using the result as the actual test
	// camera's orientation. This guarantees theta=60 degrees between camera and object regardless of
	// the object's own altitude (a naive same-altitude azimuth-offset approach does NOT give a
	// reliable theta - the angular separation for a fixed azimuth difference shrinks as altitude
	// grows, collapsing toward 0 degrees near the poles). 60 degrees is comfortably under the
	// 89-degree lens cap, but a 21mm lens's true edge-of-frame angle is only ~40.6 degrees
	// (atan(18/21)), so this must land off-canvas.
	@Test
	void starAtASixtyDegreeOffsetIsRejectedEvenThoughItsWithinTheLensMaxAngle() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		RectilinearProjection projection = new RectilinearProjection(21.0);

		StarCoordinate star = new StarCoordinate("Off Canvas Star", "OCS1", 1.0, 0.0, 0.0, 1, true);
		CelestialObject starObject = me.qbert.skywatch.astro.impl.StarObject.create()
				.setStarLocation(address(star)).setObserverLocation(location).setObserverTime(time).build();
		starObject.recompute();
		ObjectDirectionAltAz starAltAz = starObject.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = sixtyDegreesAwayFrom(starAltAz);

		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();
		CelestialObjectsLayer.paintStars(g2d, Collections.singletonList(star), time, location, projection,
				camera, CANVAS_SIZE, CANVAS_SIZE, true, true);
		g2d.dispose();

		Color starColor = StarBrightness.grayscaleFor(star.getApparentMagnitude(), star.getApparentMagnitude(),
				star.getApparentMagnitude());
		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertNotEquals(rgbNoAlpha(starColor), rgbNoAlpha(image, x, y),
						"a star 60 degrees off boresight (within the 89-degree lens cap, but past this "
								+ "21mm lens's real ~40.6-degree edge) must not render anywhere on canvas");
	}

	@Test
	void sunAtASixtyDegreeOffsetIsRejectedEvenThoughItsWithinTheLensMaxAngle() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		RectilinearProjection projection = new RectilinearProjection(21.0);

		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = sixtyDegreesAwayFrom(sunAltAz);

		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();
		CelestialObjectsLayer.paintSun(g2d, time, location, projection, camera, ColorPresets.defaultScheme(),
				CANVAS_SIZE, CANVAS_SIZE, 5.0);
		g2d.dispose();

		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertNotEquals(rgbNoAlpha(ColorPresets.defaultScheme().getSunColor()), rgbNoAlpha(image, x, y),
						"the sun 60 degrees off boresight (within the lens cap but past the real frame edge) "
								+ "must not render anywhere on canvas");
	}

	@Test
	void planetAtASixtyDegreeOffsetIsRejectedEvenThoughItsWithinTheLensMaxAngle() throws Exception {
		// paintPlanets(...) draws all 8 planets in one call, so unlike the star/sun tests above this
		// needs an epoch where no OTHER real planet coincidentally also falls within the lens's real
		// edge (~40.6 degrees) - TEST_EPOCH_MILLIS itself has Neptune sitting at only ~22 degrees from
		// this same camera orientation, which would make the canvas-wide scan below find a genuine,
		// correctly-rendered Neptune and misread it as this fix not working. Verified directly (a
		// throwaway diagnostic against every planet's own theta at this exact camera orientation) that
		// this specific epoch keeps every OTHER planet beyond the ~40.6-degree edge (nearest is Mars at
		// ~49 degrees) - Jupiter itself is the only planet within reach of this camera's 21mm frame.
		ObservationTime time = observationTimeAt(1_400_000_000_000L);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		RectilinearProjection projection = new RectilinearProjection(21.0);

		SolarObjects solarObjects = (SolarObjects) SolarObjects.create()
				.setObserverLocation(location).setObserverTime(time).build();
		solarObjects.recompute();
		solarObjects.setObjectIndex(4); // Jupiter
		ObjectDirectionAltAz jupiterAltAz = solarObjects.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = sixtyDegreesAwayFrom(jupiterAltAz);

		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();
		CelestialObjectsLayer.paintPlanets(g2d, time, location, projection, camera, ColorPresets.defaultScheme(),
				CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();

		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertNotEquals(rgbNoAlpha(ColorPresets.defaultScheme().getPlanetColor()), rgbNoAlpha(image, x, y),
						"Jupiter 60 degrees off boresight (within the lens cap but past the real frame edge) "
								+ "must not render anywhere on canvas");
	}

	// See starAtASixtyDegreeOffsetIsRejectedEvenThoughItsWithinTheLensMaxAngle's own comment for why
	// this reconstructAltAz(...)-based construction is used instead of a naive same-altitude azimuth
	// offset - guarantees theta=60 degrees regardless of the target's own real altitude.
	private Orientation sixtyDegreesAwayFrom(ObjectDirectionAltAz target) {
		Orientation referenceAtTarget = new Orientation(target.getAltitude(), target.getAzimuth(), 0.0);
		ObjectDirectionAltAz sixtyDegreesAway = me.qbert.skywatch.camera.orientation.BoresightAngles
				.reconstructAltAz(referenceAtTarget, Math.toRadians(60.0), 0.0);
		return new Orientation(sixtyDegreesAway.getAltitude(), sixtyDegreesAway.getAzimuth(), 0.0);
	}

	@Test
	void starsRenderAsUnfilledBoundingCirclesWhenACameraImageIsShown() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		StarCoordinate star = new StarCoordinate("Test Star", "TS1", 1.0, 83.822, -5.391, 1, true);
		me.qbert.skywatch.astro.CelestialObject starObject = me.qbert.skywatch.astro.impl.StarObject.create()
				.setStarLocation(address(star)).setObserverLocation(location).setObserverTime(time).build();
		starObject.recompute();
		ObjectDirectionAltAz starAltAz = starObject.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(starAltAz.getAltitude(), starAltAz.getAzimuth(), 0.0);

		RectilinearProjection projection = new RectilinearProjection(50.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		// cameraImageShown=true - the "bound the real star in the photo" case [CLAUDE.md's "I also
		// noticed that the stars are always represented as circles" feedback].
		CelestialObjectsLayer.paintStars(g2d, Collections.singletonList(star), time, location, projection, camera,
				CANVAS_SIZE, CANVAS_SIZE, false, true);
		g2d.dispose();

		int centerX = CANVAS_SIZE / 2;
		int centerY = CANVAS_SIZE / 2;
		assertEquals(rgbNoAlpha(Color.BLACK), rgbNoAlpha(image, centerX, centerY),
				"a bounding circle must not fill its own center");

		boolean foundOutline = false;
		for (int x = centerX; x < CANVAS_SIZE; x++) {
			if (rgbNoAlpha(image, x, centerY) != rgbNoAlpha(Color.BLACK)) {
				foundOutline = true;
				break;
			}
		}
		assertTrue(foundOutline, "expected to find the star's outline near its computed position");
	}

	@Test
	void starsRenderAsFilledDotsWhenNoCameraImageIsShown() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		StarCoordinate star = new StarCoordinate("Test Star", "TS1", 1.0, 83.822, -5.391, 1, true);
		me.qbert.skywatch.astro.CelestialObject starObject = me.qbert.skywatch.astro.impl.StarObject.create()
				.setStarLocation(address(star)).setObserverLocation(location).setObserverTime(time).build();
		starObject.recompute();
		ObjectDirectionAltAz starAltAz = starObject.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(starAltAz.getAltitude(), starAltAz.getAzimuth(), 0.0);

		RectilinearProjection projection = new RectilinearProjection(50.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		// The bare 8-arg overload (no cameraImageShown argument) must default to this same behavior -
		// a plain synthetic star field, no photo underneath to bound.
		CelestialObjectsLayer.paintStars(g2d, Collections.singletonList(star), time, location, projection, camera,
				CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();

		int centerX = CANVAS_SIZE / 2;
		int centerY = CANVAS_SIZE / 2;
		assertNotEquals(rgbNoAlpha(Color.BLACK), rgbNoAlpha(image, centerX, centerY),
				"a filled dot must paint its own center, unlike a bounding circle");
	}

	@Test
	void planetsRenderAsUnfilledBoundingCirclesAtTheirComputedPositions() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		SolarObjects solarObjects = (SolarObjects) SolarObjects.create()
				.setObserverLocation(location).setObserverTime(time).build();
		solarObjects.recompute();
		solarObjects.setObjectIndex(4); // Jupiter
		ObjectDirectionAltAz jupiterAltAz = solarObjects.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(jupiterAltAz.getAltitude(), jupiterAltAz.getAzimuth(), 0.0);

		RectilinearProjection projection = new RectilinearProjection(50.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		CelestialObjectsLayer.paintPlanets(g2d, time, location, projection, camera, ColorPresets.defaultScheme(),
				CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();

		int centerX = CANVAS_SIZE / 2;
		int centerY = CANVAS_SIZE / 2;
		assertEquals(rgbNoAlpha(Color.BLACK), rgbNoAlpha(image, centerX, centerY),
				"a bounding circle must not fill its own center");

		boolean foundOutline = false;
		for (int x = centerX; x < CANVAS_SIZE; x++) {
			if (rgbNoAlpha(image, x, centerY) != rgbNoAlpha(Color.BLACK)) {
				foundOutline = true;
				break;
			}
		}
		assertTrue(foundOutline, "expected to find Jupiter's outline near its computed position");
	}

	// Regression guard for the recompute()-after-build() gotcha (see CelestialObjectsLayer's class
	// comment): a freshly-built-but-never-recomputed object resolves to altitude = 90 - latitude,
	// azimuth = 180 exactly, regardless of actual time - a suspiciously plausible-looking wrong
	// value. If this ever starts failing, some code path stopped calling recompute() after build().
	@Test
	void solarObjectsRecomputeActuallyChangesPositionFromTheDegenerateDefault() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		SolarObjects solarObjects = (SolarObjects) SolarObjects.create()
				.setObserverLocation(location).setObserverTime(time).build();
		solarObjects.setObjectIndex(4); // Jupiter, before recompute()
		ObjectDirectionAltAz beforeRecompute = solarObjects.getCurrentDirectionAsAltitudeAzimuth();

		solarObjects.recompute();
		ObjectDirectionAltAz afterRecompute = solarObjects.getCurrentDirectionAsAltitudeAzimuth();

		assertEquals(45.0, beforeRecompute.getAltitude(), 1e-6,
				"sanity check on the known-degenerate default itself: altitude = 90 - latitude");
		assertEquals(180.0, beforeRecompute.getAzimuth(), 1e-6);
		assertNotEquals(beforeRecompute.getAltitude(), afterRecompute.getAltitude(), 1e-6,
				"recompute() must actually change the reported position away from the degenerate default");
	}

	@Test
	void brighterStarsRenderLighterThanDimmerOnes() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		StarCoordinate brightStar = new StarCoordinate("Bright", "B1", -1.0, 83.822, -5.391, 1, true);
		StarCoordinate dimStar = new StarCoordinate("Dim", "D1", 5.0, 88.793, 7.407, 1, true);
		List<StarCoordinate> stars = Arrays.asList(brightStar, dimStar);

		Color brightColor = StarBrightness.grayscaleFor(brightStar.getApparentMagnitude(), -1.0, 5.0);
		Color dimColor = StarBrightness.grayscaleFor(dimStar.getApparentMagnitude(), -1.0, 5.0);

		assertTrue(brightColor.getRed() > dimColor.getRed(),
				"a brighter (lower magnitude) star must render lighter than a dimmer one from the same set");
	}

	@Test
	void showLabelDrawsTextNearTheSunWithoutChangingItsOwnPosition() throws Exception {
		ObservationTime time = observationTimeAt(TEST_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		BufferedImage withLabel = blankImage();
		Graphics2D g2d = withLabel.createGraphics();
		CelestialObjectsLayer.paintSun(g2d, time, location, projection, camera, ColorPresets.defaultScheme(),
				CANVAS_SIZE, CANVAS_SIZE, 5.0, true);
		g2d.dispose();

		// The sun disc itself is unaffected by the label toggle.
		assertEquals(rgbNoAlpha(ColorPresets.defaultScheme().getSunColor()), rgbNoAlpha(withLabel, CANVAS_SIZE / 2, CANVAS_SIZE / 2));

		// Some pixel near the offset position (up and to the right of center, per Labels) differs
		// from the plain background - the label text itself.
		boolean foundLabelPixel = false;
		for (int y = CANVAS_SIZE / 2 - 30; y < CANVAS_SIZE / 2; y++) {
			for (int x = CANVAS_SIZE / 2; x < CANVAS_SIZE / 2 + 30; x++) {
				if (rgbNoAlpha(withLabel, x, y) != rgbNoAlpha(Color.BLACK)) {
					foundLabelPixel = true;
					break;
				}
			}
		}
		assertTrue(foundLabelPixel, "expected to find the 'Sun' label text near the offset position");
	}

	private me.qbert.skywatch.model.CelestialAddress address(StarCoordinate star) {
		me.qbert.skywatch.model.CelestialAddress address = new me.qbert.skywatch.model.CelestialAddress();
		address.setAddress(star.getRightAscension(), star.getDeclination());
		return address;
	}

	private BufferedImage blankImage() {
		BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();
		return image;
	}

	private int rgbNoAlpha(BufferedImage image, int x, int y) {
		return image.getRGB(x, y) & 0x00FFFFFF;
	}

	private int rgbNoAlpha(Color color) {
		return color.getRGB() & 0x00FFFFFF;
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
