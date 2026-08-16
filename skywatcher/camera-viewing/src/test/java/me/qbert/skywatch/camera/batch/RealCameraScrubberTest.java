package me.qbert.skywatch.camera.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;
import me.qbert.skywatch.camera.config.VirtualImageSource;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.plate.PlateSolveSession;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.source.ArchiveFrameCache;
import me.qbert.skywatch.camera.source.DirectoryCache;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Task 0.6/4.7's interactive scrubbing case. Two flavors of test: synthetic multi-frame archives
// (@TempDir, deterministic timestamps, exercising the snap-to-closest-older-frame algorithm's
// edges precisely) and a real-data test against camera-viewing/cameras/1 - actual JPEG captures
// the user copied in from a real camera, one per minute, 2560x1440 - confirming the whole pipeline
// (PathTemplate parsing, DirectoryCache, ArchiveFrameScanner, FrameCompositor) works against real
// files, not just synthetic blanks, matching this module's "verify for real" convention.
class RealCameraScrubberTest {

	private static final int IMAGE_SIZE = 200;

	@Test
	void snapsToTheClosestOlderArchivedFrame(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));
		writeBlankImage(new File(cameraRoot, "20260809_120100_1.jpg"));
		writeBlankImage(new File(cameraRoot, "20260809_120200_1.jpg"));

		CameraConfig camera = realCamera(cameraRoot, tempDir);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		// 90 seconds after t0 - between the 120100 and 120200 frames - must snap to 120100, not
		// 120200 and not interpolate.
		RealCameraScrubber.Result result = RealCameraScrubber.composite(camera, cache, t0 + 90_000L, true,
				IMAGE_SIZE, IMAGE_SIZE, defaultOptions());

		assertEquals(t0 + 60_000L, result.getRenderedEpochMillis(), "should snap to the 120100 frame, not 120200");
		assertTrue(result.isRealImageShown());
		assertEquals(IMAGE_SIZE, result.getImage().getWidth());
	}

	@Test
	void exactMatchReturnsThatSameFrame(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));
		writeBlankImage(new File(cameraRoot, "20260809_120100_1.jpg"));

		CameraConfig camera = realCamera(cameraRoot, tempDir);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		RealCameraScrubber.Result result = RealCameraScrubber.composite(camera, cache, t0 + 60_000L, true,
				IMAGE_SIZE, IMAGE_SIZE, defaultOptions());

		assertEquals(t0 + 60_000L, result.getRenderedEpochMillis(), "at-or-before is inclusive of an exact match");
		assertTrue(result.isRealImageShown());
	}

	@Test
	void scrubTargetBeforeArchiveFallsBackToNoImage(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));

		CameraConfig camera = realCamera(cameraRoot, tempDir);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		long beforeArchive = t0 - 60_000L;
		int fallbackSize = IMAGE_SIZE + 40;
		RealCameraScrubber.Result result = RealCameraScrubber.composite(camera, cache, beforeArchive, true,
				fallbackSize, fallbackSize, defaultOptions());

		assertFalse(result.isRealImageShown(), "nothing archived yet at a scrub position before the first frame");
		assertEquals(fallbackSize, result.getImage().getWidth(), "should fall back to the caller's canvas size");
		assertEquals(beforeArchive, result.getRenderedEpochMillis(), "falls back to the raw scrub target");
	}

	@Test
	void aFrameThatFailsToReadDegradesGracefullyInsteadOfCrashingTheWholeRender(@TempDir File tempDir) throws Exception {
		// Real user report: a stack trace from ImageIO.read(...) throwing IIOException("Can't read
		// input file!") propagated uncaught out of PreviewWindow.refresh(), crashing the entire
		// render Timer loop on the EDT. Reproduced here via ArchiveFrameCache's own memoization (see
		// its class comment) - the scanned frame list can go briefly stale relative to the real
		// filesystem, so a frame that was found a moment ago can have since been moved/deleted by the
		// time it's actually read (very plausible against a large, actively-managed archive).
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		File frameFile = new File(cameraRoot, "20260809_120000_1.jpg");
		writeBlankImage(frameFile);

		CameraConfig camera = realCamera(cameraRoot, tempDir);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		WallClock fakeWallClock = () -> 0L;
		ArchiveFrameCache archiveFrameCache = new ArchiveFrameCache(camera.getRealImageSource(), cache, fakeWallClock, 60_000L);

		// Populate the memoized scan (finds the real file, still present at this point).
		RealCameraScrubber.composite(camera, cache, t0 + 5_000L, true, IMAGE_SIZE, IMAGE_SIZE, defaultOptions(), null,
				archiveFrameCache);

		// The file disappears - the memoized frame list (not due for a refresh yet) still points at it.
		assertTrue(frameFile.delete());

		RealCameraScrubber.Result result = RealCameraScrubber.composite(camera, cache, t0 + 5_000L, true, IMAGE_SIZE,
				IMAGE_SIZE, defaultOptions(), null, archiveFrameCache);

		assertFalse(result.isRealImageShown(), "an unreadable frame must degrade to the no-image case, not crash");
		assertEquals(t0 + 5_000L, result.getRenderedEpochMillis(), "falls back to the raw scrub target");
		assertEquals(IMAGE_SIZE, result.getImage().getWidth(), "should still produce a real, valid composited render");
	}

	@Test
	void hiddenModeUsesTheRawScrubTargetAndNeverTouchesTheArchive(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));

		CameraConfig camera = realCamera(cameraRoot, tempDir);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		RealCameraScrubber.Result result = RealCameraScrubber.composite(camera, cache, t0 + 90_000L, false,
				IMAGE_SIZE, IMAGE_SIZE, defaultOptions());

		assertFalse(result.isRealImageShown(), "hidden mode never shows an image, even though a frame exists at/before this time");
		assertEquals(t0 + 90_000L, result.getRenderedEpochMillis(), "hidden mode renders at the exact continuous scrub time");
	}

	@Test
	void archiveScanTruncatedIsFalseWithAnUnlimitedCache(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));

		CameraConfig camera = realCamera(cameraRoot, tempDir);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		RealCameraScrubber.Result result = RealCameraScrubber.composite(camera, cache, t0, true, IMAGE_SIZE, IMAGE_SIZE,
				defaultOptions());

		assertFalse(result.isArchiveScanTruncated());
	}

	@Test
	void archiveScanTruncatedIsTrueAndRenderStillSucceedsWhenTheCacheLimitTrips(@TempDir File tempDir) throws Exception {
		// A camera whose archive has many day-subdirectories (needs "**" so DirectoryCache actually
		// has directories to discover, unlike realCamera(...)'s deliberately flat helper layout).
		File cameraRoot = new File(tempDir, "cameras/1");
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		for (int i = 0; i < 20; i++) {
			File day = new File(cameraRoot, "day" + i);
			day.mkdirs();
			writeBlankImage(new File(day, "2026080" + (i % 9 + 1) + "_120000_1.jpg"));
		}

		RealImageSource source = RealImageSource.liveAndRecorded(new File(cameraRoot, "latest.jpg").getPath(),
				cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg", TimezoneSetting.explicit(ZoneOffset.UTC),
				DstAmbiguousPolicy.ASSUME_STANDARD);
		CameraConfig camera = new CameraConfig("cam1", CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setRealImageSource(source);
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));

		DirectoryCache limitedCache = new DirectoryCache(new File(tempDir, "cache"), 5);

		RealCameraScrubber.Result result = RealCameraScrubber.composite(camera, limitedCache, t0, true, IMAGE_SIZE,
				IMAGE_SIZE, defaultOptions());

		assertTrue(result.isArchiveScanTruncated(), "the circuit breaker should have tripped");
		// The render must still complete normally (some frames may have been found, or none yet) -
		// a truncated scan is not a failure.
		assertEquals(IMAGE_SIZE, result.getImage().getWidth());
	}

	@Test
	void rejectsAVirtualCameraWhenImageIsShown(@TempDir File tempDir) throws Exception {
		CameraConfig virtualCamera = new CameraConfig("virtual-pano",
				CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360), ObserverLocationSetting.useSystemLocale());
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		assertThrows(IllegalStateException.class, () -> RealCameraScrubber.composite(virtualCamera, cache,
				0L, true, IMAGE_SIZE, IMAGE_SIZE, defaultOptions()));
	}

	@Test
	void activeEditSessionOverridesThePersistedCalibration(@TempDir File tempDir) throws Exception {
		// Task "app mode"'s live-editing bridge: a PlateSolveSession's PENDING orientation/location
		// must win over whatever's actually persisted in CalibrationHistory.
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));

		ObserverLocation location = observerLocationAt(45.0, -75.0);
		ObjectDirectionAltAz sunAltAz = sunAltAzAt(t0, location);

		CameraConfig camera = realCamera(cameraRoot, tempDir); // saved calibration points nowhere near the sun
		PlateSolveSession session = new PlateSolveSession(camera, new File(tempDir, "camera.properties"));
		session.adjustOrientation(new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0));

		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		RealCameraScrubber.Result result = RealCameraScrubber.composite(camera, cache, t0 + 5_000L, true, IMAGE_SIZE,
				IMAGE_SIZE, defaultOptions(), session);

		assertEquals(rgbNoAlpha(ColorPresets.defaultScheme().getSunColor()), rgbNoAlpha(result.getImage(), IMAGE_SIZE / 2, IMAGE_SIZE / 2),
				"the sun should render centered now that the PENDING (not saved) orientation points at it");
	}

	@Test
	void nullActiveEditSessionPreservesTheOriginalBehavior(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));
		CameraConfig camera = realCamera(cameraRoot, tempDir);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		RealCameraScrubber.Result withOverload = RealCameraScrubber.composite(camera, cache, t0 + 5_000L, true,
				IMAGE_SIZE, IMAGE_SIZE, defaultOptions());
		RealCameraScrubber.Result withExplicitNull = RealCameraScrubber.composite(camera, cache, t0 + 5_000L, true,
				IMAGE_SIZE, IMAGE_SIZE, defaultOptions(), null);

		assertEquals(withOverload.getRenderedEpochMillis(), withExplicitNull.getRenderedEpochMillis());
		assertEquals(withOverload.isRealImageShown(), withExplicitNull.isRealImageShown());
	}

	// A direct user report: time-scrubbing needlessly re-decodes the same archived photo from disk on
	// every render tick, even when the scrub resolves to the identical frame as last time - see
	// batch.LastImageCache's own class comment. Proven by overwriting the SAME file path with
	// different content between two calls sharing one CameraImageCaches - a cache hit must keep
	// showing the FIRST call's content, not pick up the filesystem change.
	@Test
	void reusesTheDecodedImageWhenTheResolvedFrameIsUnchanged(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		File frameFile = new File(cameraRoot, "20260809_120000_1.jpg");
		writeSolidImage(frameFile, Color.BLACK);

		CameraConfig camera = realCamera(cameraRoot, tempDir);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		CameraImageCaches imageCaches = new CameraImageCaches();

		RealCameraScrubber.Result first = RealCameraScrubber.composite(camera, cache, t0, true, IMAGE_SIZE, IMAGE_SIZE,
				defaultOptions(), null, null, imageCaches);
		int firstCorner = rgbNoAlpha(first.getImage(), 2, 2);

		writeSolidImage(frameFile, Color.WHITE); // same path, different content

		RealCameraScrubber.Result second = RealCameraScrubber.composite(camera, cache, t0, true, IMAGE_SIZE, IMAGE_SIZE,
				defaultOptions(), null, null, imageCaches);
		int secondCorner = rgbNoAlpha(second.getImage(), 2, 2);

		assertEquals(firstCorner, secondCorner,
				"a cache hit must reuse the previously-decoded image, not re-read the changed file");
	}

	// The correctness counterpart to the test above: scrubbing to a DIFFERENT archived frame with the
	// same CameraImageCaches must still pick up that frame's own real content, not keep serving
	// whatever was cached for the first frame.
	@Test
	void aDifferentResolvedFrameIsReReadNotStaleFromTheCache(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeSolidImage(new File(cameraRoot, "20260809_120000_1.jpg"), Color.BLACK);
		writeSolidImage(new File(cameraRoot, "20260809_120100_1.jpg"), Color.WHITE);

		CameraConfig camera = realCamera(cameraRoot, tempDir);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		CameraImageCaches imageCaches = new CameraImageCaches();

		RealCameraScrubber.Result first = RealCameraScrubber.composite(camera, cache, t0, true, IMAGE_SIZE, IMAGE_SIZE,
				defaultOptions(), null, null, imageCaches);
		RealCameraScrubber.Result second = RealCameraScrubber.composite(camera, cache, t0 + 60_000L, true, IMAGE_SIZE,
				IMAGE_SIZE, defaultOptions(), null, null, imageCaches);

		assertNotEquals(rgbNoAlpha(first.getImage(), 2, 2), rgbNoAlpha(second.getImage(), 2, 2),
				"scrubbing to a different archived frame must re-read, not keep serving the first cached image");
	}

	private void writeSolidImage(File file, Color color) throws IOException {
		BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(color);
		g2d.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
		g2d.dispose();
		ImageIO.write(image, "jpg", file);
	}

	// The user's own real, previously-calibrated coefficients for a south-facing camera.
	private static final double CALIBRATED_A = -0.015173144276557696;
	private static final double CALIBRATED_B = -0.026200973539670214;
	private static final double CALIBRATED_C = 9.254249203305798E-4;
	private static final double CALIBRATED_D = 1.0578540561260015;

	// Direct user instruction: distortion only makes sense while a real photo is actually on screen
	// to align against. Proven by pointing the camera 5 degrees off the sun (distortion has no
	// visible effect exactly at boresight) and comparing the sun's rendered pixel distance from
	// center WITH vs WITHOUT the coefficients configured, holding imageShown fixed at true - the same
	// methodology the earlier zoom-fix tests used (PlateSolveSessionTest.
	// adjustZoomActuallyChangesTheRenderedFieldOfView).
	@Test
	void distortionAppliesWhenTheRealImageIsShown(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));

		CameraConfig camera = realCameraOffsetFromTheSun(cameraRoot, tempDir, t0);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		RealCameraScrubber.Result idealResult = RealCameraScrubber.composite(camera, cache, t0, true, IMAGE_SIZE,
				IMAGE_SIZE, defaultOptions());
		int idealDistance = distanceFromCenterToSunPixel(idealResult.getImage());

		((me.qbert.skywatch.camera.projection.AbstractCameraProjection) camera.getProjection())
				.setDistortionCoefficients(CALIBRATED_A, CALIBRATED_B, CALIBRATED_C, CALIBRATED_D);
		RealCameraScrubber.Result distortedResult = RealCameraScrubber.composite(camera, cache, t0, true, IMAGE_SIZE,
				IMAGE_SIZE, defaultOptions());
		int distortedDistance = distanceFromCenterToSunPixel(distortedResult.getImage());

		assertTrue(Math.abs(distortedDistance - idealDistance) >= 1,
				"the user's real barrel-distortion coefficients must actually move the sun's rendered position "
						+ "while the real image is shown (ideal=" + idealDistance + "px, distorted=" + distortedDistance + "px)");
	}

	// The mirror-image proof: the SAME camera, SAME real distortion coefficients configured, but
	// imageShown=false this time - the sun's rendered position must be UNCHANGED from the
	// no-distortion baseline, proving distortionEnabled is forced off rather than merely defaulting
	// to on and happening to not matter.
	@Test
	void distortionDoesNotApplyWhenTheRealImageIsHiddenEvenWithCoefficientsConfigured(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();

		CameraConfig camera = realCameraOffsetFromTheSun(cameraRoot, tempDir, t0);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		RealCameraScrubber.Result idealResult = RealCameraScrubber.composite(camera, cache, t0, false, IMAGE_SIZE,
				IMAGE_SIZE, defaultOptions());
		int idealDistance = distanceFromCenterToSunPixel(idealResult.getImage());

		((me.qbert.skywatch.camera.projection.AbstractCameraProjection) camera.getProjection())
				.setDistortionCoefficients(CALIBRATED_A, CALIBRATED_B, CALIBRATED_C, CALIBRATED_D);
		RealCameraScrubber.Result distortedResult = RealCameraScrubber.composite(camera, cache, t0, false, IMAGE_SIZE,
				IMAGE_SIZE, defaultOptions());
		int distortedDistance = distanceFromCenterToSunPixel(distortedResult.getImage());

		assertEquals(idealDistance, distortedDistance,
				"distortion must be forced off while the real image is hidden, even with real coefficients configured");
	}

	// A camera pointed 5 degrees off the sun's own azimuth at t0 (distortion has no visible effect
	// exactly at boresight) - the same "point off-target, measure pixel distance" setup the zoom-fix
	// tests established.
	private CameraConfig realCameraOffsetFromTheSun(File cameraRoot, File tempDir, long epochMillis) throws Exception {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		ObjectDirectionAltAz sunAltAz = sunAltAzAt(epochMillis, location);

		CameraConfig camera = realCamera(cameraRoot, tempDir);
		// A later effectiveFrom than realCamera(...)'s own initial entry (0L) so this one wins
		// CalibrationHistory.latestAsOf(...)'s "newest entry at or before the query time" lookup,
		// rather than trying to remove the unmodifiable-list entry realCamera(...) already appended.
		camera.getCalibrationHistory().append(new CalibrationEntry(1L,
				new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth() + 5.0, 0.0), 200.0, 45.0, -75.0));
		return camera;
	}

	// Scans for the sun's own color and returns its pixel distance from the canvas center - matches
	// PlateSolveSessionTest/BatchReprocessorTest's own identical helper from the earlier zoom fix.
	private int distanceFromCenterToSunPixel(BufferedImage image) {
		int sunColor = rgbNoAlpha(ColorPresets.defaultScheme().getSunColor());
		int centerX = image.getWidth() / 2;
		int centerY = image.getHeight() / 2;
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				if (rgbNoAlpha(image, x, y) == sunColor) {
					int dx = x - centerX;
					int dy = y - centerY;
					return (int) Math.round(Math.sqrt(dx * dx + dy * dy));
				}
		return Integer.MAX_VALUE;
	}

	private int rgbNoAlpha(BufferedImage image, int x, int y) {
		return image.getRGB(x, y) & 0x00FFFFFF;
	}

	private int rgbNoAlpha(Color color) {
		return color.getRGB() & 0x00FFFFFF;
	}

	private ObservationTime observationTimeAt(long epochMillis) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(java.util.TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		return time;
	}

	private ObserverLocation observerLocationAt(double latitude, double longitude) {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);
		return location;
	}

	private ObjectDirectionAltAz sunAltAzAt(long epochMillis, ObserverLocation location) throws Exception {
		ObservationTime time = observationTimeAt(epochMillis);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		return sun.getCurrentDirectionAsAltitudeAzimuth();
	}

	@Test
	void scrubsARealArchivedCameraDirectory() throws Exception {
		// The user's own real camera captures, checked into the module at cameras/1 - one JPEG per
		// minute from 12:00:00Z to 12:45:00Z on 2026-08-09, plus a "latest.jpg" this class never
		// touches (that's LiveCameraSaver's concern, not scrubbing's).
		File cameraRoot = new File("cameras/1");
		assertTrue(cameraRoot.isDirectory(), "expected cameras/1 real capture data to exist at the module root");

		CameraConfig camera = realCamera(cameraRoot, null);
		DirectoryCache cache = new DirectoryCache(java.nio.file.Files.createTempDirectory("real-camera-scrub-cache").toFile());

		// 12:07:30Z - 30 seconds after the 12:07:00 frame, before the 12:08:00 one - must snap back
		// to 12:07:00.
		long target = Instant.parse("2026-08-09T12:07:30Z").toEpochMilli();
		long expectedFrame = Instant.parse("2026-08-09T12:07:00Z").toEpochMilli();

		RealCameraScrubber.Result result = RealCameraScrubber.composite(camera, cache, target, true,
				IMAGE_SIZE, IMAGE_SIZE, defaultOptions());

		assertEquals(expectedFrame, result.getRenderedEpochMillis(), "should snap to the real 12:07:00 capture");
		assertTrue(result.isRealImageShown());
		assertEquals(2560, result.getImage().getWidth(), "expected the real capture's own resolution, not the fallback canvas size");
		assertEquals(1440, result.getImage().getHeight());
		assertEquals(BufferedImage.TYPE_INT_RGB, result.getImage().getType(), "expected an opaque RGB result, not ARGB");
		assertFalse(hasAnyImageIoJpegWriteFailure(result.getImage()), "expected the composited real frame to be writable as JPEG");
	}

	private boolean hasAnyImageIoJpegWriteFailure(BufferedImage image) throws IOException {
		File tempFile = File.createTempFile("scrub-test", ".jpg");
		tempFile.deleteOnExit();
		boolean wrote = ImageIO.write(image, "jpg", tempFile);
		return !wrote;
	}

	// cameraRoot's parent (tempDir, or cameraRoot's own grandparent for the real-data case) is
	// where the "latest.jpg" live path and the DirectoryCache both live - mirrors the real
	// prototype layout (CLAUDE.md's "Image sources"), even though this class never reads "latest".
	private CameraConfig realCamera(File cameraRoot, File tempDir) {
		RealImageSource source = RealImageSource.liveAndRecorded(new File(cameraRoot, "latest.jpg").getPath(),
				cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg", TimezoneSetting.explicit(ZoneOffset.UTC),
				DstAmbiguousPolicy.ASSUME_STANDARD);

		CameraConfig camera = new CameraConfig("cam1", CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setRealImageSource(source);
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory()
				.append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));
		return camera;
	}

	private FrameCompositor.Options defaultOptions() {
		return new FrameCompositor.Options()
				.setStars(Collections.<StarCoordinate>emptyList())
				.setColorScheme(ColorPresets.defaultScheme())
				.setMinSunMoonRadiusPixels(5.0);
	}

	private void writeBlankImage(File file) throws IOException {
		BufferedImage image = new BufferedImage(RealCameraScrubberTest.IMAGE_SIZE, RealCameraScrubberTest.IMAGE_SIZE,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
		g2d.dispose();
		ImageIO.write(image, "jpg", file);
	}
}
