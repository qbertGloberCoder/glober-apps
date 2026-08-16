package me.qbert.skywatch.camera.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

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
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;
import me.qbert.skywatch.camera.config.VirtualImageSource;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.render.ImagePlacement;
import me.qbert.skywatch.camera.source.ArchiveFrameScanner;
import me.qbert.skywatch.camera.source.DirectoryCache;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

class BatchReprocessorTest {

	private static final int IMAGE_SIZE = 200;
	private static final long FRAME_EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

	@Test
	void reprocessesARealFrameAndBurnsTheSunOntoIt(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		File frameFile = new File(cameraRoot, "20240809_000000_1.jpg");
		writeBlankImage(frameFile, IMAGE_SIZE, IMAGE_SIZE);

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		List<ArchiveFrameScanner.Frame> frames = ArchiveFrameScanner.scan(source, cache);
		assertEquals(1, frames.size());
		ArchiveFrameScanner.Frame frame = frames.get(0);

		// Point the camera exactly at the sun's own computed position for this frame's time/
		// location, so it must render dead center - matches CelestialObjectsLayerTest's approach.
		ObservationTime time = observationTimeAt(FRAME_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();

		CameraConfig cameraConfig = new CameraConfig("backyard-east", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		cameraConfig.setProjection(new RectilinearProjection(50.0));
		cameraConfig.getCalibrationHistory().append(new CalibrationEntry(0L,
				new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0), 1.0, 45.0, -75.0));

		BufferedImage result = BatchReprocessor.reprocessFrame(frame, cameraConfig, defaultOptions());

		assertEquals(IMAGE_SIZE, result.getWidth());
		assertEquals(IMAGE_SIZE, result.getHeight());
		assertEquals(rgbNoAlpha(ColorPresets.defaultScheme().getSunColor()),
				rgbNoAlpha(result, IMAGE_SIZE / 2, IMAGE_SIZE / 2),
				"the sun should be burned onto the real photo at its own computed position");
	}

	@Test
	void theCalibrationEntrysZoomActuallyChangesTheRenderedFieldOfView(@TempDir File tempDir) throws Exception {
		// The batch/persisted-rendering counterpart to PlateSolveSessionTest's identically-named
		// test - a real user report that zoom had no effect anywhere in the pipeline. This class
		// resolves zoom from the CalibrationEntry actually in effect for the frame
		// (resolveCalibration(...)), a different code path from PlateSolveSession's live pendingZoom.
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		File frameFile = new File(cameraRoot, "20240809_000000_1.jpg");
		writeBlankImage(frameFile, IMAGE_SIZE, IMAGE_SIZE);

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		ArchiveFrameScanner.Frame frame = ArchiveFrameScanner.scan(source, cache).get(0);

		ObservationTime time = observationTimeAt(FRAME_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();

		CameraConfig wideCamera = new CameraConfig("backyard-east", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		wideCamera.setProjection(new RectilinearProjection(50.0)); // the camera's own fixed lens definition
		wideCamera.getCalibrationHistory().append(new CalibrationEntry(0L,
				new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth() + 5.0, 0.0), 20.0, 45.0, -75.0));

		CameraConfig narrowCamera = new CameraConfig("backyard-east", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		narrowCamera.setProjection(new RectilinearProjection(50.0));
		narrowCamera.getCalibrationHistory().append(new CalibrationEntry(0L,
				new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth() + 5.0, 0.0), 200.0, 45.0, -75.0));

		BufferedImage wide = BatchReprocessor.reprocessFrame(frame, wideCamera, defaultOptions());
		BufferedImage narrow = BatchReprocessor.reprocessFrame(frame, narrowCamera, defaultOptions());

		int wideDistance = distanceFromCenterToSunPixel(wide);
		int narrowDistance = distanceFromCenterToSunPixel(narrow);
		assertTrue(narrowDistance > wideDistance * 2,
				"the calibration entry's own zoom (20mm vs 200mm) must actually change the rendered FOV "
						+ "(wide=" + wideDistance + "px, narrow=" + narrowDistance + "px), not just the camera's fixed lens");
	}

	// This class always shows the real photo in Layer 1 (there is no hide toggle in reprocess/save-
	// latest, see its own class comment) - distortion should always apply when real coefficients are
	// configured, unconditionally, unlike RealCameraScrubber's imageShown-gated case.
	@Test
	void distortionCoefficientsAlwaysApplyBecauseThisClassAlwaysShowsTheRealImage(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		File frameFile = new File(cameraRoot, "20240809_000000_1.jpg");
		writeBlankImage(frameFile, IMAGE_SIZE, IMAGE_SIZE);

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		ArchiveFrameScanner.Frame frame = ArchiveFrameScanner.scan(source, cache).get(0);

		ObservationTime time = observationTimeAt(FRAME_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();

		CameraConfig cameraConfig = new CameraConfig("backyard-east", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		RectilinearProjection projection = new RectilinearProjection(50.0);
		cameraConfig.setProjection(projection);
		cameraConfig.getCalibrationHistory().append(new CalibrationEntry(0L,
				new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth() + 5.0, 0.0), 50.0, 45.0, -75.0));

		BufferedImage ideal = BatchReprocessor.reprocessFrame(frame, cameraConfig, defaultOptions());
		int idealDistance = distanceFromCenterToSunPixel(ideal);

		projection.setDistortionCoefficients(-0.015173144276557696, -0.026200973539670214, 9.254249203305798E-4,
				1.0578540561260015);
		BufferedImage distorted = BatchReprocessor.reprocessFrame(frame, cameraConfig, defaultOptions());
		int distortedDistance = distanceFromCenterToSunPixel(distorted);

		assertTrue(Math.abs(distortedDistance - idealDistance) >= 1,
				"real barrel-distortion coefficients must actually move the sun's rendered position here "
						+ "(ideal=" + idealDistance + "px, distorted=" + distortedDistance + "px)");
	}

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
	void reprocessedFrameIsOpaqueAndWritableAsJpeg(@TempDir File tempDir) throws Exception {
		// Regression guard: FrameCompositor.compose(...) (which reprocessFrame(...) now routes
		// through, since the round that swapped this class's compositing core) always returns a
		// TYPE_INT_ARGB canvas - some placements/toggles genuinely need alpha. A Real camera's
		// output never does (the Layer-1 photo is always opaque, sky/ground stay auto-disabled for
		// it), and Java's JPEG ImageWriter silently REFUSES to write an ARGB image at all (ImageIO.
		// write(...) returns false, without throwing) - real archives are typically JPEG (see this
		// class's own worked examples), so this bit for real the first time this class was routed
		// through FrameCompositor, caught by ReprocessCommandTest's end-to-end JPEG write failing
		// silently. compositeOverlay(...) converts back to an opaque TYPE_INT_RGB image specifically
		// to prevent this.
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		File frameFile = new File(cameraRoot, "20240809_000000_1.jpg");
		writeBlankImage(frameFile, IMAGE_SIZE, IMAGE_SIZE);

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		ArchiveFrameScanner.Frame frame = ArchiveFrameScanner.scan(source, cache).get(0);

		CameraConfig cameraConfig = new CameraConfig("backyard-east", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		cameraConfig.setProjection(new RectilinearProjection(50.0));
		cameraConfig.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 1.0, 45.0, -75.0));

		BufferedImage result = BatchReprocessor.reprocessFrame(frame, cameraConfig, defaultOptions());

		assertEquals(BufferedImage.TYPE_INT_RGB, result.getType(), "expected an opaque RGB result, not ARGB");
		File jpegOutput = new File(tempDir, "out.jpg");
		assertTrue(ImageIO.write(result, "jpg", jpegOutput), "expected the JPEG writer to accept the reprocessed frame");
	}

	@Test
	void oldFramesRenderAgainstTheCalibrationThatWasCorrectForThemUnaffectedByALaterAppend(@TempDir File tempDir)
			throws Exception {
		// Task 7.3 [spec §7.2]: writing a new time-versioned calibration entry is always an append,
		// never an edit - CalibrationHistoryTest already covers this at the CalibrationHistory
		// lookup level directly; this is the same guarantee proven end to end through the actual
		// render pipeline (BatchReprocessor.reprocessFrame -> FrameCompositor.compose), which no
		// existing test here exercised (every other test in this class only ever appends one entry).
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long oldEpochMillis = FRAME_EPOCH_MILLIS;
		long newEpochMillis = FRAME_EPOCH_MILLIS + 12L * 60L * 60L * 1000L; // 12 hours later - a
		// genuinely different real sun position, not a repeating same-time-of-day coincidence.
		File oldFrameFile = new File(cameraRoot, "20240809_000000_1.jpg");
		File newFrameFile = new File(cameraRoot, "20240809_120000_1.jpg");
		writeBlankImage(oldFrameFile, IMAGE_SIZE, IMAGE_SIZE);
		writeBlankImage(newFrameFile, IMAGE_SIZE, IMAGE_SIZE);

		ObserverLocation location = observerLocationAt(45.0, -75.0);
		ObjectDirectionAltAz oldSunAltAz = sunAltAzAt(oldEpochMillis, location);
		ObjectDirectionAltAz newSunAltAz = sunAltAzAt(newEpochMillis, location);

		CameraConfig cameraConfig = new CameraConfig("backyard-east", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		cameraConfig.setProjection(new RectilinearProjection(50.0));
		// The FIRST (and, at this point, only) entry: aimed exactly at the sun's real position at
		// the OLD frame's timestamp.
		cameraConfig.getCalibrationHistory().append(new CalibrationEntry(0L,
				new Orientation(oldSunAltAz.getAltitude(), oldSunAltAz.getAzimuth(), 0.0), 1.0, 45.0, -75.0));

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		List<ArchiveFrameScanner.Frame> frames = ArchiveFrameScanner.scan(source, cache);
		ArchiveFrameScanner.Frame oldFrame = frameAt(frames, oldEpochMillis);
		ArchiveFrameScanner.Frame newFrame = frameAt(frames, newEpochMillis);

		// A NEW entry, appended AFTER the old frame's calibration already existed, effective
		// partway between the two frames - aimed at the sun's real position at the NEW frame's
		// timestamp instead. If append-only resolution were broken (e.g. always using the latest
		// entry regardless of the query time), the old frame would now render against THIS
		// orientation instead of its own.
		long effectiveFromBetweenTheTwoFrames = oldEpochMillis + (newEpochMillis - oldEpochMillis) / 2;
		cameraConfig.getCalibrationHistory().append(new CalibrationEntry(effectiveFromBetweenTheTwoFrames,
				new Orientation(newSunAltAz.getAltitude(), newSunAltAz.getAzimuth(), 0.0), 1.0, 45.0, -75.0));

		BufferedImage oldResult = BatchReprocessor.reprocessFrame(oldFrame, cameraConfig, defaultOptions());
		BufferedImage newResult = BatchReprocessor.reprocessFrame(newFrame, cameraConfig, defaultOptions());

		int sunColor = rgbNoAlpha(ColorPresets.defaultScheme().getSunColor());
		assertEquals(sunColor, rgbNoAlpha(oldResult, IMAGE_SIZE / 2, IMAGE_SIZE / 2),
				"the OLD frame must still render against its OWN (first-appended) calibration, unaffected by the later append");
		assertEquals(sunColor, rgbNoAlpha(newResult, IMAGE_SIZE / 2, IMAGE_SIZE / 2),
				"the NEW frame must resolve to the newly-appended calibration effective as of its own timestamp");

		// Negative control: rendering the OLD frame's timestamp against the NEW entry's orientation
		// directly must NOT center the sun - proving the two orientations are genuinely different
		// positions, so the assertions above are a real discriminator, not a coincidence.
		BufferedImage oldFrameWithWrongOrientation = FrameCompositor.compose(cameraConfig.getProjection(),
				new Orientation(newSunAltAz.getAltitude(), newSunAltAz.getAzimuth(), 0.0), observationTimeAt(oldEpochMillis),
				location, defaultOptions().setCameraImage(ImageIO.read(oldFrameFile)).setPlacement(ImagePlacement.LAYER_1));
		assertTrue(sunColor != rgbNoAlpha(oldFrameWithWrongOrientation, IMAGE_SIZE / 2, IMAGE_SIZE / 2),
				"sanity check: the NEW entry's orientation must NOT happen to also center the sun for the OLD frame's time");
	}

	@Test
	void rejectsAVirtualCamera(@TempDir File tempDir) throws Exception {
		File frameFile = new File(tempDir, "20240809_000000.jpg");
		writeBlankImage(frameFile, IMAGE_SIZE, IMAGE_SIZE);

		RealImageSource source = RealImageSource.preRecordedOnly(tempDir.getPath() + "/YYYYmmdd_HHMMSS.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		ArchiveFrameScanner.Frame frame = ArchiveFrameScanner.scan(source, cache).get(0);

		CameraConfig virtualCamera = new CameraConfig("virtual-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.useSystemLocale());

		assertThrows(IllegalArgumentException.class,
				() -> BatchReprocessor.reprocessFrame(frame, virtualCamera, defaultOptions()));
	}

	private FrameCompositor.Options defaultOptions() {
		return new FrameCompositor.Options()
				.setStars(Collections.<StarCoordinate>emptyList())
				.setColorScheme(ColorPresets.defaultScheme())
				.setMinSunMoonRadiusPixels(5.0);
	}

	private void writeBlankImage(File file, int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, width, height);
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
		time.initTime(TimeZone.getTimeZone("UTC"));
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

	private ArchiveFrameScanner.Frame frameAt(List<ArchiveFrameScanner.Frame> frames, long epochMillis) {
		for (ArchiveFrameScanner.Frame frame : frames)
			if (frame.getEpochMillis() == epochMillis)
				return frame;
		throw new IllegalStateException("no scanned frame found for epoch " + epochMillis);
	}

	// Item 0's shared-instance CameraAstronomy overload: proves reprocessAll(...) with ONE
	// CameraAstronomy reused across the WHOLE run renders PIXEL-IDENTICAL output to the original
	// always-fresh-construction path, across TWO frames with genuinely different timestamps - the
	// real proof that the shared instance correctly recomputes per frame rather than getting stuck
	// on whichever frame it was first mutated for.
	@Test
	void reprocessAllWithCameraAstronomyMatchesTheOriginalOverloadExactlyAcrossMultipleFrames(@TempDir File tempDir)
			throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		writeBlankImage(new File(cameraRoot, "20240809_000000_1.jpg"), IMAGE_SIZE, IMAGE_SIZE);
		writeBlankImage(new File(cameraRoot, "20240809_060000_1.jpg"), IMAGE_SIZE, IMAGE_SIZE);

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		List<ArchiveFrameScanner.Frame> frames = ArchiveFrameScanner.scan(source, cache);
		assertEquals(2, frames.size());

		CameraConfig cameraConfig = new CameraConfig("multi-frame-cam", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		cameraConfig.setProjection(new RectilinearProjection(50.0));
		cameraConfig.getCalibrationHistory()
				.append(new CalibrationEntry(0L, new Orientation(20.0, 90.0, 0.0), 1.0, 45.0, -75.0));

		List<StarCoordinate> starCatalog = Collections.singletonList(
				new StarCoordinate("Test Star", "TS1", 1.0, 83.822, -5.391, 1, true));

		List<BatchReprocessor.Result> withoutAstronomy = BatchReprocessor.reprocessAll(frames, cameraConfig,
				defaultOptions().setStars(starCatalog).setShowStars(true));
		List<BatchReprocessor.Result> withAstronomy = BatchReprocessor.reprocessAll(frames, cameraConfig,
				defaultOptions().setShowStars(true), starCatalog);

		assertEquals(withoutAstronomy.size(), withAstronomy.size());
		for (int i = 0; i < withoutAstronomy.size(); i++) {
			BufferedImage a = withoutAstronomy.get(i).getImage();
			BufferedImage b = withAstronomy.get(i).getImage();
			assertEquals(a.getWidth(), b.getWidth());
			assertEquals(a.getHeight(), b.getHeight());
			for (int y = 0; y < a.getHeight(); y++)
				for (int x = 0; x < a.getWidth(); x++)
					assertEquals(a.getRGB(x, y), b.getRGB(x, y),
							"frame " + i + " pixel (" + x + "," + y + ") must match between astronomy-backed and original reprocessing");
		}
	}
}
