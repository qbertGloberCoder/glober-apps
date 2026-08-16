package me.qbert.skywatch.camera.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraConfigStore;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.plate.PlateSolveSession;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.source.DirectoryCache;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Task 8.2's headless-testable core - CalibrationController wraps already-tested pieces
// (plate.PlateSolveSession, source.ArchiveFrameScanner), so these tests focus on the piece that's
// new here: finding the right archived frame to preview against, and confirming the PENDING
// (not saved) orientation is what actually drives the preview.
class CalibrationControllerTest {

	private static final int IMAGE_SIZE = 200;

	@Test
	void previewUsesThePendingOrientationAgainstTheClosestArchivedFrame(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));

		ObserverLocation location = observerLocationAt(45.0, -75.0);
		ObjectDirectionAltAz sunAltAz = sunAltAzAt(t0, location);

		CameraConfig camera = realCamera(cameraRoot); // saved calibration points nowhere near the sun
		PlateSolveSession session = new PlateSolveSession(camera, new File(tempDir, "camera.properties"));
		session.adjustOrientation(new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0));

		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		CalibrationController controller = new CalibrationController(session, cache, defaultOptions());

		BufferedImage preview = controller.renderPreview(t0 + 5_000L); // 5s after the frame's own timestamp

		assertEquals(rgbNoAlpha(ColorPresets.defaultScheme().getSunColor()), rgbNoAlpha(preview, IMAGE_SIZE / 2, IMAGE_SIZE / 2),
				"the sun should render centered now that the PENDING orientation points at it");
	}

	@Test
	void previewThrowsWhenNoArchivedFrameExistsYet(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));

		CameraConfig camera = realCamera(cameraRoot);
		PlateSolveSession session = new PlateSolveSession(camera, new File(tempDir, "camera.properties"));
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		CalibrationController controller = new CalibrationController(session, cache, defaultOptions());

		assertThrows(IllegalStateException.class, () -> controller.renderPreview(t0 - 60_000L));
	}

	@Test
	void consecutivePreviewsReuseTheMemoizedArchiveScanRatherThanRescanningEveryTime(@TempDir File tempDir)
			throws Exception {
		// Real user report: renderPreview(...) is called on every single spinner drag while
		// calibrating - re-walking the whole archive tree on every one of those stalls the UI for a
		// large archive. CalibrationController wires an ArchiveFrameCache (default 5s refresh
		// interval) under the hood - two back-to-back calls (real elapsed time far under 5s) must
		// both resolve against the SAME scanned frame list. Proven directly here via image size (like
		// PreviewControllerTest's own equivalent test): a differently-sized frame added closer to the
		// target time between the two calls must NOT be picked up by the second one.
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeSizedImage(new File(cameraRoot, "20260809_120000_1.jpg"), 80, 60);

		CameraConfig camera = realCamera(cameraRoot);
		PlateSolveSession session = new PlateSolveSession(camera, new File(tempDir, "camera.properties"));
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		CalibrationController controller = new CalibrationController(session, cache, defaultOptions());

		BufferedImage first = controller.renderPreview(t0 + 5_000L); // first render - performs the initial scan
		assertEquals(80, first.getWidth());

		// A closer frame appears, timestamped between the first frame and the target time above, at
		// a DIFFERENT resolution - if the second call re-scanned, frameAtOrBefore(...) would snap to
		// this one instead, changing the preview's own canvas size.
		writeSizedImage(new File(cameraRoot, "20260809_120003_1.jpg"), 40, 30);

		BufferedImage second = controller.renderPreview(t0 + 5_000L);
		assertEquals(80, second.getWidth(), "the memoized (pre-addition) frame list should still be in effect");
		assertEquals(60, second.getHeight());
	}

	@Test
	void adjustAndSaveAndRevertDelegateToTheSession(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));
		File configFile = new File(tempDir, "camera.properties");

		CameraConfig camera = realCamera(cameraRoot);
		CameraConfigStore.save(camera, configFile);
		PlateSolveSession session = new PlateSolveSession(camera, configFile);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		CalibrationController controller = new CalibrationController(session, cache, defaultOptions());

		controller.adjustOrientation(new Orientation(20.0, 100.0, 2.0));
		controller.adjustLocation(46.0, -76.0);
		controller.adjustZoom(55.0);
		assertTrue(controller.hasPendingEdit());

		controller.save(86_400_000L);
		assertFalse(controller.hasPendingEdit());
		assertEquals(2, session.getCameraConfig().getCalibrationHistory().getEntries().size());

		controller.adjustOrientation(new Orientation(999.0, 999.0, 0.0));
		assertTrue(controller.hasPendingEdit());
		controller.revert();
		assertFalse(controller.hasPendingEdit());
		assertEquals(20.0, controller.getSession().getPendingOrientation().getAltitude(), 0.0001,
				"revert should reset to the just-saved entry, discarding the unsaved 999.0 edit");
	}

	private CameraConfig realCamera(File cameraRoot) {
		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		CameraConfig camera = new CameraConfig("cal-cam", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setRealImageSource(source);
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));
		return camera;
	}

	private FrameCompositor.Options defaultOptions() {
		return new FrameCompositor.Options()
				.setStars(Collections.<StarCoordinate>emptyList())
				.setColorScheme(ColorPresets.defaultScheme())
				.setMinSunMoonRadiusPixels(5.0);
	}

	private void writeSizedImage(File file, int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, width, height);
		g2d.dispose();
		ImageIO.write(image, "jpg", file);
	}

	private void writeBlankImage(File file) throws IOException {
		BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
		g2d.dispose();
		ImageIO.write(image, "jpg", file);
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
}
