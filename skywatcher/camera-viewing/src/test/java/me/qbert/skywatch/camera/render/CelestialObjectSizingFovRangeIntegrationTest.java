package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Task 9.3: "Confirm 4.2's fix behaves correctly across the full described FOV range (120-degree
// wide-field minimum-size floor through real/simulated telephoto zoom) - the concrete bug this
// rewrite was asked to fix, not just a general test pass." CelestialObjectSizingTest already covers
// the pure radiusPixels(...) formula in isolation, and CelestialObjectSizingRenderingIntegrationTest
// already covers that a computed radius renders correctly through ga-base's ArcRenderer - but
// neither exercises a REAL CameraProjection (the thing an actual camera config declares) at either
// FOV extreme through the real CelestialObjectsLayer.paintSun(...) call site that derives fovDegrees
// from the projection itself (see that class's own comment on the derivation). This is the one
// remaining link in the chain "the math is right" -> "a real wide fisheye or a real telephoto lens
// actually renders the sun at the right size."
class CelestialObjectSizingFovRangeIntegrationTest {

	private static final int CANVAS_SIZE = 400;
	private static final double MIN_RADIUS_PIXELS = 5.0;
	private static final long EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z
	private static final double LATITUDE = 45.0;
	private static final double LONGITUDE = -75.0;

	@Test
	void aRealWideFisheyeClampsTheSunToTheMinimumRadius() throws Exception {
		// A short-focal-length fisheye with a generous max angle - a genuine ~170-degree wide-field
		// lens, not a synthetic FOV number.
		CameraProjection projection = new FisheyeProjection(6.0, Math.toRadians(170.0));

		int measuredRadius = renderSunAndMeasureRadius(projection);

		double actualFovDegrees = 2.0
				* Math.toDegrees(projection.angleForSensorRadiusMillimeters(CameraProjection.SENSOR_WIDTH_MILLIMETERS / 2.0));
		assertTrue(actualFovDegrees > 100.0, "sanity check: this lens config should be a genuinely wide FOV, was " + actualFovDegrees);

		assertEquals((int) Math.round(MIN_RADIUS_PIXELS), measuredRadius, 3,
				"a real wide fisheye's true angular radius is sub-pixel, so the sun must render at the minimum floor, not invisible");
	}

	@Test
	void aRealLongTelephotoRendersTheSunAtItsTrueProportionalSize() throws Exception {
		// A 500mm lens on the 36mm-equivalent reference sensor - a genuine long telephoto, not a
		// synthetic FOV number.
		CameraProjection projection = new RectilinearProjection(500.0);

		int measuredRadius = renderSunAndMeasureRadius(projection);

		double actualFovDegrees = 2.0
				* Math.toDegrees(projection.angleForSensorRadiusMillimeters(CameraProjection.SENSOR_WIDTH_MILLIMETERS / 2.0));
		double expectedRadius = CelestialObjectSizing.radiusPixels(CelestialObjectSizing.SUN_MOON_ANGULAR_DIAMETER_DEGREES,
				actualFovDegrees, CANVAS_SIZE, MIN_RADIUS_PIXELS);

		assertTrue(expectedRadius > MIN_RADIUS_PIXELS * 2,
				"sanity check: a 500mm lens should put the true radius well above the wide-field floor");
		assertEquals((int) Math.round(expectedRadius), measuredRadius, 3,
				"a real long-telephoto lens should render the sun at its true proportional size, not the floor");
	}

	@Test
	void anEvenLongerTelephotoRendersLargerStill() throws Exception {
		// No upper clamp anywhere in this pipeline - a longer lens must keep growing the rendered
		// disc, confirmed through the real projection/rendering path, not just the pure formula
		// (already covered by CelestialObjectSizingTest.noUpperClampAtEvenNarrowerFieldsOfView).
		int radiusAt500mm = renderSunAndMeasureRadius(new RectilinearProjection(500.0));
		int radiusAt2000mm = renderSunAndMeasureRadius(new RectilinearProjection(2000.0));

		assertTrue(radiusAt2000mm > radiusAt500mm * 3,
				"a 2000mm lens should render a substantially larger disc than a 500mm one, with no ceiling");
	}

	// Aims the camera exactly at the sun's own real computed position (so it always lands dead
	// center, at theta=0, regardless of the lens under test), paints it via the actual
	// CelestialObjectsLayer.paintSun(...) call site, then measures the rendered disc's radius by
	// scanning outward from the canvas center - the same technique
	// CelestialObjectSizingRenderingIntegrationTest already established.
	private int renderSunAndMeasureRadius(CameraProjection projection) throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(LATITUDE, LONGITUDE);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		Orientation cameraOrientation = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);

		BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

		CelestialObjectsLayer.paintSun(g2d, time, location, projection, cameraOrientation, ColorPresets.defaultScheme(),
				CANVAS_SIZE, CANVAS_SIZE, MIN_RADIUS_PIXELS);
		g2d.dispose();

		int sunRgb = ColorPresets.defaultScheme().getSunColor().getRGB() & 0x00FFFFFF;
		int centerX = CANVAS_SIZE / 2;
		int centerY = CANVAS_SIZE / 2;
		int measuredRadius = 0;
		for (int x = centerX; x < CANVAS_SIZE; x++) {
			int rgb = image.getRGB(x, centerY) & 0x00FFFFFF;
			if (rgb == sunRgb)
				measuredRadius = x - centerX;
			else if (measuredRadius > 0)
				break;
		}
		return measuredRadius;
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
