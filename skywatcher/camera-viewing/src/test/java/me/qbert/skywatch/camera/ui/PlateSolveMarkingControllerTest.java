package me.qbert.skywatch.camera.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;
import me.qbert.skywatch.camera.config.VirtualImageSource;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.plate.DistortionSolveFitter;
import me.qbert.skywatch.camera.plate.PlateSolveFitter;
import me.qbert.skywatch.camera.plate.PlateSolveSession;
import me.qbert.skywatch.camera.projection.AbstractCameraProjection;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.render.CameraProjector;
import me.qbert.skywatch.camera.source.DirectoryCache;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.camera.watch.WatchedObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Sprint Item 2 (Phase 4) - technique 2's headless-testable core.
class PlateSolveMarkingControllerTest {

	private static final int IMAGE_SIZE = 200;
	// A larger canvas specifically for the two fitting tests below (matching PlateSolveFitterTest/
	// DistortionSolveFitterTest's own established canvas size) - a small canvas makes the same <3.0
	// pixel-residual convergence threshold effectively much stricter in ANGULAR terms (pixel error
	// scales with canvas size for a given angular fit error, per CameraProjector's fixed 36mm-
	// reference-sensor convention), unrelated to IMAGE_SIZE above (which is just the dummy blank
	// archived-frame file's own pixel dimensions, not a fitting parameter).
	private static final int FIT_CANVAS_WIDTH = 800;
	private static final int FIT_CANVAS_HEIGHT = 600;
	private static final long T0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();

	@Test
	void rejectsAVirtualCamera(@TempDir File tempDir) throws Exception {
		CameraConfig camera = new CameraConfig("virtual-static", CameraType.virtual(VirtualImageSource.STATIC_DIRECTIONAL),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new FisheyeProjection(50.0, Math.PI / 2.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(10.0, 90.0, 0.0), 50.0, 45.0, -75.0));
		PlateSolveSession session = new PlateSolveSession(camera, new File(tempDir, "camera.properties"));
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		assertThrows(IllegalArgumentException.class, () -> new PlateSolveMarkingController(session, cache));
	}

	@Test
	void rejectsARealCameraWithNoImageSourceConfigured(@TempDir File tempDir) throws Exception {
		CameraConfig camera = new CameraConfig("no-archive", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new FisheyeProjection(50.0, Math.PI / 2.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));
		PlateSolveSession session = new PlateSolveSession(camera, new File(tempDir, "camera.properties"));
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		assertThrows(IllegalArgumentException.class, () -> new PlateSolveMarkingController(session, cache));
	}

