package me.qbert.skywatch.camera.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.Arrays;
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
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

class LiveWatchDaemonTest {

	private static final int IMAGE_SIZE = 100;
	private static final long NOW_EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z
	private static final WallClock FIXED_CLOCK = () -> NOW_EPOCH_MILLIS;

	@Test
	void firstTickAlwaysSavesEvenWithoutAChange(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile, 1_000_000_000L);

		CameraConfig camera = readyCamera("cam1", tempDir);
		LiveWatchDaemon.Watch watch = new LiveWatchDaemon.Watch(camera, new File(tempDir, "cam1-out.png"));
		LiveWatchDaemon daemon = new LiveWatchDaemon(Collections.singletonList(watch),
				defaultOptions(), FIXED_CLOCK);

		LiveWatchDaemon.TickResult result = daemon.tick();

		assertEquals(1, result.getSaved().size());
		assertTrue(result.getFailed().isEmpty());
		assertTrue(watch.getOutputFile().isFile());
	}

	@Test
	void secondTickWithNoMtimeChangeDoesNotResave(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile, 1_000_000_000L);

		CameraConfig camera = readyCamera("cam1", tempDir);
		LiveWatchDaemon.Watch watch = new LiveWatchDaemon.Watch(camera, new File(tempDir, "cam1-out.png"));
		LiveWatchDaemon daemon = new LiveWatchDaemon(Collections.singletonList(watch),
				defaultOptions(), FIXED_CLOCK);

		daemon.tick();
		LiveWatchDaemon.TickResult second = daemon.tick();

		assertTrue(second.getSaved().isEmpty(), "an unchanged latest file must not trigger a re-save");
		assertTrue(second.getFailed().isEmpty());
	}

	@Test
	void aChangedMtimeTriggersARenewedSave(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile, 1_000_000_000L);

		CameraConfig camera = readyCamera("cam1", tempDir);
		LiveWatchDaemon.Watch watch = new LiveWatchDaemon.Watch(camera, new File(tempDir, "cam1-out.png"));
		LiveWatchDaemon daemon = new LiveWatchDaemon(Collections.singletonList(watch),
				defaultOptions(), FIXED_CLOCK);

		daemon.tick();
		// Simulate a new frame arriving - explicit setLastModified rather than relying on real
		// filesystem timing, so this is deterministic regardless of the host's mtime granularity.
		writeBlankImage(latestFile, 1_000_060_000L);

		LiveWatchDaemon.TickResult second = daemon.tick();

		assertEquals(1, second.getSaved().size(), "a genuinely changed mtime must trigger a fresh save");
	}

	@Test
	void aCameraWithNoLiveSourceConfiguredIsSkippedNotErrored(@TempDir File tempDir) {
		CameraConfig unconfigured = new CameraConfig("unconfigured", CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		// Deliberately no setRealImageSource(...) call - matches "unconfigured until explicitly set".
		LiveWatchDaemon.Watch watch = new LiveWatchDaemon.Watch(unconfigured, new File(tempDir, "out.png"));
		LiveWatchDaemon daemon = new LiveWatchDaemon(Collections.singletonList(watch),
				defaultOptions(), FIXED_CLOCK);

		LiveWatchDaemon.TickResult result = daemon.tick();

		assertTrue(result.getSaved().isEmpty());
		assertTrue(result.getFailed().isEmpty(), "an unconfigured camera should be silently skipped, not treated as a failure");
	}

	@Test
	void aVirtualCameraWatchIsSkippedNotErrored(@TempDir File tempDir) {
		CameraConfig virtualCamera = new CameraConfig("virtual-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.useSystemLocale());
		LiveWatchDaemon.Watch watch = new LiveWatchDaemon.Watch(virtualCamera, new File(tempDir, "out.png"));
		LiveWatchDaemon daemon = new LiveWatchDaemon(Collections.singletonList(watch),
				defaultOptions(), FIXED_CLOCK);

		LiveWatchDaemon.TickResult result = daemon.tick();

		assertTrue(result.getSaved().isEmpty());
		assertTrue(result.getFailed().isEmpty());
	}

	@Test
	void oneCamerasFailureDoesNotPreventOthersInTheSameTick(@TempDir File tempDir) throws Exception {
		File latestFile1 = new File(tempDir, "cam1-latest.jpg");
		File latestFile2 = new File(tempDir, "cam2-latest.jpg");
		writeBlankImage(latestFile1, 1_000_000_000L);
		writeBlankImage(latestFile2, 1_000_000_000L);

		CameraConfig goodCamera = readyCamera("cam1", latestFile1, tempDir);

		// A camera with a live source configured, but no calibration entry at all - saveLatest must
		// fail for this one (no calibration covers any time).
		CameraConfig brokenCamera = new CameraConfig("cam2", CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		brokenCamera.setProjection(new RectilinearProjection(50.0));
		brokenCamera.setRealImageSource(RealImageSource.liveAndRecorded(latestFile2.getPath(),
				tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg", TimezoneSetting.explicit(ZoneOffset.UTC),
				DstAmbiguousPolicy.ASSUME_STANDARD));

		LiveWatchDaemon.Watch goodWatch = new LiveWatchDaemon.Watch(goodCamera, new File(tempDir, "cam1-out.png"));
		LiveWatchDaemon.Watch brokenWatch = new LiveWatchDaemon.Watch(brokenCamera, new File(tempDir, "cam2-out.png"));
		LiveWatchDaemon daemon = new LiveWatchDaemon(Arrays.asList(goodWatch, brokenWatch),
				defaultOptions(), FIXED_CLOCK);

		LiveWatchDaemon.TickResult result = daemon.tick();

		assertEquals(1, result.getSaved().size());
		assertTrue(result.getSaved().contains(goodCamera));
		assertEquals(1, result.getFailed().size());
		assertTrue(result.getFailed().containsKey(brokenCamera));
	}

	@Test
	void runLoopsUntilStopRequested(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile, 1_000_000_000L);

		CameraConfig camera = readyCamera("cam1", tempDir);
		LiveWatchDaemon.Watch watch = new LiveWatchDaemon.Watch(camera, new File(tempDir, "cam1-out.png"));
		LiveWatchDaemon daemon = new LiveWatchDaemon(Collections.singletonList(watch),
				defaultOptions(), FIXED_CLOCK);

		int[] tickCount = { 0 };
		daemon.run(0L, () -> {
			tickCount[0]++;
			return tickCount[0] >= 3;
		});

		assertEquals(3, tickCount[0], "the loop should call the stop check exactly as many times as it ran");
	}

	private FrameCompositor.Options defaultOptions() {
		return new FrameCompositor.Options()
				.setStars(Collections.<StarCoordinate>emptyList())
				.setColorScheme(ColorPresets.defaultScheme())
				.setMinSunMoonRadiusPixels(5.0);
	}

	private CameraConfig readyCamera(String name, File tempDir) throws Exception {
		return readyCamera(name, new File(tempDir, "latest.jpg"), tempDir);
	}

	private CameraConfig readyCamera(String name, File latestFile, File tempDir) throws Exception {
		ObservationTime time = observationTimeAt(NOW_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();

		CameraConfig camera = new CameraConfig(name, CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L,
				new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0), 1.0, 45.0, -75.0));
		camera.setRealImageSource(RealImageSource.liveAndRecorded(latestFile.getPath(),
				tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg", TimezoneSetting.explicit(ZoneOffset.UTC),
				DstAmbiguousPolicy.ASSUME_STANDARD));
		return camera;
	}

	private void writeBlankImage(File file, long lastModifiedMillis) throws IOException {
		BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
		g2d.dispose();
		ImageIO.write(image, "jpg", file);
		file.setLastModified(lastModifiedMillis);
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
