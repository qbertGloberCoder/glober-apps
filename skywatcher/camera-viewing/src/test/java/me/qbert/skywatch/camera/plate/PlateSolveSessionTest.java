package me.qbert.skywatch.camera.plate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.TimeZone;

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
import me.qbert.skywatch.camera.config.VirtualImageSource;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Task 7.1's manual plate-solve Save/Revert core support - task 0.7's live-edit model, Fixed-camera
// case only.
class PlateSolveSessionTest {

	private static final int IMAGE_SIZE = 200;
	private static final long FRAME_EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

	@Test
	void rejectsACameraWithNoCalibrationHistoryYet(@TempDir File tempDir) {
		CameraConfig camera = new CameraConfig("backyard", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));

		assertThrows(IllegalStateException.class, () -> new PlateSolveSession(camera, new File(tempDir, "camera.properties")));
	}

	@Test
	void rejectsAPtzCamera(@TempDir File tempDir) {
		CameraConfig camera = new CameraConfig("ptz-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.explicit(45.0, -75.0));

		assertThrows(IllegalArgumentException.class, () -> new PlateSolveSession(camera, new File(tempDir, "camera.properties")));
	}

	@Test
	void pendingValuesStartAtTheLatestCalibrationEntry(@TempDir File tempDir) throws Exception {
		CameraConfig camera = fixedCameraWithOneCalibration();

		PlateSolveSession session = new PlateSolveSession(camera, new File(tempDir, "camera.properties"));

		assertFalse(session.hasPendingEdit());
		assertEquals(10.0, session.getPendingOrientation().getAltitude(), 0.0001);
		assertEquals(90.0, session.getPendingOrientation().getAzimuth(), 0.0001);
		assertEquals(45.0, session.getPendingLatitude(), 0.0001);
		assertEquals(-75.0, session.getPendingLongitude(), 0.0001);
		assertEquals(50.0, session.getPendingZoom(), 0.0001);
	}

	@Test
	void adjustingSetsAPendingEditWithoutTouchingCalibrationHistory(@TempDir File tempDir) throws Exception {
		CameraConfig camera = fixedCameraWithOneCalibration();
		PlateSolveSession session = new PlateSolveSession(camera, new File(tempDir, "camera.properties"));

		session.adjustOrientation(new Orientation(20.0, 100.0, 2.0));
		session.adjustLocation(46.0, -76.0);
		session.adjustZoom(55.0);

		assertTrue(session.hasPendingEdit());
		assertEquals(20.0, session.getPendingOrientation().getAltitude(), 0.0001);
		assertEquals(46.0, session.getPendingLatitude(), 0.0001);
		assertEquals(55.0, session.getPendingZoom(), 0.0001);
		assertEquals(1, camera.getCalibrationHistory().getEntries().size(),
				"an unsaved adjustment must never touch the append-only calibration history");
	}

	@Test
	void renderUsesThePendingOrientationNotTheSavedOne(@TempDir File tempDir) throws Exception {
		CameraConfig camera = fixedCameraWithOneCalibration(); // saved orientation points nowhere near the sun
		PlateSolveSession session = new PlateSolveSession(camera, new File(tempDir, "camera.properties"));

		ObservationTime time = observationTimeAt(FRAME_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();

		session.adjustOrientation(new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0));

		BufferedImage stillFrame = blankImage();
		BufferedImage rendered = session.render(stillFrame, time, defaultOptions());

		assertEquals(rgbNoAlpha(ColorPresets.defaultScheme().getSunColor()),
				rgbNoAlpha(rendered, IMAGE_SIZE / 2, IMAGE_SIZE / 2),
				"the sun should render dead center now that the PENDING (not saved) orientation points at it");
	}

	@Test
	void adjustZoomActuallyChangesTheRenderedFieldOfView(@TempDir File tempDir) throws Exception {
		// Real user report: "the zoom doesn't work" - render() used to always use the camera's
		// fixed, persisted CameraProjection, completely ignoring pendingZoom. Proven here directly:
		// point the camera at a fixed angular offset from the sun (not dead center - zoom has no
		// visible effect exactly at center), then confirm the sun's rendered pixel distance from
		// center is measurably LARGER at a longer focal length (narrower FOV) than at a shorter one,
		// for the exact same real-world angular offset - the defining, real-world effect of "zoom."
		CameraConfig camera = fixedCameraWithOneCalibration();
		PlateSolveSession session = new PlateSolveSession(camera, new File(tempDir, "camera.properties"));

		ObservationTime time = observationTimeAt(FRAME_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();

		// 5 degrees off the sun's own azimuth - close enough to stay on a 50mm lens's canvas, far
		// enough to move measurably under a focal-length change.
		session.adjustOrientation(new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth() + 5.0, 0.0));

		BufferedImage stillFrame = blankImage();

		session.adjustZoom(20.0); // wide - the 5-degree offset should map to a SMALL pixel distance
		BufferedImage wide = session.render(stillFrame, time, defaultOptions());
		int wideDistance = distanceFromCenterToSunPixel(wide);

		session.adjustZoom(200.0); // narrow/telephoto - the SAME 5-degree offset should map further out
		BufferedImage narrow = session.render(stillFrame, time, defaultOptions());
		int narrowDistance = distanceFromCenterToSunPixel(narrow);

		assertTrue(narrowDistance > wideDistance * 2,
				"a longer focal length must place the same real angular offset measurably further from center "
						+ "(wide=" + wideDistance + "px, narrow=" + narrowDistance + "px) - zoom must actually affect the render");
	}

	// Direct user instruction: distortion only makes sense for a Real camera's real photo -
	// PlateSolveSession itself is reused for a Virtual Fixed camera's ordinary orientation editing too
	// (CLAUDE.md: "have the same save/revert rules as a real camera"), so this proves render(...)
	// actually gates on camera kind, not just "always on since a real stillFrame is always supplied."
	@Test
	void distortionAppliesForARealCameraButNotForAVirtualFixedCamera(@TempDir File tempDir) throws Exception {
		ObservationTime time = observationTimeAt(FRAME_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		Orientation offsetFromSun = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth() + 5.0, 0.0);
		BufferedImage stillFrame = blankImage();

		CameraConfig realCamera = fixedCameraWithOneCalibration();
		PlateSolveSession realSession = new PlateSolveSession(realCamera, new File(tempDir, "real.properties"));
		realSession.adjustOrientation(offsetFromSun);
		int realIdealDistance = distanceFromCenterToSunPixel(realSession.render(stillFrame, time, defaultOptions()));
		((me.qbert.skywatch.camera.projection.AbstractCameraProjection) realCamera.getProjection())
				.setDistortionCoefficients(-0.015173144276557696, -0.026200973539670214, 9.254249203305798E-4,
						1.0578540561260015);
		int realDistortedDistance = distanceFromCenterToSunPixel(realSession.render(stillFrame, time, defaultOptions()));
		assertTrue(Math.abs(realDistortedDistance - realIdealDistance) >= 1,
				"a Real camera's session must actually apply distortion (ideal=" + realIdealDistance + "px, distorted="
						+ realDistortedDistance + "px)");

		CameraConfig virtualCamera = fixedVirtualCameraWithOneCalibration();
		PlateSolveSession virtualSession = new PlateSolveSession(virtualCamera, new File(tempDir, "virtual.properties"));
		virtualSession.adjustOrientation(offsetFromSun);
		int virtualIdealDistance = distanceFromCenterToSunPixel(virtualSession.render(stillFrame, time, defaultOptions()));
		((me.qbert.skywatch.camera.projection.AbstractCameraProjection) virtualCamera.getProjection())
				.setDistortionCoefficients(-0.015173144276557696, -0.026200973539670214, 9.254249203305798E-4,
						1.0578540561260015);
		int virtualDistortedDistance = distanceFromCenterToSunPixel(virtualSession.render(stillFrame, time, defaultOptions()));
		assertEquals(virtualIdealDistance, virtualDistortedDistance,
				"a Virtual Fixed camera's session must never apply distortion, even with real coefficients configured");
	}

	private CameraConfig fixedVirtualCameraWithOneCalibration() {
		CameraConfig camera = new CameraConfig("virtual-static", CameraType.virtual(VirtualImageSource.STATIC_DIRECTIONAL),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(10.0, 90.0, 0.0), 50.0, 45.0, -75.0));
		return camera;
	}

	// Scans for the sun's own color and returns its pixel distance from the canvas center - null-
	// safe (returns Integer.MAX_VALUE, which would fail the calling assertion loudly) if not found,
	// rather than silently passing on a vacuous "0 == 0" comparison.
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

	@Test
	void saveAppendsANewCalibrationEntryAndPersistsToFile(@TempDir File tempDir) throws Exception {
		File configFile = new File(tempDir, "camera.properties");
		CameraConfig camera = fixedCameraWithOneCalibration();
		CameraConfigStore.save(camera, configFile);

		PlateSolveSession session = new PlateSolveSession(camera, configFile);
		session.adjustOrientation(new Orientation(20.0, 100.0, 2.0));
		session.adjustLocation(46.0, -76.0);
		session.adjustZoom(55.0);

		session.save(86_400_000L);

		assertFalse(session.hasPendingEdit(), "save() commits the pending edit");
		assertEquals(2, camera.getCalibrationHistory().getEntries().size());
		CalibrationEntry appended = camera.getCalibrationHistory().getEntries().get(1);
		assertEquals(86_400_000L, appended.getEffectiveFromEpochMillis());
		assertEquals(20.0, appended.getOrientation().getAltitude(), 0.0001);
		assertEquals(46.0, appended.getLatitude(), 0.0001);
		assertEquals(55.0, appended.getZoom(), 0.0001);

		CameraConfig reloaded = CameraConfigStore.load(configFile);
		assertEquals(2, reloaded.getCalibrationHistory().getEntries().size(), "save() must persist to the file, not just in memory");
	}

	@Test
	void revertDiscardsAnUnsavedEditAndReloadsFromTheFile(@TempDir File tempDir) throws Exception {
		File configFile = new File(tempDir, "camera.properties");
		CameraConfig camera = fixedCameraWithOneCalibration();
		CameraConfigStore.save(camera, configFile);

		PlateSolveSession session = new PlateSolveSession(camera, configFile);
		session.adjustOrientation(new Orientation(99.0, 199.0, 9.0));
		assertTrue(session.hasPendingEdit());

		session.revert();

		assertFalse(session.hasPendingEdit());
		assertEquals(10.0, session.getPendingOrientation().getAltitude(), 0.0001,
				"revert must reset to whatever was actually persisted, discarding the unsaved 99.0 edit");
		assertEquals(1, session.getCameraConfig().getCalibrationHistory().getEntries().size());
	}

	@Test
	void revertAfterASaveKeepsTheSavedEntryNotTheOriginalOne(@TempDir File tempDir) throws Exception {
		File configFile = new File(tempDir, "camera.properties");
		CameraConfig camera = fixedCameraWithOneCalibration();
		CameraConfigStore.save(camera, configFile);

		PlateSolveSession session = new PlateSolveSession(camera, configFile);
		session.adjustOrientation(new Orientation(20.0, 100.0, 0.0));
		session.save(86_400_000L); // now actually persisted

		session.adjustOrientation(new Orientation(999.0, 999.0, 0.0)); // a second, unsaved edit
		session.revert();

		assertEquals(20.0, session.getPendingOrientation().getAltitude(), 0.0001,
				"revert reloads the CURRENT file state (including the earlier save), not the pre-session original");
		assertEquals(2, session.getCameraConfig().getCalibrationHistory().getEntries().size());
	}

	private CameraConfig fixedCameraWithOneCalibration() {
		CameraConfig camera = new CameraConfig("backyard-east", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(10.0, 90.0, 0.0), 50.0, 45.0, -75.0));
		return camera;
	}

	private FrameCompositor.Options defaultOptions() {
		return new FrameCompositor.Options()
				.setStars(Collections.<StarCoordinate>emptyList())
				.setColorScheme(ColorPresets.defaultScheme())
				.setMinSunMoonRadiusPixels(5.0);
	}

	private BufferedImage blankImage() {
		BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
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
