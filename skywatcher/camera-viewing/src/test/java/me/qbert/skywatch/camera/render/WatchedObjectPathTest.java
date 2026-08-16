package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.watch.WatchedObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

class WatchedObjectPathTest {

	private static final int CANVAS_SIZE = 200;
	private static final long EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

	@Test
	void pathPassesThroughTheWatchedObjectsLivePosition() throws Exception {
		// Same robust-test approach as the rest of this module: aim the camera exactly at the
		// watched object's own live ("now") position - the loop's final sample lands exactly there
		// (endMillis is included via the <= bound), so a segment is guaranteed near canvas center
		// regardless of the object's actual position at this test's fixed time/location.
		ObservationTime currentTime = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		WatchedObject sun = WatchedObject.sun();
		ObjectDirectionAltAz liveAltAz = sun.resolveAltAz(currentTime, location);
		Orientation camera = new Orientation(liveAltAz.getAltitude(), liveAltAz.getAzimuth(), 0.0);
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		WatchedObjectPath.paint(g2d, sun, currentTime, location, projection, camera, Color.ORANGE, CANVAS_SIZE,
				CANVAS_SIZE);
		g2d.dispose();

		assertTrue(hasColorNear(image, CANVAS_SIZE / 2, CANVAS_SIZE / 2, 3, Color.ORANGE),
				"expected the path to pass near the watched object's own live screen position");
	}

	@Test
	void doesNotMutateTheCallersCurrentTime() throws Exception {
		// The sampling loop walks backward across the trailing window using its OWN private
		// ObservationTime, deliberately never touching the caller's - this pins that down directly,
		// since a shared mutable ObservationTime accidentally being repointed mid-loop would corrupt
		// whatever else in the same frame still needs "now" (e.g. the live sun/moon/planet/star
		// glyphs painted right after this in FrameCompositor's OBJECTS case).
		ObservationTime currentTime = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		long originalMillis = currentTime.getTime().getTimeInMillis();
		double originalJulianDate = currentTime.getJulianDate();

		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();
		WatchedObjectPath.paint(g2d, WatchedObject.sun(), currentTime, location, new FisheyeProjection(20.0, Math.PI),
				new Orientation(0.0, 0.0, 0.0), Color.ORANGE, CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();

		assertEquals(originalMillis, currentTime.getTime().getTimeInMillis());
		assertEquals(originalJulianDate, currentTime.getJulianDate());
	}

	@Test
	void customWindowAndIntervalAreRespected() throws Exception {
		// A short window with a coarse interval - just confirms the overload wires through and
		// completes without error across a differently-shaped sampling range than the defaults.
		ObservationTime currentTime = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		WatchedObject moon = WatchedObject.moon();
		ObjectDirectionAltAz liveAltAz = moon.resolveAltAz(currentTime, location);
		Orientation camera = new Orientation(liveAltAz.getAltitude(), liveAltAz.getAzimuth(), 0.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		WatchedObjectPath.paint(g2d, moon, currentTime, location, new FisheyeProjection(20.0, Math.PI), camera,
				Color.CYAN, CANVAS_SIZE, CANVAS_SIZE, 2L * 60 * 60 * 1000, 15L * 60 * 1000);
		g2d.dispose();

		assertTrue(hasColorNear(image, CANVAS_SIZE / 2, CANVAS_SIZE / 2, 3, Color.CYAN),
				"expected the custom-window path to still pass near the watched object's own live position");
	}

	@Test
	void rejectsInvalidArguments() throws Exception {
		ObservationTime currentTime = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		assertThrows(IllegalArgumentException.class, () -> WatchedObjectPath.paint(g2d, null, currentTime, location,
				projection, camera, Color.ORANGE, CANVAS_SIZE, CANVAS_SIZE));
		assertThrows(IllegalArgumentException.class, () -> WatchedObjectPath.paint(g2d, WatchedObject.sun(), null,
				location, projection, camera, Color.ORANGE, CANVAS_SIZE, CANVAS_SIZE));
		assertThrows(IllegalArgumentException.class,
				() -> WatchedObjectPath.paint(g2d, WatchedObject.sun(), currentTime, location, projection, camera,
						Color.ORANGE, CANVAS_SIZE, CANVAS_SIZE, 0, WatchedObjectPath.DEFAULT_SAMPLE_INTERVAL_MILLIS));
		assertThrows(IllegalArgumentException.class,
				() -> WatchedObjectPath.paint(g2d, WatchedObject.sun(), currentTime, location, projection, camera,
						Color.ORANGE, CANVAS_SIZE, CANVAS_SIZE, WatchedObjectPath.DEFAULT_TRAILING_WINDOW_MILLIS, 0));
		g2d.dispose();
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
