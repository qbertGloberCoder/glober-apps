package me.qbert.skywatch.camera.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraConfigStore;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

class ReprocessCommandTest {

	private static final int IMAGE_SIZE = 100;
	private static final long FRAME_EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

	@Test
	void reprocessesAWholeArchiveEndToEndFromCliArguments(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		File frameFile = new File(cameraRoot, "20240809_000000_1.jpg");
		writeBlankImage(frameFile);

		ObjectDirectionAltAz sunAltAz = sunAltAzAt(FRAME_EPOCH_MILLIS, 45.0, -75.0);
		File outputDir = new File(tempDir, "output");

		String[] args = {
				"--name", "backyard",
				"--lat", "45.0", "--lon", "-75.0",
				"--alt", String.valueOf(sunAltAz.getAltitude()), "--az", String.valueOf(sunAltAz.getAzimuth()),
				"--focal-length", "50.0",
				"--archive-template", cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--output-dir", outputDir.getPath()
		};

		int exitCode = new ReprocessCommand().run(args);

		assertEquals(0, exitCode);
		File outputFile = new File(outputDir, frameFile.getName());
		assertTrue(outputFile.isFile(), "expected the reprocessed frame to keep its original filename");

		// reprocess deliberately keeps the source frame's own filename/extension (a real archive is
		// typically JPEG - see the user's own worked examples), so the written-then-read-back pixel
		// isn't byte-identical to the in-memory composited color - JPEG's lossy compression shifts
		// it by a few quantization levels around the disc's edge. Assert per-channel closeness
		// rather than exact equality (see LiveCameraSaverTest for the same artifact, avoided there
		// by using a PNG output instead - not an option here, since preserving the source format is
		// this command's actual intended behavior).
		BufferedImage written = ImageIO.read(outputFile);
		assertColorsClose(ColorPresets.defaultScheme().getSunColor(), new Color(written.getRGB(IMAGE_SIZE / 2, IMAGE_SIZE / 2)));
	}

	private void assertColorsClose(Color expected, Color actual) {
		int tolerance = 20;
		assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= tolerance
				&& Math.abs(expected.getGreen() - actual.getGreen()) <= tolerance
				&& Math.abs(expected.getBlue() - actual.getBlue()) <= tolerance,
				"expected a color close to " + expected + " but was " + actual);
	}

	@Test
	void reprocessesUsingASavedConfigFileInsteadOfIndividualCameraFlags(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		File frameFile = new File(cameraRoot, "20240809_000000_1.jpg");
		writeBlankImage(frameFile);

		ObjectDirectionAltAz sunAltAz = sunAltAzAt(FRAME_EPOCH_MILLIS, 45.0, -75.0);

		// Pre-recorded-only, matching what reprocess actually needs (unlike save-latest/watch's
		// Live+recorded profiles tested elsewhere) - covers both RealCaptureMode branches of
		// CameraConfigArgs.loadFromConfigFile(...) across the CLI test suite as a whole.
		CameraConfig profile = new CameraConfig("backyard", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		profile.setProjection(new RectilinearProjection(50.0));
		profile.getCalibrationHistory().append(new CalibrationEntry(0L,
				new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0), 50.0, 45.0, -75.0));
		profile.setRealImageSource(RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(java.time.ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD));
		File configFile = new File(tempDir, "backyard.properties");
		CameraConfigStore.save(profile, configFile);

		File outputDir = new File(tempDir, "output");
		String[] args = { "--config", configFile.getPath(), "--output-dir", outputDir.getPath() };

		int exitCode = new ReprocessCommand().run(args);

		assertEquals(0, exitCode);
		File outputFile = new File(outputDir, frameFile.getName());
		assertTrue(outputFile.isFile());
		BufferedImage written = ImageIO.read(outputFile);
		assertColorsClose(ColorPresets.defaultScheme().getSunColor(), new Color(written.getRGB(IMAGE_SIZE / 2, IMAGE_SIZE / 2)));
	}

	@Test
	void anEmptyArchiveWritesNothingButStillSucceeds(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		File outputDir = new File(tempDir, "output");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--archive-template", cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				"--output-dir", outputDir.getPath()
		};

		int exitCode = new ReprocessCommand().run(args);

		assertEquals(0, exitCode);
		assertEquals(0, outputDir.listFiles((dir, name) -> !name.equals(".cache")).length);
	}

	private void writeBlankImage(File file) throws IOException {
		BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
		g2d.dispose();
		ImageIO.write(image, "jpg", file);
	}

	private ObjectDirectionAltAz sunAltAzAt(long epochMillis, double latitude, double longitude) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);

		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);

		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		return sun.getCurrentDirectionAsAltitudeAzimuth();
	}
}
