package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.astro.CameraAstronomy;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.FisheyeProjection;

// Proves the new CameraAstronomy-backed overloads render PIXEL-IDENTICAL output to the original
// ObservationTime/ObserverLocation-based overloads - these eliminate the per-grid-sample-point
// StarObject construction (confirmed the single dominant, previously unflagged object-churn cost
// in this module - up to ~32,400 constructions per full-density graticule paint), they don't
// change what gets drawn.
class GraticuleCameraAstronomyTest {

	private static final int SIZE = 200;
	private static final long EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

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

	private static CameraAstronomy astronomyAt(long epochMillis, double latitude, double longitude) throws Exception {
		CameraAstronomy astronomy = new CameraAstronomy(TimeZone.getTimeZone("UTC"), Collections.emptyList());
		astronomy.applyTimeAndLocation(epochMillis, latitude, longitude);
		return astronomy;
	}

	private static BufferedImage blankImage() {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, SIZE, SIZE);
		g2d.dispose();
		return image;
	}

	private static int[] pixels(BufferedImage image) {
		int[] pixels = new int[SIZE * SIZE];
		image.getRGB(0, 0, SIZE, SIZE, pixels, 0, SIZE);
		return pixels;
	}

	@Test
	void paintGridMatchesTheOriginalOverloadExactly() throws Exception {
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation straightUp = new Orientation(90.0, 0.0, 0.0);

		BufferedImage original = blankImage();
		Graphics2D g1 = original.createGraphics();
		Graticule.paintGrid(g1, observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0), projection,
				straightUp, Color.GREEN, 30.0, 15.0, SIZE, SIZE);
		g1.dispose();

		BufferedImage viaAstronomy = blankImage();
		Graphics2D g2 = viaAstronomy.createGraphics();
		Graticule.paintGrid(g2, astronomyAt(EPOCH_MILLIS, 45.0, -75.0), projection, straightUp, Color.GREEN, 30.0, 15.0,
				SIZE, SIZE);
		g2.dispose();

		assertArrayEquals(pixels(original), pixels(viaAstronomy));
		assertTrue(hasColor(viaAstronomy, Color.GREEN), "expected at least one graticule line pixel");
	}

	@Test
	void paintCelestialEquatorMatchesTheOriginalOverloadExactly() throws Exception {
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation straightUp = new Orientation(90.0, 0.0, 0.0);

		BufferedImage original = blankImage();
		Graphics2D g1 = original.createGraphics();
		Graticule.paintCelestialEquator(g1, observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0),
				projection, straightUp, Color.CYAN, SIZE, SIZE);
		g1.dispose();

		BufferedImage viaAstronomy = blankImage();
		Graphics2D g2 = viaAstronomy.createGraphics();
		Graticule.paintCelestialEquator(g2, astronomyAt(EPOCH_MILLIS, 45.0, -75.0), projection, straightUp, Color.CYAN,
				SIZE, SIZE);
		g2.dispose();

		assertArrayEquals(pixels(original), pixels(viaAstronomy));
		assertTrue(hasColor(viaAstronomy, Color.CYAN), "expected the celestial equator line to paint something");
	}

	private static boolean hasColor(BufferedImage image, Color color) {
		int target = color.getRGB() & 0x00FFFFFF;
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				if ((image.getRGB(x, y) & 0x00FFFFFF) == target)
					return true;
		return false;
	}
}
