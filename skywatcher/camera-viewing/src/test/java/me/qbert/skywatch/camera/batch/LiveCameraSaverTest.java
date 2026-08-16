package me.qbert.skywatch.camera.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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

class LiveCameraSaverTest {

	private static final int IMAGE_SIZE = 200;
	private static final long NOW_EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

	@Test
	void savesTheLatestFrameWithTheSunBurnedOntoItAtWallClockNow(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile, IMAGE_SIZE, IMAGE_SIZE);

		ObservationTime time = observationTimeAt(NOW_EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();

		CameraConfig cameraConfig = new CameraConfig("driveway", CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		cameraConfig.setProjection(new RectilinearProjection(50.0));
		cameraConfig.getCalibrationHistory().append(new CalibrationEntry(0L,
				new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0), 1.0, 45.0, -75.0));
		cameraConfig.setRealImageSource(RealImageSource.liveAndRecorded(latestFile.getPath(),
				tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg", TimezoneSetting.explicit(java.time.ZoneOffset.UTC),
				DstAmbiguousPolicy.ASSUME_STANDARD));

		// PNG, not JPEG, for the output - JPEG's lossy compression would otherwise shift the sun's
		// exact color by a few quantization levels around the disc's edge, defeating an exact-color
		// assertion after reading the file back.
		File outputFile = new File(tempDir, "driveway-overlay.png");
		WallClock fixedClock = () -> NOW_EPOCH_MILLIS;

		LiveCameraSaver.saveLatest(cameraConfig, defaultOptions(), fixedClock, outputFile);

		assertTrue(outputFile.isFile(), "expected an output file to be written");
		BufferedImage written = ImageIO.read(outputFile);
		assertEquals(IMAGE_SIZE, written.getWidth());
		assertEquals(IMAGE_SIZE, written.getHeight());
		assertEquals(rgbNoAlpha(ColorPresets.defaultScheme().getSunColor()),
				rgbNoAlpha(written, IMAGE_SIZE / 2, IMAGE_SIZE / 2),
				"the sun should be burned onto the saved output at its own computed position");
	}

	@Test
	void rejectsAPreRecordedOnlyCameraWithNoLatestSource(@TempDir File tempDir) {
		CameraConfig preRecordedOnly = new CameraConfig("archive-only", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		preRecordedOnly.setProjection(new RectilinearProjection(50.0));
		preRecordedOnly.setRealImageSource(RealImageSource.preRecordedOnly(tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(java.time.ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD));

		File outputFile = new File(tempDir, "out.jpg");
		WallClock fixedClock = () -> NOW_EPOCH_MILLIS;

		assertThrows(IllegalStateException.class,
				() -> LiveCameraSaver.saveLatest(preRecordedOnly, defaultOptions(), fixedClock, outputFile));
	}

	@Test
	void rejectsAVirtualCamera(@TempDir File tempDir) {
		CameraConfig virtualCamera = new CameraConfig("virtual-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.useSystemLocale());

		File outputFile = new File(tempDir, "out.jpg");
		WallClock fixedClock = () -> NOW_EPOCH_MILLIS;

		assertThrows(IllegalStateException.class,
				() -> LiveCameraSaver.saveLatest(virtualCamera, defaultOptions(), fixedClock, outputFile));
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
}
