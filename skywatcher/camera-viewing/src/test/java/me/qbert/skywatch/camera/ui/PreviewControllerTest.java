package me.qbert.skywatch.camera.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.TimeZone;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.clock.SimulatedClock;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.source.DirectoryCache;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Task 8.1's headless-testable core. PreviewController wraps already-tested pieces
// (batch.CameraImageDispatch, clock.SimulatedClock) - these tests focus on confirming the WIRING
// (defaults, toggles, canvas-size propagation, the clock actually driving which frame renders),
// not re-proving CameraImageDispatch's own scrubbing/compositing correctness.
class PreviewControllerTest {

	private static final int CANVAS_SIZE = 150;
	// 2026-08-09T17:00:00Z - confirmed sun altitude +60.7deg at lat 45/lon -75 (this file's cameras'
	// own location), comfortably above render.Layer1DuskFade's 0deg full-brightness threshold.
	private static final long DAYTIME_EPOCH_MILLIS = Instant.parse("2026-08-09T17:00:00Z").toEpochMilli();

	@Test
	void rejectsNullConstructorArguments(@TempDir File tempDir) {
		CameraConfig camera = realCamera(new File(tempDir, "cameras/1"));
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		FrameCompositor.Options options = defaultOptions();
		SimulatedClock clock = new SimulatedClock();

		assertThrows(IllegalArgumentException.class,
				() -> new PreviewController(null, cache, options, clock, CANVAS_SIZE, CANVAS_SIZE));
		assertThrows(IllegalArgumentException.class,
				() -> new PreviewController(camera, cache, null, clock, CANVAS_SIZE, CANVAS_SIZE));
		assertThrows(IllegalArgumentException.class,
				() -> new PreviewController(camera, cache, options, null, CANVAS_SIZE, CANVAS_SIZE));
	}

