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
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.watch.WatchedObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

class WatchedObjectCrosshairTest {

	private static final int CANVAS_SIZE = 200;
	private static final long EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

	@Test
	void paintsAReticleCenteredOnTheWatchedObjectsResolvedPosition() throws Exception {
		ObservationTime referenceTime = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		// Aim the camera exactly at the sun's own resolved position, so the crosshair must land dead
		// center regardless of the exact ephemeris values - same robust-test approach
		// CelestialObjectsLayerTest uses for the sun's live glyph.
		ObjectDirectionAltAz sunAltAz = WatchedObject.sun().resolveAltAz(referenceTime, location);
		Orientation camera = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();
		WatchedObjectCrosshair.paint(g2d, WatchedObject.sun(), referenceTime, location, projection, camera,
				Color.YELLOW, CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();

		// The exact center pixel sits in the reticle's gap (deliberately not drawn there), so check a
		// point along one of the four arms instead.
		assertEquals(Color.YELLOW.getRGB(), image.getRGB(CANVAS_SIZE / 2 + 6, CANVAS_SIZE / 2),
				"expected the crosshair's right arm to be drawn near the resolved screen position");
	}

	@Test
	void theCenterItselfIsLeftAsAGap() throws Exception {
		ObservationTime referenceTime = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		ObjectDirectionAltAz sunAltAz = WatchedObject.sun().resolveAltAz(referenceTime, location);
		Orientation camera = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();
		WatchedObjectCrosshair.paint(g2d, WatchedObject.sun(), referenceTime, location, projection, camera,
				Color.YELLOW, CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();

		assertEquals(Color.BLACK.getRGB(), image.getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2),
				"the reticle's own center should stay untouched - it's a gap, not a solid marker");
	}

	@Test
	void resolvesAtTheGivenReferenceTimeNotTheLiveObjectPosition() throws Exception {
		// Aim at the sun's position at EPOCH_MILLIS but resolve the crosshair against a time 6 hours
		// later - the sun will have moved, so the crosshair must NOT land at canvas center.
		ObservationTime aimTime = observationTimeAt(EPOCH_MILLIS);
		ObservationTime laterReferenceTime = observationTimeAt(EPOCH_MILLIS + 6L * 60 * 60 * 1000);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		ObjectDirectionAltAz sunAltAzAtAimTime = WatchedObject.sun().resolveAltAz(aimTime, location);
		Orientation camera = new Orientation(sunAltAzAtAimTime.getAltitude(), sunAltAzAtAimTime.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();
		WatchedObjectCrosshair.paint(g2d, WatchedObject.sun(), laterReferenceTime, location, projection, camera,
				Color.YELLOW, CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();

		assertTrue(!hasColor(image, Color.YELLOW, CANVAS_SIZE / 2 - 15, CANVAS_SIZE / 2 - 15, 30),
				"the crosshair should have moved away from canvas center since it targets a later reference time");
	}

	@Test
	void showLabelDrawsTheObjectsDisplayNameNearTheCrosshair() throws Exception {
		ObservationTime referenceTime = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		ObjectDirectionAltAz sunAltAz = WatchedObject.sun().resolveAltAz(referenceTime, location);
		Orientation camera = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		BufferedImage withoutLabel = blankImage();
		Graphics2D g1 = withoutLabel.createGraphics();
		WatchedObjectCrosshair.paint(g1, WatchedObject.sun(), referenceTime, location, projection, camera,
				Color.YELLOW, CANVAS_SIZE, CANVAS_SIZE, false);
		g1.dispose();

		BufferedImage withLabel = blankImage();
		Graphics2D g2 = withLabel.createGraphics();
		WatchedObjectCrosshair.paint(g2, WatchedObject.sun(), referenceTime, location, projection, camera,
				Color.YELLOW, CANVAS_SIZE, CANVAS_SIZE, true);
		g2.dispose();

		assertTrue(countMatchingPixels(withLabel, Color.YELLOW) > countMatchingPixels(withoutLabel, Color.YELLOW),
				"expected showLabel=true to paint additional pixels for the object's name");
	}

	// A real user report: the crosshair rendered (and visibly moved) for a watched star that was
	// actually far outside the frame - projectToPixels(...) only rejects by the lens's max
	// representable angle (a generous, fixed bound), not by whether the resulting point actually
	// falls inside the canvas. Reuses the same BoresightAngles.reconstructAltAz(...)-based
	// construction CelestialObjectsLayerTest's matching off-canvas tests use, for the same reason: a
	// same-altitude azimuth offset gives an unreliable theta (it shrinks as altitude grows, toward 0
	// near the poles), whereas reconstructAltAz(...) guarantees theta=60 degrees regardless of the
	// object's own altitude.
	@Test
	void doesNotRenderWhenTheResolvedPositionIsWithinTheLensCapButOffCanvas() throws Exception {
		ObservationTime referenceTime = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		RectilinearProjection projection = new RectilinearProjection(21.0);

		ObjectDirectionAltAz sunAltAz = WatchedObject.sun().resolveAltAz(referenceTime, location);
		Orientation reference = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);
		ObjectDirectionAltAz sixtyDegreesAway = me.qbert.skywatch.camera.orientation.BoresightAngles
				.reconstructAltAz(reference, Math.toRadians(60.0), 0.0);
		Orientation camera = new Orientation(sixtyDegreesAway.getAltitude(), sixtyDegreesAway.getAzimuth(), 0.0);

		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();
		WatchedObjectCrosshair.paint(g2d, WatchedObject.sun(), referenceTime, location, projection, camera,
				Color.YELLOW, CANVAS_SIZE, CANVAS_SIZE, true);
		g2d.dispose();

		assertTrue(countMatchingPixels(image, Color.YELLOW) == 0,
				"a watched object 60 degrees off boresight (within the 89-degree lens cap, but past this "
						+ "21mm lens's real ~40.6-degree edge) must not render a crosshair or label anywhere on canvas");
	}

	@Test
	void rejectsNullWatchedObject() {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		assertThrows(IllegalArgumentException.class,
				() -> WatchedObjectCrosshair.paint(g2d, null, observationTimeAt(EPOCH_MILLIS),
						observerLocationAt(45.0, -75.0), projection, camera, Color.YELLOW, CANVAS_SIZE, CANVAS_SIZE));
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

	private boolean hasColor(BufferedImage image, Color color, int startX, int startY, int size) {
		for (int y = Math.max(0, startY); y < Math.min(CANVAS_SIZE, startY + size); y++)
			for (int x = Math.max(0, startX); x < Math.min(CANVAS_SIZE, startX + size); x++)
				if (image.getRGB(x, y) == color.getRGB())
					return true;
		return false;
	}

	private int countMatchingPixels(BufferedImage image, Color color) {
		int count = 0;
		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				if (image.getRGB(x, y) == color.getRGB())
					count++;
		return count;
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
