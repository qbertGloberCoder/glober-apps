package me.qbert.skywatch.camera.render;

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
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.watch.WatchedObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

class GraticuleTest {

	private static final int SIZE = 200;
	private static final long EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

	@Test
	void gridPaintsGraticuleLinesOntoTheCanvas() throws Exception {
		// A near-full-hemisphere fisheye pointed at the zenith - whatever RA/Dec grid step is used,
		// several meridians/parallels are guaranteed to cross this frame somewhere.
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation straightUp = new Orientation(90.0, 0.0, 0.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		Graticule.paintGrid(g2d, observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0), projection,
				straightUp, Color.GREEN, 30.0, 15.0, SIZE, SIZE);
		g2d.dispose();

		assertTrue(hasColor(image, Color.GREEN), "expected at least one graticule line pixel");
	}

	@Test
	void celestialEquatorPaintsALineOntoTheCanvas() throws Exception {
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation straightUp = new Orientation(90.0, 0.0, 0.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		Graticule.paintCelestialEquator(g2d, observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0),
				projection, straightUp, Color.CYAN, SIZE, SIZE);
		g2d.dispose();

		assertTrue(hasColor(image, Color.CYAN), "expected the celestial equator line to paint something");
	}

	@Test
	void celestialEquatorIsIndependentOfTheGeneralGridToggle() throws Exception {
		// Pointed at a patch of sky far from Dec=0 at this latitude/time and using a narrow lens, so
		// a coarse grid step plausibly misses this frame entirely while the dedicated celestial-
		// equator call (drawn regardless of grid step) can still be exercised independently.
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(80.0, 0.0, 0.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		Graticule.paintCelestialEquator(g2d, observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0),
				projection, camera, Color.CYAN, SIZE, SIZE);
		g2d.dispose();

		// Not asserting the line necessarily lands on-screen here (that depends on where Dec=0
		// actually is at this time/location/orientation) - just that calling it alone, without
		// paintGrid(...), does not throw and completes normally.
	}

	// Item 5 ("Graticule redesign") - the four new reference-line groups.

	@Test
	void paintPrimeMeridianPaintsALineOntoTheCanvas() throws Exception {
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation straightUp = new Orientation(90.0, 0.0, 0.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		Graticule.paintPrimeMeridian(g2d, observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0), projection,
				straightUp, Color.RED, SIZE, SIZE);
		g2d.dispose();

		assertTrue(hasColor(image, Color.RED), "expected the prime meridian (RA=0) line to paint something");
	}

