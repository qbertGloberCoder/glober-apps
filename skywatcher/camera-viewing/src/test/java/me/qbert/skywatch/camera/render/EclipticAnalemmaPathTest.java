package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.astro.CameraAstronomy;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.model.ObjectDirectionRaDec;

class EclipticAnalemmaPathTest {

	private ObservationTime endTime() throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(1_723_161_600_000L);
		return time;
	}

	private ObserverLocation observer() {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(45.0, -75.0);
		return location;
	}

	@Test
	void sunEclipticPathSamplesRoughlyAYear() throws Exception {
		List<ObjectDirectionRaDec> path = EclipticAnalemmaPath.sampleSun(observer(), endTime(), false);

		assertTrue(path.size() > 300 && path.size() < 370);
		for (ObjectDirectionRaDec point : path)
			assertFalse(Double.isNaN(point.getRightAscension()) || Double.isNaN(point.getDeclination()));
	}

	@Test
	void moonEclipticPathSamplesRoughlyASynodicMonth() throws Exception {
		List<ObjectDirectionRaDec> path = EclipticAnalemmaPath.sampleMoon(observer(), endTime(), false);

		assertTrue(path.size() > 25 && path.size() < 32, "expected ~29.5 daily samples, got " + path.size());
	}

	@Test
	void analemmaModeCollapsesToTheSameSampleCountAsEcliptic() throws Exception {
		List<ObjectDirectionRaDec> ecliptic = EclipticAnalemmaPath.sampleSun(observer(), endTime(), false);
		List<ObjectDirectionRaDec> analemma = EclipticAnalemmaPath.sampleSun(observer(), endTime(), true);

		assertEquals(ecliptic.size(), analemma.size());
	}

	// The actual regression this fix addresses - a real user report, verified numerically by the
	// user's own standalone workaround before this fix was written into production code (see
	// EclipticAnalemmaPath's own class comment): analemma mode must trace a COMPACT figure-8-shaped
	// region of sky (both altitude and azimuth staying within a narrow band across the whole year),
	// genuinely different in character from ecliptic mode, which is supposed to sweep the sun's full
	// annual path - a wide altitude range and azimuth spanning most/all of the compass. Neither
	// exact-degree-range assertion is hardcoded (location/date-dependent) - this asserts the
	// qualitative SHAPE difference the bug report was actually about, robust to exactly which epoch
	// this test runs against.
	@Test
	void analemmaModeStaysCompactWhileEclipticModeSweepsTheFullAnnualRange() throws Exception {
		List<ObjectDirectionAltAz> ecliptic = EclipticAnalemmaPath.sampleSunAltAz(observer(), endTime(), false);
		List<ObjectDirectionAltAz> analemma = EclipticAnalemmaPath.sampleSunAltAz(observer(), endTime(), true);

		double eclipticAzimuthRange = range(ecliptic, ObjectDirectionAltAz::getAzimuth);
		double analemmaAzimuthRange = range(analemma, ObjectDirectionAltAz::getAzimuth);
		double eclipticAltitudeRange = range(ecliptic, ObjectDirectionAltAz::getAltitude);
		double analemmaAltitudeRange = range(analemma, ObjectDirectionAltAz::getAltitude);

		// Relative comparisons rather than hardcoded absolute degree thresholds - the exact absolute
		// range depends on observer location/date (verified directly: this test's own fixture
		// produces a genuinely wider analemma azimuth band, ~34 degrees, than the user's own
		// real-world example at a different location/date, ~8 degrees) - what must hold universally
		// is that analemma stays DRAMATICALLY more compact than ecliptic's full annual sweep, in both
		// axes.
		assertTrue(eclipticAzimuthRange > 100.0,
				"ecliptic mode should sweep broadly through the compass over a year - got " + eclipticAzimuthRange);
		assertTrue(analemmaAzimuthRange < eclipticAzimuthRange / 3.0,
				"analemma's azimuth range must be dramatically smaller than ecliptic mode's own sweep - "
						+ "analemma=" + analemmaAzimuthRange + ", ecliptic=" + eclipticAzimuthRange);
		assertTrue(analemmaAltitudeRange < eclipticAltitudeRange,
				"analemma's altitude range must be meaningfully smaller than ecliptic mode's own sweep - "
						+ "analemma=" + analemmaAltitudeRange + ", ecliptic=" + eclipticAltitudeRange);
	}

	private double range(List<ObjectDirectionAltAz> points, java.util.function.ToDoubleFunction<ObjectDirectionAltAz> field) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (ObjectDirectionAltAz point : points) {
			double value = field.applyAsDouble(point);
			min = Math.min(min, value);
			max = Math.max(max, value);
		}
		return max - min;
	}

	// Item 7b - proves the CameraAstronomy-backed overloads (reusing astronomy's own long-lived
	// SunPrecession/MoonPrecession instance) render PIXEL-IDENTICAL output to the original
	// ObserverLocation/ObservationTime-based overloads (which construct a fresh SunPrecession/
	// MoonPrecession per call) - the point of threading astronomy through is to eliminate per-paint
	// object construction, not to change what gets drawn.
	@Test
	void sampleSunAltAzViaAstronomyMatchesTheOriginalOverloadExactly() throws Exception {
		CameraAstronomy astronomy = new CameraAstronomy(TimeZone.getTimeZone("UTC"), Collections.emptyList());
		astronomy.applyTimeAndLocation(1_723_161_600_000L, 45.0, -75.0);

		List<ObjectDirectionAltAz> original = EclipticAnalemmaPath.sampleSunAltAz(observer(), endTime(), false);
		List<ObjectDirectionAltAz> viaAstronomy = EclipticAnalemmaPath.sampleSunAltAz(astronomy, false);

		assertEquals(original.size(), viaAstronomy.size());
		for (int i = 0; i < original.size(); i++) {
			assertEquals(original.get(i).getAltitude(), viaAstronomy.get(i).getAltitude(), 0.0001);
			assertEquals(original.get(i).getAzimuth(), viaAstronomy.get(i).getAzimuth(), 0.0001);
		}
	}

	@Test
	void sampleMoonAltAzViaAstronomyMatchesTheOriginalOverloadExactlyAnalemmaMode() throws Exception {
		CameraAstronomy astronomy = new CameraAstronomy(TimeZone.getTimeZone("UTC"), Collections.emptyList());
		astronomy.applyTimeAndLocation(1_723_161_600_000L, 45.0, -75.0);

		List<ObjectDirectionAltAz> original = EclipticAnalemmaPath.sampleMoonAltAz(observer(), endTime(), true);
		List<ObjectDirectionAltAz> viaAstronomy = EclipticAnalemmaPath.sampleMoonAltAz(astronomy, true);

		assertEquals(original.size(), viaAstronomy.size());
		for (int i = 0; i < original.size(); i++) {
			assertEquals(original.get(i).getAltitude(), viaAstronomy.get(i).getAltitude(), 0.0001);
			assertEquals(original.get(i).getAzimuth(), viaAstronomy.get(i).getAzimuth(), 0.0001);
		}
	}

	@Test
	void paintSunViaAstronomyMatchesTheOriginalOverloadExactly() throws Exception {
		CameraAstronomy astronomy = new CameraAstronomy(TimeZone.getTimeZone("UTC"), Collections.emptyList());
		astronomy.applyTimeAndLocation(1_723_161_600_000L, 45.0, -75.0);
		List<ObjectDirectionAltAz> points = EclipticAnalemmaPath.sampleSunAltAz(astronomy, false);
		Orientation camera = new Orientation(points.get(0).getAltitude(), points.get(0).getAzimuth(), 0.0);
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI);

		BufferedImage original = blankImage();
		Graphics2D g1 = original.createGraphics();
		EclipticAnalemmaPath.paintSun(g1, astronomy.getObserverLocation(), astronomy.getObservationTime(), false,
				projection, camera, Color.GREEN, CANVAS_SIZE, CANVAS_SIZE);
		g1.dispose();

		BufferedImage viaAstronomy = blankImage();
		Graphics2D g2 = viaAstronomy.createGraphics();
		EclipticAnalemmaPath.paintSun(g2, astronomy, false, projection, camera, Color.GREEN, CANVAS_SIZE, CANVAS_SIZE);
		g2.dispose();

		assertArrayEquals(pixels(original), pixels(viaAstronomy));
		assertTrue(hasColorNear(viaAstronomy, CANVAS_SIZE / 2, CANVAS_SIZE / 2, 3, Color.GREEN));
	}

	private static final int CANVAS_SIZE = 200;

	private static int[] pixels(BufferedImage image) {
		int[] pixels = new int[CANVAS_SIZE * CANVAS_SIZE];
		image.getRGB(0, 0, CANVAS_SIZE, CANVAS_SIZE, pixels, 0, CANVAS_SIZE);
		return pixels;
	}

	@Test
	void paintSunEclipticPaintsSomethingOntoTheCanvas() throws Exception {
		// Same robust-test approach the rest of this module uses (CelestialObjectsLayerTest,
		// WatchedObjectCrosshairTest, ...): aim the camera exactly at the FIRST sample's own resolved
		// position (index 0 = the sample at endTime itself, since the loop walks backward from
		// there), so a segment is guaranteed to land dead center regardless of the sun's actual
		// altitude at this test's fixed time/location - no need to reason about lens geometry or
		// horizon visibility to make this deterministic.
		List<ObjectDirectionAltAz> points = EclipticAnalemmaPath.sampleSunAltAz(observer(), endTime(), false);
		Orientation camera = new Orientation(points.get(0).getAltitude(), points.get(0).getAzimuth(), 0.0);
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		EclipticAnalemmaPath.paintSun(g2d, observer(), endTime(), false, projection, camera, Color.GREEN, CANVAS_SIZE,
				CANVAS_SIZE);
		g2d.dispose();

		// A thin line's exact rasterized pixels near its own double-precision endpoint aren't
		// pixel-perfect guaranteed (unlike a filled disc with real area) - checking a small
		// neighborhood, not the single exact center pixel, matching LabelsTest's own precedent.
		assertTrue(hasColorNear(image, CANVAS_SIZE / 2, CANVAS_SIZE / 2, 3, Color.GREEN),
				"expected the sun-ecliptic path to pass near its own first sample's screen position");
	}

	@Test
	void paintMoonAnalemmaPaintsSomethingOntoTheCanvas() throws Exception {
		List<ObjectDirectionAltAz> points = EclipticAnalemmaPath.sampleMoonAltAz(observer(), endTime(), true);
		Orientation camera = new Orientation(points.get(0).getAltitude(), points.get(0).getAzimuth(), 0.0);
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		EclipticAnalemmaPath.paintMoon(g2d, observer(), endTime(), true, projection, camera, Color.MAGENTA, CANVAS_SIZE,
				CANVAS_SIZE);
		g2d.dispose();

		assertTrue(hasColorNear(image, CANVAS_SIZE / 2, CANVAS_SIZE / 2, 3, Color.MAGENTA),
				"expected the moon-analemma path to pass near its own first sample's screen position");
	}

	private BufferedImage blankImage() {
		BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();
		return image;
	}

	private boolean hasColorNear(BufferedImage image, int centerX, int centerY, int radius, Color color) {
		for (int y = Math.max(0, centerY - radius); y < Math.min(CANVAS_SIZE, centerY + radius); y++)
			for (int x = Math.max(0, centerX - radius); x < Math.min(CANVAS_SIZE, centerX + radius); x++)
				if (image.getRGB(x, y) == color.getRGB())
					return true;
		return false;
	}
}