	@Test
	void rejectsNonPositiveCanvasDimensions(@TempDir File tempDir) {
		CameraConfig camera = realCamera(new File(tempDir, "cameras/1"));
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		assertThrows(IllegalArgumentException.class,
				() -> new PreviewController(camera, cache, defaultOptions(), new SimulatedClock(), 0, CANVAS_SIZE));
		assertThrows(IllegalArgumentException.class,
				() -> new PreviewController(camera, cache, defaultOptions(), new SimulatedClock(), CANVAS_SIZE, -1));

		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), new SimulatedClock(),
				CANVAS_SIZE, CANVAS_SIZE);
		assertThrows(IllegalArgumentException.class, () -> controller.setCanvasSize(0, CANVAS_SIZE));
	}

	@Test
	void imageShownDefaultsToTrue(@TempDir File tempDir) {
		CameraConfig camera = realCamera(new File(tempDir, "cameras/1"));
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), new SimulatedClock(),
				CANVAS_SIZE, CANVAS_SIZE);

		assertTrue(controller.isImageShown());
	}

	@Test
	void rendersTheRealArchivedFrameAtItsOwnResolutionWhenShown(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeOpaqueImage(new File(cameraRoot, "20260809_120000_1.jpg"), 80, 60, Color.RED);

		CameraConfig camera = realCamera(cameraRoot);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.setTime(t0 + 5_000L);
		clock.pause();

		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);

		BufferedImage frame = controller.renderCurrentFrame();

		assertEquals(80, frame.getWidth(), "shown mode should use the real archived frame's own resolution");
		assertEquals(60, frame.getHeight());
	}

	@Test
	void consecutiveRendersReuseTheMemoizedArchiveScanRatherThanRescanningEveryTime(@TempDir File tempDir)
			throws Exception {
		// Real user report: the render loop calls renderCurrentFrame() up to 4 times a second while
		// playing - re-walking the whole archive tree on every single one of those stalls the UI for
		// a large archive. PreviewController wires an ArchiveFrameCache (default 5s refresh interval)
		// under the hood - two back-to-back calls (real elapsed time far under 5s) must both resolve
		// against the SAME scanned frame list, so a frame added between them isn't picked up yet.
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeOpaqueImage(new File(cameraRoot, "20260809_120000_1.jpg"), 80, 60, Color.RED);

		CameraConfig camera = realCamera(cameraRoot);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.setTime(t0 + 90_000L); // between the 120000 frame and a not-yet-added 120100 one
		clock.pause();

		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);
		controller.renderCurrentFrame(); // first render - performs the initial scan

		writeOpaqueImage(new File(cameraRoot, "20260809_120100_1.jpg"), 40, 30, Color.BLUE);
		BufferedImage second = controller.renderCurrentFrame();

		assertEquals(80, second.getWidth(), "the memoized (pre-addition) frame list should still be in effect");
		assertEquals(60, second.getHeight());
	}

	@Test
	void rendersAtTheCanvasSizeWhenHidden(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeOpaqueImage(new File(cameraRoot, "20260809_120000_1.jpg"), 80, 60, Color.RED);

		CameraConfig camera = realCamera(cameraRoot);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.setTime(t0 + 5_000L);
		clock.pause();

		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);
		controller.setImageShown(false);

		BufferedImage frame = controller.renderCurrentFrame();

		assertEquals(CANVAS_SIZE, frame.getWidth(), "hidden mode should fall back to the controller's own canvas size");
		assertEquals(CANVAS_SIZE, frame.getHeight());
	}

	@Test
	void setCanvasSizeChangesTheHiddenRenderSize(@TempDir File tempDir) throws Exception {
		CameraConfig camera = realCamera(new File(tempDir, "cameras/1"));
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.pause();

		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);
		controller.setImageShown(false);
		controller.setCanvasSize(321, 240);

		BufferedImage frame = controller.renderCurrentFrame();

		assertEquals(321, frame.getWidth());
		assertEquals(240, frame.getHeight());
		assertEquals(321, controller.getCanvasWidthPixels());
		assertEquals(240, controller.getCanvasHeightPixels());
	}

	@Test
	void hiddenModeCropsFromASquareRenderMatchingTheLongerDimensionLandscape(@TempDir File tempDir) throws Exception {
		// The resize/FOV fix: a wider-than-tall canvas should be exactly the centered vertical crop
		// of a square render at the wider (longer) dimension - "FOV = max(width,height), crop the
		// sensor" rather than letterboxing a narrower FOV to fit.
		CameraConfig camera = realCamera(new File(tempDir, "cameras/1"));
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.pause();
		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);
		controller.setImageShown(false);

		int wide = 300;
		int tall = 150;

		controller.setCanvasSize(wide, wide);
		BufferedImage square = controller.renderCurrentFrame();

		controller.setCanvasSize(wide, tall);
		BufferedImage cropped = controller.renderCurrentFrame();

		assertEquals(wide, cropped.getWidth());
		assertEquals(tall, cropped.getHeight());

		int cropY = (wide - tall) / 2;
		for (int x = 0; x < wide; x += 20)
			for (int y = 0; y < tall; y += 20)
				assertEquals(square.getRGB(x, y + cropY), cropped.getRGB(x, y),
						"pixel (" + x + "," + y + ") should match the square render's corresponding cropped row");
	}

	@Test
	void hiddenModeCropsFromASquareRenderMatchingTheLongerDimensionPortrait(@TempDir File tempDir) throws Exception {
		CameraConfig camera = realCamera(new File(tempDir, "cameras/1"));
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.pause();
		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);
		controller.setImageShown(false);

		int narrow = 150;
		int tallSize = 300;

		controller.setCanvasSize(tallSize, tallSize);
		BufferedImage square = controller.renderCurrentFrame();

		controller.setCanvasSize(narrow, tallSize);
		BufferedImage cropped = controller.renderCurrentFrame();

		assertEquals(narrow, cropped.getWidth());
		assertEquals(tallSize, cropped.getHeight());

		int cropX = (tallSize - narrow) / 2;
		for (int x = 0; x < narrow; x += 20)
			for (int y = 0; y < tallSize; y += 20)
				assertEquals(square.getRGB(x + cropX, y), cropped.getRGB(x, y),
						"pixel (" + x + "," + y + ") should match the square render's corresponding cropped column");
	}

	@Test
	void theClockControlsWhichArchivedFrameIsShown(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		long t1 = t0 + 60_000L;
		writeOpaqueImage(new File(cameraRoot, "20260809_120000_1.jpg"), 40, 40, Color.RED);
		writeOpaqueImage(new File(cameraRoot, "20260809_120100_1.jpg"), 40, 40, Color.BLUE);

		CameraConfig camera = realCamera(cameraRoot);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.pause();

		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);

		// A corner pixel, unlikely to be overdrawn by any computed sun/moon/star glyph. Compared by
		// dominant channel rather than exact equality - JPEG's lossy compression can shift a solid
		// fill by a few units (confirmed: 0xFF0000 round-tripped as 0xFE0000 here), which an exact
		// match would wrongly fail on.
		clock.setTime(t0 + 5_000L);
		BufferedImage atT0 = controller.renderCurrentFrame();
		assertTrue(redChannel(atT0.getRGB(0, 0)) > blueChannel(atT0.getRGB(0, 0)), "expected the RED frame at t0");

		clock.setTime(t1 + 5_000L);
		BufferedImage atT1 = controller.renderCurrentFrame();
		assertTrue(blueChannel(atT1.getRGB(0, 0)) > redChannel(atT1.getRGB(0, 0)), "expected the BLUE frame at t1");
	}

	// A real user report: the control panel's Time tab spinners never reflected what was actually
	// rendered. getLastRenderedEpochMillis() is what ui.ControlPanel.refreshTimeFields() reads to fix
	// that - proven here at the source: scrubbing to a target 30 seconds after the 12:00:00 frame
	// must expose the SNAPPED frame's own timestamp (12:00:00), not the raw scrub target (12:00:30).
	@Test
	void getLastRenderedEpochMillisReflectsTheSnappedArchivedFrameNotTheRawScrubTarget(@TempDir File tempDir)
			throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeOpaqueImage(new File(cameraRoot, "20260809_120000_1.jpg"), 40, 40, Color.RED);

		CameraConfig camera = realCamera(cameraRoot);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.pause();
		clock.setTime(t0 + 30_000L);

		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);
		controller.renderCurrentFrame();

		assertEquals(t0, controller.getLastRenderedEpochMillis(),
				"should reflect the snapped 12:00:00 frame's own timestamp, not the raw 12:00:30 scrub target");
	}

	@Test
	void getLastRenderedEpochMillisReflectsTheRawTargetWhenHidden(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();

		CameraConfig camera = realCamera(cameraRoot);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.pause();
		clock.setTime(t0 + 30_000L);

		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);
		controller.setImageShown(false);
		controller.renderCurrentFrame();

		assertEquals(t0 + 30_000L, controller.getLastRenderedEpochMillis(),
				"hidden mode has no frame to snap to - should reflect the exact clock target");
	}

	private int redChannel(int rgb) {
		return (rgb >> 16) & 0xFF;
	}

	private int blueChannel(int rgb) {
		return rgb & 0xFF;
	}

	private CameraConfig realCamera(File cameraRoot) {
		cameraRoot.mkdirs();
		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		CameraConfig camera = new CameraConfig("preview-cam", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setRealImageSource(source);
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 1.0, 45.0, -75.0));
		return camera;
	}

	private FrameCompositor.Options defaultOptions() {
		return new FrameCompositor.Options()
				.setStars(Collections.<StarCoordinate>emptyList())
				.setColorScheme(ColorPresets.defaultScheme())
				.setMinSunMoonRadiusPixels(5.0);
	}

	private void writeOpaqueImage(File file, int width, int height, Color color) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(color);
		g2d.fillRect(0, 0, width, height);
		g2d.dispose();
		ImageIO.write(image, "jpg", file);
	}

	// Left half (source x in [0,180)) red, right half blue - matches
	// EquirectangularSceneRendererTest's own two-color fixture and its corrected, CENTERED azimuth
	// convention (azimuth=0 is the source image's own center - see that class's fix this round).
	private CameraConfig virtualPtzCamera(File sceneFile, double initialAzimuthDegrees) throws IOException {
		BufferedImage source = new BufferedImage(360, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = source.createGraphics();
		g2d.setColor(Color.RED);
		g2d.fillRect(0, 0, 180, 1);
		g2d.setColor(Color.BLUE);
		g2d.fillRect(180, 0, 180, 1);
		g2d.dispose();
		ImageIO.write(source, "png", sceneFile);

		CameraConfig camera = new CameraConfig("ptz-preview-cam",
				me.qbert.skywatch.camera.config.CameraType.virtual(me.qbert.skywatch.camera.config.VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.setVirtualScenePath(sceneFile.getPath());
		camera.setCurrentOrientation(new Orientation(0.0, initialAzimuthDegrees, 0.0));
		return camera;
	}

	// --- Plate-solve marking mode (a direct user request: click-to-mark moved from a small dedicated
	// panel - now retired - into this larger, resizable preview window for better click precision) ---

	@Test
	void markingModeSuppressesEveryOverlayAndShowsTheRawPhoto(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeOpaqueImage(new File(cameraRoot, "20260809_120000_1.jpg"), 80, 60, Color.WHITE);

		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(45.0, -75.0);
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(t0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();

		// realCamera(...)'s OWN default calibration entry uses zoom=1.0mm (an extreme, near-minimum-
		// bound wide-angle lens fine for the dimension-only tests elsewhere in this file, but not for
		// this test's actual sun-centering math) - appending this entry with a normal 50.0mm zoom
		// instead, matching the projection's own initial focal length, keeps the FOV reasonable.
		CameraConfig camera = realCamera(cameraRoot); // aimed dead center at the sun
		camera.getCalibrationHistory().append(new CalibrationEntry(1L,
				new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0), 50.0, 45.0, -75.0));

		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.setTime(t0 + 5_000L);
		clock.pause();

		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);

		BufferedImage normal = controller.renderCurrentFrame();
		int centerX = normal.getWidth() / 2;
		int centerY = normal.getHeight() / 2;
		int sunColor = ColorPresets.defaultScheme().getSunColor().getRGB() & 0x00FFFFFF;
		assertEquals(sunColor, normal.getRGB(centerX, centerY) & 0x00FFFFFF,
				"test setup sanity check: the sun renders dead center without marking mode");

		controller.setMarkingModeActive(true);
		BufferedImage marking = controller.renderCurrentFrame();
		assertEquals(Color.WHITE.getRGB(), marking.getRGB(centerX, centerY),
				"marking mode must show the raw photo with no overlay drawn on top - a technique-2 user is "
						+ "meant to click a REAL object exactly as it appears, not a computed prediction");
	}

	@Test
	void markingModeForcesTheImageVisibleEvenWhenTheShowImageToggleIsOff(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeOpaqueImage(new File(cameraRoot, "20260809_120000_1.jpg"), 80, 60, Color.RED);

		CameraConfig camera = realCamera(cameraRoot);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.setTime(t0 + 5_000L);
		clock.pause();

		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);
		controller.setImageShown(false);
		controller.setMarkingModeActive(true);

		BufferedImage frame = controller.renderCurrentFrame();

		assertEquals(80, frame.getWidth(), "marking mode must render the real archived frame's own resolution - "
				+ "not the square no-image-shown fallback - regardless of the ordinary imageShown toggle");
	}

	// --- CameraAstronomy-backed constructor (Item 0's shared-instance architecture) ---

	@Test
	void aCameraAstronomyBackedControllerRendersIdenticallyToTheOriginalConstructor(@TempDir File tempDir)
			throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeOpaqueImage(new File(cameraRoot, "20260809_120000_1.jpg"), 80, 60, Color.RED);

		CameraConfig camera = realCamera(cameraRoot);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.setTime(t0 + 5_000L);
		clock.pause();

		PreviewController original = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);
		BufferedImage withoutAstronomy = original.renderCurrentFrame();

		me.qbert.skywatch.camera.astro.CameraAstronomy astronomy = new me.qbert.skywatch.camera.astro.CameraAstronomy(
				java.util.TimeZone.getTimeZone("UTC"), Collections.<StarCoordinate>emptyList());
		PreviewController viaAstronomy = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE, me.qbert.skywatch.camera.source.ArchiveFrameCache.DEFAULT_REFRESH_INTERVAL_MILLIS, null,
				astronomy);
		BufferedImage withAstronomy = viaAstronomy.renderCurrentFrame();

		assertEquals(withoutAstronomy.getWidth(), withAstronomy.getWidth());
		assertEquals(withoutAstronomy.getHeight(), withAstronomy.getHeight());
		for (int y = 0; y < withoutAstronomy.getHeight(); y++)
			for (int x = 0; x < withoutAstronomy.getWidth(); x++)
				assertEquals(withoutAstronomy.getRGB(x, y), withAstronomy.getRGB(x, y),
						"pixel (" + x + "," + y + ") must match between the astronomy-backed and original renders");

		assertEquals(astronomy, viaAstronomy.getAstronomy());
	}

	// Compares against an independently-built, non-astronomy-backed reference render for each frame
	// (rather than a hardcoded exact color) so this test stays robust regardless of whether
	// render.Layer1DuskFade happens to apply to this call path - unrelated to whether CameraAstronomy
	// re-renders correctly, which is all this test actually cares about.
	@Test
	void aCameraAstronomyBackedControllerCorrectlyReRendersAfterTheClockAdvances(@TempDir File tempDir)
			throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeOpaqueImage(new File(cameraRoot, "20260809_120000_1.jpg"), 80, 60, Color.RED);
		writeOpaqueImage(new File(cameraRoot, "20260809_130000_1.jpg"), 80, 60, Color.BLUE);

		CameraConfig camera = realCamera(cameraRoot);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		SimulatedClock clock = new SimulatedClock();
		clock.setTime(t0 + 5_000L);
		clock.pause();

		me.qbert.skywatch.camera.astro.CameraAstronomy astronomy = new me.qbert.skywatch.camera.astro.CameraAstronomy(
				java.util.TimeZone.getTimeZone("UTC"), Collections.<StarCoordinate>emptyList());
		PreviewController controller = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE, me.qbert.skywatch.camera.source.ArchiveFrameCache.DEFAULT_REFRESH_INTERVAL_MILLIS, null,
				astronomy);
		PreviewController reference = new PreviewController(camera, cache, defaultOptions(), clock, CANVAS_SIZE,
				CANVAS_SIZE);

		BufferedImage first = controller.renderCurrentFrame();
		BufferedImage firstReference = reference.renderCurrentFrame();
		assertEquals(firstReference.getRGB(0, 0), first.getRGB(0, 0), "must snap to the 12:00:00 frame first");

		clock.setTime(t0 + 3_600_000L + 5_000L);
		BufferedImage second = controller.renderCurrentFrame();
		BufferedImage secondReference = reference.renderCurrentFrame();
		assertEquals(secondReference.getRGB(0, 0), second.getRGB(0, 0),
				"reusing the SAME CameraAstronomy instance must still re-render correctly after the clock advances, "
						+ "not get stuck on the first render");
		assertTrue(first.getRGB(0, 0) != second.getRGB(0, 0), "sanity check: the two frames must actually differ");
	}

	// --- renderFastPtzPreview(...) - new this round, see its own class comment for the performance
	// problem it exists to fix ---

	@Test
	void renderFastPtzPreviewFallsBackToAFullRenderWhenNoPreviousFrameExists(@TempDir File tempDir) throws Exception {
		CameraConfig camera = virtualPtzCamera(new File(tempDir, "panorama.png"), 45.0);
		PreviewController controller = new PreviewController(camera, null, defaultOptions(), new SimulatedClock(),
				CANVAS_SIZE, CANVAS_SIZE);

		BufferedImage preview = controller.renderFastPtzPreview(64);

		assertEquals(CANVAS_SIZE, preview.getWidth(), "with no prior frame, this is just a normal full render");
		assertEquals(CANVAS_SIZE, preview.getHeight());
	}

	@Test
	void renderFastPtzPreviewCompositesALiveInsetOverAStaleBackgroundFromTheLastFullFrame(@TempDir File tempDir)
			throws Exception {
		// azimuth=45 (blue half, center-relative) for the full frame; azimuth=225 (red half) for the
		// live inset - two genuinely different colors, so the test can tell "still showing the old
		// full frame" apart from "reflects the new, live orientation" by color alone. A fixed,
		// confirmed-daytime clock (not the default real-wall-clock SimulatedClock()) - render.
		// Layer1DuskFade now darkens Layer 1 based on the sun's real altitude, so this test's exact
		// full-saturation color assertions would otherwise be wall-clock-dependent (flaky) depending
		// on when the suite actually runs, the same class of problem as this module's already-tracked
		// wall-clock-dependent flakes.
		CameraConfig camera = virtualPtzCamera(new File(tempDir, "panorama.png"), 45.0);
		PreviewController controller = new PreviewController(camera, null, defaultOptions(),
				new SimulatedClock(() -> DAYTIME_EPOCH_MILLIS), CANVAS_SIZE, CANVAS_SIZE);
		BufferedImage fullFrame = controller.renderCurrentFrame(); // establishes lastFullFrame, facing azimuth=45
		assertEquals(Color.BLUE.getRGB(), fullFrame.getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2) | 0xFF000000,
				"test setup sanity check: facing azimuth=45 should show blue dead center");

		camera.setCurrentOrientation(new Orientation(0.0, 225.0, 0.0)); // pan to the red half, without a new full render
		int maxDimensionPixels = 64;
		BufferedImage preview = controller.renderFastPtzPreview(maxDimensionPixels);

		assertEquals(CANVAS_SIZE, preview.getWidth(), "the composite is the full frame's own size, not the small inset's");
		assertEquals(CANVAS_SIZE, preview.getHeight());

		// Top-left corner sits nowhere near the bottom-right inset - must still show the STALE
		// azimuth=45 (blue) background, proving it's a copy of the old full frame, not freshly
		// re-rendered at the new orientation.
		assertEquals(Color.BLUE.getRGB(), preview.getRGB(0, 0) | 0xFF000000,
				"background away from the inset must be untouched - still the old, stale full frame");

		// The inset's own center must reflect the NEW, live azimuth=225 (red) - computed the same way
		// renderFastPtzPreview(...) itself does, so this test doesn't hardcode a brittle pixel offset.
		int insetMargin = 8;
		int insetX = CANVAS_SIZE - maxDimensionPixels - insetMargin;
		int insetY = CANVAS_SIZE - maxDimensionPixels - insetMargin;
		assertEquals(Color.RED.getRGB(), preview.getRGB(insetX + maxDimensionPixels / 2, insetY + maxDimensionPixels / 2)
				| 0xFF000000, "the small inset must reflect the CURRENT, live orientation, not the stale background");
	}

	@Test
	void renderFastPtzPreviewRejectsNonPositiveMaxDimension(@TempDir File tempDir) throws Exception {
		CameraConfig camera = virtualPtzCamera(new File(tempDir, "panorama.png"), 45.0);
		PreviewController controller = new PreviewController(camera, null, defaultOptions(), new SimulatedClock(),
				CANVAS_SIZE, CANVAS_SIZE);

		assertThrows(IllegalArgumentException.class, () -> controller.renderFastPtzPreview(0));
	}
}