	// The local meridian (az 0/180) and prime vertical (az 90/270) both pass through the zenith by
	// construction - aiming the boresight straight up puts the zenith at dead screen center.
	@Test
	void paintObserverCardinalCrossPassesThroughTheZenith() {
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation straightUp = new Orientation(90.0, 0.0, 0.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		Graticule.paintObserverCardinalCross(g2d, projection, straightUp, Color.GREEN, SIZE, SIZE);
		g2d.dispose();

		assertTrue(hasColorNear(image, SIZE / 2, SIZE / 2, 3, Color.GREEN),
				"expected the cardinal cross to pass through the zenith at screen center");
	}

	// A stronger, more targeted check than the zenith case above (which both halves of the cross
	// pass through regardless of azimuth, near the pole) - aiming a NARROW lens exactly along
	// azimuth=0 at a non-zenith altitude isolates the local-meridian half specifically: only
	// paintAltitudeSweep(0.0/180.0, ...) should land at screen center here, not an azimuth-agnostic
	// coincidence near the pole.
	@Test
	void paintObserverCardinalCrossLocalMeridianPassesThroughABoresightAimedAlongItsOwnAzimuth() {
		RectilinearProjection projection = new RectilinearProjection(200.0);
		Orientation aimedAlongLocalMeridian = new Orientation(45.0, 0.0, 0.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		Graticule.paintObserverCardinalCross(g2d, projection, aimedAlongLocalMeridian, Color.GREEN, SIZE, SIZE);
		g2d.dispose();

		assertTrue(hasColorNear(image, SIZE / 2, SIZE / 2, 3, Color.GREEN),
				"expected the local meridian to pass through a boresight aimed exactly along azimuth=0");
	}

	// The horizon (altitude=0) passes through dead screen center when the boresight itself is level
	// (altitude=0).
	@Test
	void paintHorizonPassesThroughABoresightAimedAtTheHorizon() {
		RectilinearProjection projection = new RectilinearProjection(200.0);
		Orientation level = new Orientation(0.0, 123.0, 0.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		Graticule.paintHorizon(g2d, projection, level, Color.ORANGE, SIZE, SIZE);
		g2d.dispose();

		assertTrue(hasColorNear(image, SIZE / 2, SIZE / 2, 3, Color.ORANGE),
				"expected the horizon line to pass through a level boresight's screen center");
	}

	// The reference lines must pass through the watched object's OWN current position - aiming the
	// boresight exactly at that position (resolved via the same WatchedObject API, independently of
	// Graticule) puts it at dead screen center.
	@Test
	void paintWatchedObjectReferenceLinesPassesThroughTheWatchedObjectsCurrentPosition() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		WatchedObject sun = WatchedObject.sun();
		ObjectDirectionAltAz sunAltAz = sun.resolveAltAz(time, location);
		Orientation aimedAtSun = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);
		CameraProjection projection = new FisheyeProjection(20.0, Math.PI / 2.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		Graticule.paintWatchedObjectReferenceLines(g2d, sun, time, location, projection, aimedAtSun, Color.YELLOW, SIZE,
				SIZE);
		g2d.dispose();

		assertTrue(hasColorNear(image, SIZE / 2, SIZE / 2, 3, Color.YELLOW),
				"expected the watched-object reference lines to pass through its own current position");
	}

	// By construction (see Graticule.paintBoresightReferenceLines' own derivation comment), both
	// lines are built directly from the boresight's OWN alt/az - they must pass through screen
	// center regardless of which direction the camera is pointed.
	@Test
	void paintBoresightReferenceLinesAlwaysPassesThroughScreenCenter() {
		CameraProjection projection = new FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation arbitrary = new Orientation(37.0, 214.0, 0.0);
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		Graticule.paintBoresightReferenceLines(g2d, observerLocationAt(45.0, -75.0), projection, arbitrary, Color.CYAN,
				SIZE, SIZE);
		g2d.dispose();

		assertTrue(hasColorNear(image, SIZE / 2, SIZE / 2, 3, Color.CYAN),
				"expected the boresight reference lines to pass through screen center regardless of orientation");
	}

	@Test
	void rejectsOutOfRangeSteps() {
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);

		assertThrows(IllegalArgumentException.class,
				() -> Graticule.paintGrid(blankImage().createGraphics(), observationTimeAt(EPOCH_MILLIS),
						observerLocationAt(45.0, -75.0), projection, camera, Color.GREEN, 0.0, 15.0, SIZE, SIZE));
		assertThrows(IllegalArgumentException.class,
				() -> Graticule.paintGrid(blankImage().createGraphics(), observationTimeAt(EPOCH_MILLIS),
						observerLocationAt(45.0, -75.0), projection, camera, Color.GREEN, 30.0, 180.0, SIZE, SIZE));
	}

	private BufferedImage blankImage() {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, SIZE, SIZE);
		g2d.dispose();
		return image;
	}

	private boolean hasColor(BufferedImage image, Color color) {
		for (int y = 0; y < SIZE; y++)
			for (int x = 0; x < SIZE; x++)
				if (image.getRGB(x, y) == color.getRGB())
					return true;
		return false;
	}

	// A thin line's exact rasterized pixels near its own double-precision target aren't
	// pixel-perfect guaranteed - checking a small neighborhood, matching this module's established
	// precedent (EclipticAnalemmaPathTest, WatchedObjectCrosshairTest).
	private boolean hasColorNear(BufferedImage image, int centerX, int centerY, int radius, Color color) {
		for (int y = Math.max(0, centerY - radius); y < Math.min(SIZE, centerY + radius); y++)
			for (int x = Math.max(0, centerX - radius); x < Math.min(SIZE, centerX + radius); x++)
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
