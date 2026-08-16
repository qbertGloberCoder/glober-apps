package me.qbert.skywatch.camera.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraLibrary;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;
import me.qbert.skywatch.camera.config.VirtualImageSource;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;

// The new "cache-update" subcommand - a deliberate, explicit, unlimited-scan synchronization
// action, separate from the interactive commands' limited/circuit-broken cache (see CLAUDE.md's
// "Local file cache" section for the full story behind this). Console output is captured via an
// injected PrintStream (run(args, out)) rather than System.out, matching this module's established
// testability-seam convention.
class CacheUpdateCommandTest {

	@Test
	void synchronizesARealCamerasArchiveAndPrintsProgress(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		File day = new File(cameraRoot, "2026/08/09");
		day.mkdirs();
		writeBlankImage(new File(day, "20260809_120000_1.jpg"));
		writeBlankImage(new File(day, "20260809_120100_1.jpg"));

		File libraryDir = new File(tempDir, "library");
		File cacheDir = new File(tempDir, "cache");
		new CameraLibrary(libraryDir).save("backyard", realCamera(cameraRoot));

		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		int exitCode = new CacheUpdateCommand().run(
				new String[] { "--camera", "backyard", "--library-dir", libraryDir.getPath(), "--cache-dir", cacheDir.getPath() },
				new PrintStream(captured, true, "UTF-8"));

		assertEquals(0, exitCode);
		String output = captured.toString(StandardCharsets.UTF_8.name());
		assertTrue(output.contains("Scanning archive for \"backyard\""), "expected a start-of-scan message");
		assertTrue(output.contains("Found 2 archived frames"), "expected the final frame count in the summary");

		// The cache root now actually holds real partition files - a real, on-disk side effect, not
		// just a printed claim.
		assertTrue(cacheDir.isDirectory());
		assertTrue(cacheDir.list().length > 0, "expected at least one cache partition file to have been written");
	}

	@Test
	void missingCameraFlagIsAUsageError(@TempDir File tempDir) {
		assertThrows(CliUsageException.class,
				() -> new CacheUpdateCommand().run(new String[] { "--library-dir", tempDir.getPath() }, System.out));
	}

	@Test
	void unknownCameraNameIsAUsageError(@TempDir File tempDir) {
		File libraryDir = new File(tempDir, "library");
		libraryDir.mkdirs();

		assertThrows(CliUsageException.class, () -> new CacheUpdateCommand()
				.run(new String[] { "--camera", "nobody", "--library-dir", libraryDir.getPath() }, System.out));
	}

	@Test
	void virtualCameraIsRejected(@TempDir File tempDir) throws IOException {
		File libraryDir = new File(tempDir, "library");
		CameraConfig virtualCamera = new CameraConfig("pano",
				CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360), ObserverLocationSetting.useSystemLocale());
		new CameraLibrary(libraryDir).save("pano", virtualCamera);

		CliUsageException thrown = assertThrows(CliUsageException.class, () -> new CacheUpdateCommand()
				.run(new String[] { "--camera", "pano", "--library-dir", libraryDir.getPath() }, System.out));
		assertTrue(thrown.getMessage().contains("Virtual"));
	}

	private CameraConfig realCamera(File cameraRoot) {
		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		CameraConfig camera = new CameraConfig("backyard", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setRealImageSource(source);
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 1.0, 45.0, -75.0));
		return camera;
	}

	private void writeBlankImage(File file) throws IOException {
		ImageIO.write(new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", file);
	}
}