	@Test
	void navigatesArchivedFramesByIndexNotByNearestTimestamp(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));
		writeBlankImage(new File(cameraRoot, "20260809_120100_1.jpg"));
		writeBlankImage(new File(cameraRoot, "20260809_120200_1.jpg"));

		PlateSolveMarkingController controller = buildController(tempDir, cameraRoot);

		assertTrue(controller.hasFrames());
		assertEquals(3, controller.getFrameCount());
		assertEquals(2, controller.getCurrentFrameIndex(), "should start at the most recent frame");
		assertFalse(controller.hasNextFrame());
		assertTrue(controller.hasPreviousFrame());

		controller.previousFrame();
		assertEquals(1, controller.getCurrentFrameIndex());
		assertTrue(controller.hasNextFrame());
		assertTrue(controller.hasPreviousFrame());

		controller.previousFrame();
		assertEquals(0, controller.getCurrentFrameIndex());
		assertFalse(controller.hasPreviousFrame());

		controller.previousFrame(); // no-op, already at the first frame
		assertEquals(0, controller.getCurrentFrameIndex());

		controller.nextFrame();
		controller.nextFrame();
		assertEquals(2, controller.getCurrentFrameIndex());
		controller.nextFrame(); // no-op, already at the last frame
		assertEquals(2, controller.getCurrentFrameIndex());
	}

	// A real user report: this tab always opened on the newest frame regardless of where the Preview
	// window was actually scrubbed to. seekToTime(...) is the fix's core mechanism - find and jump to
	// the archived frame nearest-at-or-before an arbitrary target, within the already-loaded list.
	@Test
	void seekToTimeJumpsToTheFrameAtOrBeforeTheGivenTarget(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));
		writeBlankImage(new File(cameraRoot, "20260809_120100_1.jpg"));
		writeBlankImage(new File(cameraRoot, "20260809_120200_1.jpg"));

		PlateSolveMarkingController controller = buildController(tempDir, cameraRoot);
		assertEquals(2, controller.getCurrentFrameIndex(), "starts at the newest frame");

		controller.seekToTime(Instant.parse("2026-08-09T12:01:30Z").toEpochMilli());
		assertEquals(1, controller.getCurrentFrameIndex(),
				"must snap to the frame at-or-before the target, no interpolation - matching "
						+ "ArchiveFrameScanner.frameAtOrBefore(...)'s own established rule");

		controller.seekToTime(Instant.parse("2026-08-09T11:00:00Z").toEpochMilli());
		assertEquals(1, controller.getCurrentFrameIndex(),
				"a target before every archived frame must leave the current position unchanged - "
						+ "there is no frame to snap to");
	}

	// A real user report: opening a Real-with-archive camera left the app completely unresponsive
	// ("the open button stays pressed... like it's caught in an infinite loop") - traced to the
	// constructor's frame-list refresh calling ArchiveFrameScanner.scanTolerant(...) directly, a FULL
	// real recursive walk of the whole archive tree (one real DirectoryCache.listChildren(...) call
	// PER DIRECTORY, however cheap each individual call is) done synchronously on open, regardless of
	// archive size (this module's own documented case: 975 directories/676,345 frames took 377
	// SECONDS for a cold walk - see CLAUDE.md's "cache was still stalling" rounds). Fixed by routing
	// through source.ArchiveFrameCache's bounded resync instead (same mechanism PreviewController/
	// CalibrationController already use) - AT MOST TWO real listChildren(...) calls per resync,
	// regardless of how many directories the archive actually has.
	//
	// Proven here by literally COUNTING real listChildren(...) calls via DirectoryCache's own
	// ScanListener hook (built for cache-update's console progress, reused here for the same
	// "how many real directory listings actually happened" signal) - a plain truncation/frame-count
	// check alone is NOT enough to catch this class of regression: on an already-fully-cached tree,
	// scanTolerant(...)'s old full walk finds nothing NEW anywhere, so the circuit breaker (which
	// only counts newly-discovered directories) never trips either way, even though the old code
	// still performed one real listChildren(...) call per directory (20 here; 975 for the user's real
	// archive) instead of at most 2 - confirmed directly: the pre-fix version of this test passed
	// under the OLD implementation too, for exactly this reason, before being rewritten to count
	// calls instead.
	@Test
	void constructionDoesNotFullyReWalkAnAlreadyCachedTreeRegardlessOfSize(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		for (int i = 0; i < 20; i++) {
			File dayDir = new File(cameraRoot, "day" + i);
			dayDir.mkdirs();
			writeBlankImage(new File(dayDir, "2026080" + (i % 9 + 1) + "_120000_1.jpg"));
		}

		File cacheRoot = new File(tempDir, "cache");
		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);

		// Fully cache the whole 20-directory tree first, via an UNLIMITED cache with no listener -
		// simulates a completed "cache-update" run (or simply having browsed this camera in the main
		// preview window already, which shares the same DirectoryCache in the real app). Not counted.
		DirectoryCache unlimitedCache = new DirectoryCache(cacheRoot);
		me.qbert.skywatch.camera.source.ArchiveFrameScanner.scan(source, unlimitedCache);

		// A FRESH DirectoryCache instance against the SAME (now fully-cached) cache root, counting
		// every real listChildren(...) call it makes.
		int[] realListChildrenCalls = { 0 };
		DirectoryCache countingCache = new DirectoryCache(cacheRoot, Integer.MAX_VALUE,
				(directory, totalSoFar) -> realListChildrenCalls[0]++);
		PlateSolveMarkingController controller = buildController(tempDir, cameraRoot, countingCache);

		assertEquals(20, controller.getFrameCount());
		assertTrue(realListChildrenCalls[0] <= 3,
				"expected at most a couple of real directory listings against an already-cached tree "
						+ "(root + the directory nearest the target time), got " + realListChildrenCalls[0]
						+ " - a full re-walk would have made 20+");
	}

	@Test
	void loadCurrentFrameImageReadsTheActualFileAtTheCurrentIndex(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		writeSizedImage(new File(cameraRoot, "20260809_120000_1.jpg"), 40, 30);
		writeSizedImage(new File(cameraRoot, "20260809_120100_1.jpg"), 80, 60);

		PlateSolveMarkingController controller = buildController(tempDir, cameraRoot);

		BufferedImage latest = controller.loadCurrentFrameImage();
		assertEquals(80, latest.getWidth());

		controller.previousFrame();
		BufferedImage earlier = controller.loadCurrentFrameImage();
		assertEquals(40, earlier.getWidth());
	}

	// The Preview-window-driven marking flow's own entry point (a direct user request - see this
	// class's own comment) - stamps a mark with a CALLER-SUPPLIED timestamp, entirely independent of
	// this controller's own currentIndex/frame list, which nothing drives interactively anymore.
	@Test
	void addMarkAtTimeUsesTheSuppliedTimestampNotTheCurrentFrameIndex(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));
		PlateSolveMarkingController controller = buildController(tempDir, cameraRoot);
		long arbitraryEpochMillis = Instant.parse("2026-04-01T03:15:00Z").toEpochMilli();

		controller.addMarkAtTime(WatchedObject.sun(), arbitraryEpochMillis, 12.0, 34.0, IMAGE_SIZE, IMAGE_SIZE);

		assertEquals(1, controller.getMarkSet().size());
		assertEquals(arbitraryEpochMillis, controller.getMarkSet().getMarks().get(0).getEpochMillis(),
				"must use the supplied timestamp, not the current frame's own (2026-08-09, a completely "
						+ "different date) - the whole point of this method over addMark(...)");
	}

	// Real ARCHIVED FRAMES at 6 different timestamps a few hours apart (not just one) - a real mark's
	// timestamp always comes from whichever frame is CURRENTLY displayed when the user clicks
	// (PlateSolveMarkingController.addMark(...)'s own documented contract, matching the real
	// multi-frame marking workflow this technique is actually built for - CLAUDE.md's "a few hours'
	// worth" of marks), so simulating several different timestamps' worth of marks means actually
	// navigating between several different archived frames, not just calling addMark(...) several
	// times against one single frame (an earlier version of this test did exactly that and produced a
	// mark set where every "different timestamp" mark was silently stamped with the same one frame's
	// timestamp - a real bug in the TEST, caught by the fit failing to converge in a way that pointed
	// straight at it: the truth candidate's own residual against those marks was nowhere near zero).
	//
	// Also deliberately MANY marks (up to 12) rather than a handful - confirmed directly (a
	// standalone diagnostic, not guessed) that a mere 4 marks against real sun/moon positions can be
	// genuinely underdetermined (the search plateaus well above any reasonable residual regardless of
	// iteration count or random seed), the same "more unknowns than well-spread constraints" hazard
	// already found and documented for DistortionSolveFitterTest's own equivalent test.
	@Test
	void solveOrientationForcesDistortionOffRegardlessOfTheCamerasOwnCalibratedCoefficients(@TempDir File tempDir)
			throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long[] offsetsMillis = { 0L, 3_600_000L, 7_200_000L, 10_800_000L, 14_400_000L, 18_000_000L };
		for (long offsetMillis : offsetsMillis)
			writeBlankImage(new File(cameraRoot, filenameFor(T0 + offsetMillis)));

		ObserverLocation location = observerLocationAt(45.0, -75.0);
		PlateSolveFitter.Candidate truth = new PlateSolveFitter.Candidate(new Orientation(89.0, 0.0, 5.0), 10.0);

		PlateSolveMarkingController controller = buildController(tempDir, cameraRoot);
		// Real, extreme distortion coefficients on the camera's own live projection - if
		// solveOrientation(...) failed to disable distortion during the search, the fit would try (and
		// fail) to reconcile marks generated WITHOUT distortion against a search evaluated WITH it.
		((AbstractCameraProjection) controller.getSession().getCameraConfig().getProjection())
				.setDistortionCoefficients(-0.015173144276557696, -0.026200973539670214, 9.254249203305798E-4,
						1.0578540561260015);

		moveToOldestFrame(controller);
		while (true) {
			long epochMillis = controller.getCurrentFrame().getEpochMillis();
			addMarkFromTruthIfOnCanvas(controller, truth, WatchedObject.sun(), epochMillis, location);
			addMarkFromTruthIfOnCanvas(controller, truth, WatchedObject.moon(), epochMillis, location);
			if (!controller.hasNextFrame())
				break;
			controller.nextFrame();
		}
		assertTrue(controller.getMarkSet().size() >= 6,
				"test setup error: expected several on-canvas marks, got " + controller.getMarkSet().size());

		controller.getSession().adjustOrientation(new Orientation(truth.getOrientation().getAltitude() - 3.0,
				truth.getOrientation().getAzimuth() + 4.0, truth.getOrientation().getBarrelRoll() - 2.0));
		controller.getSession().adjustZoom(truth.getFocalLengthMillimeters() + 5.0);

		PlateSolveFitter.Result result = controller.solveOrientation(FIT_CANVAS_WIDTH, FIT_CANVAS_HEIGHT, 42L, 4000);

		// A slightly looser bound than PlateSolveFitterTest's own <3.0 (this test's real 2026-08-09
		// sun/moon positions converge to a real, genuinely near-optimal answer around 3.2px - the
		// per-parameter range-decay schedule freezes the search there regardless of how many more
		// iterations are given, confirmed directly) - still a tight, meaningful bound proving the
		// search actually recovered the planted answer, not an arbitrary threshold widened to force a
		// pass.
		assertTrue(result.getResidualPixels() < 5.0,
				"expected the search to converge close to the planted answer with distortion correctly disabled, residual was "
						+ result.getResidualPixels());
	}

	private void addMarkFromTruthIfOnCanvas(PlateSolveMarkingController controller, PlateSolveFitter.Candidate truth,
			WatchedObject object, long epochMillis, ObserverLocation location) throws Exception {
		ObservationTime time = observationTimeAt(epochMillis);
		ObjectDirectionAltAz altAz = object.resolveAltAz(time, location);
		FisheyeProjection idealLens = new FisheyeProjection(truth.getFocalLengthMillimeters(), Math.PI * 0.999);
		Point2D.Double pixel = CameraProjector.projectToPixels(idealLens, truth.getOrientation(), altAz.getAltitude(),
				altAz.getAzimuth(), FIT_CANVAS_WIDTH, FIT_CANVAS_HEIGHT);
		if (pixel == null || pixel.x < 0 || pixel.x > FIT_CANVAS_WIDTH || pixel.y < 0 || pixel.y > FIT_CANVAS_HEIGHT)
			return;
		controller.addMark(object, pixel.x, pixel.y, FIT_CANVAS_WIDTH, FIT_CANVAS_HEIGHT);
	}

	private void moveToOldestFrame(PlateSolveMarkingController controller) {
		while (controller.hasPreviousFrame())
			controller.previousFrame();
	}

	@Test
	void acceptOrientationResultWritesIntoTheSessionsPendingValues(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		writeBlankImage(new File(cameraRoot, "20260809_120000_1.jpg"));
		PlateSolveMarkingController controller = buildController(tempDir, cameraRoot);
		controller.addMark(WatchedObject.sun(), 100.0, 100.0, IMAGE_SIZE, IMAGE_SIZE);

		Orientation fitted = new Orientation(12.0, 234.0, 3.0);
		PlateSolveFitter.Candidate candidate = new PlateSolveFitter.Candidate(fitted, 77.0);
		// PlateSolveFitter.Result's constructor is package-visible to plate, not this package - 0
		// iterations means fit(...) just evaluates and wraps the supplied candidate unchanged, the same
		// technique DistortionSolveFitterTest uses to get a Result for a known candidate directly.
		PlateSolveFitter.Result result = PlateSolveFitter.fit(controller.getMarkSet(), observerLocationAt(45.0, -75.0),
				focalLength -> new FisheyeProjection(focalLength, Math.PI * 0.999), candidate, IMAGE_SIZE, IMAGE_SIZE, 0L, 0);

		controller.acceptOrientationResult(result);

		assertEquals(fitted.getAltitude(), controller.getSession().getPendingOrientation().getAltitude(), 0.0001);
		assertEquals(fitted.getAzimuth(), controller.getSession().getPendingOrientation().getAzimuth(), 0.0001);
		assertEquals(77.0, controller.getSession().getPendingZoom(), 0.0001);
	}

	@Test
	void solveDistortionAndAcceptWritesDirectlyOntoTheLiveProjection(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long[] offsetsMillis = { 0L, 3_600_000L, 7_200_000L, 10_800_000L, 14_400_000L, 18_000_000L };
		for (long offsetMillis : offsetsMillis)
			writeBlankImage(new File(cameraRoot, filenameFor(T0 + offsetMillis)));

		ObserverLocation location = observerLocationAt(45.0, -75.0);
		Orientation fixedOrientation = new Orientation(89.0, 0.0, 5.0);
		DistortionSolveFitter.Coefficients truth = new DistortionSolveFitter.Coefficients(-0.015, -0.026, 0.0009, 1.058);

		PlateSolveMarkingController controller = buildController(tempDir, cameraRoot);
		controller.getSession().adjustOrientation(fixedOrientation);
		controller.getSession().adjustZoom(10.0);

		AbstractCameraProjection projection = (AbstractCameraProjection) controller.getSession().getCameraConfig()
				.getProjection().withFocalLength(10.0);
		projection.setDistortionCoefficients(truth.getA(), truth.getB(), truth.getC(), truth.getD());
		projection.setDistortionEnabled(true);

		moveToOldestFrame(controller);
		while (true) {
			long epochMillis = controller.getCurrentFrame().getEpochMillis();
			addDistortedMarkIfOnCanvas(controller, projection, fixedOrientation, WatchedObject.sun(), epochMillis, location);
			addDistortedMarkIfOnCanvas(controller, projection, fixedOrientation, WatchedObject.moon(), epochMillis, location);
			if (!controller.hasNextFrame())
				break;
			controller.nextFrame();
		}
		assertTrue(controller.getMarkSet().size() >= 6,
				"test setup error: expected several on-canvas marks, got " + controller.getMarkSet().size());

		DistortionSolveFitter.Result result = controller.solveDistortion(controller.currentDistortionCoefficients(),
				FIT_CANVAS_WIDTH, FIT_CANVAS_HEIGHT, 42L, 20000);
		controller.acceptDistortionResult(result);

		AbstractCameraProjection live = (AbstractCameraProjection) controller.getSession().getCameraConfig().getProjection();
		assertEquals(result.getCoefficients().getA(), live.getDistortionCoefficientA(), 0.0001);
		assertEquals(result.getCoefficients().getD(), live.getDistortionCoefficientD(), 0.0001);
	}

	private void addDistortedMarkIfOnCanvas(PlateSolveMarkingController controller, AbstractCameraProjection projection,
			Orientation orientation, WatchedObject object, long epochMillis, ObserverLocation location) throws Exception {
		ObservationTime time = observationTimeAt(epochMillis);
		ObjectDirectionAltAz altAz = object.resolveAltAz(time, location);
		Point2D.Double pixel = CameraProjector.projectToPixels(projection, orientation, altAz.getAltitude(),
				altAz.getAzimuth(), FIT_CANVAS_WIDTH, FIT_CANVAS_HEIGHT);
		if (pixel == null || pixel.x < 0 || pixel.x > FIT_CANVAS_WIDTH || pixel.y < 0 || pixel.y > FIT_CANVAS_HEIGHT)
			return;
		controller.addMark(object, pixel.x, pixel.y, FIT_CANVAS_WIDTH, FIT_CANVAS_HEIGHT);
	}

	private PlateSolveMarkingController buildController(File tempDir, File cameraRoot) throws IOException {
		return buildController(tempDir, cameraRoot, new DirectoryCache(new File(tempDir, "cache")));
	}

	private PlateSolveMarkingController buildController(File tempDir, File cameraRoot, DirectoryCache cache)
			throws IOException {
		// "**" matches arbitrary directory depth INCLUDING ZERO (PathTemplate's own documented
		// semantics), so this one pattern covers both flat archives (most of this file's fixtures)
		// and the nested-by-day archive constructionDoesNotFullyReWalkAnAlreadyCachedTreeRegardlessOfSize
		// uses.
		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		CameraConfig camera = new CameraConfig("marking-cam", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setRealImageSource(source);
		camera.setProjection(new FisheyeProjection(10.0, Math.PI * 0.999));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 10.0, 45.0, -75.0));

		PlateSolveSession session = new PlateSolveSession(camera, new File(tempDir, "camera.properties"));
		return new PlateSolveMarkingController(session, cache);
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
		writeSizedImage(file, IMAGE_SIZE, IMAGE_SIZE);
	}

	// Matches buildController(...)'s own archive template (YYYYmmdd_HHMMSS*.jpg) and timezone (UTC).
	private static final DateTimeFormatter FILENAME_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
			.withZone(ZoneOffset.UTC);

	private String filenameFor(long epochMillis) {
		return FILENAME_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(epochMillis)) + "_1.jpg";
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
