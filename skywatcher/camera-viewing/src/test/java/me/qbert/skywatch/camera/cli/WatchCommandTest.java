package me.qbert.skywatch.camera.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;

// Only exercises buildDaemon(...) - the argument-parsing/wiring half of WatchCommand - not the
// actual blocking run() loop, which would require interrupting a real background thread to test
// and is already covered at the LiveWatchDaemon level (see LiveWatchDaemonTest.runLoopsUntilStopRequested).
class WatchCommandTest {

	@Test
	void buildsADaemonFromValidArguments(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		latestFile.createNewFile();

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(), "--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--output", new File(tempDir, "out.png").getPath(),
				"--interval-seconds", "30"
		};

		WatchCommand.DaemonSetup setup = new WatchCommand().buildDaemon(args);

		assertNotNull(setup.daemon);
		assertEquals(30_000L, setup.intervalMillis);
	}

	@Test
	void defaultIntervalIsSixtySeconds(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		latestFile.createNewFile();

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(), "--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--output", new File(tempDir, "out.png").getPath()
		};

		WatchCommand.DaemonSetup setup = new WatchCommand().buildDaemon(args);

		assertEquals(60_000L, setup.intervalMillis);
	}

	@Test
	void buildsADaemonFromASavedConfigFile(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		latestFile.createNewFile();

		CameraConfig profile = new CameraConfig("backyard", CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		profile.setProjection(new RectilinearProjection(50.0));
		profile.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(10.0, 90.0, 0.0), 50.0, 45.0, -75.0));
		profile.setRealImageSource(RealImageSource.liveAndRecorded(latestFile.getPath(),
				tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg", TimezoneSetting.explicit(ZoneOffset.UTC),
				DstAmbiguousPolicy.ASSUME_STANDARD));
		File configFile = new File(tempDir, "backyard.properties");
		CameraConfigStore.save(profile, configFile);

		String[] args = {
				"--config", configFile.getPath(),
				"--output", new File(tempDir, "out.png").getPath(),
				"--interval-seconds", "45"
		};

		WatchCommand.DaemonSetup setup = new WatchCommand().buildDaemon(args);

		assertNotNull(setup.daemon);
		assertEquals(45_000L, setup.intervalMillis);
	}

	@Test
	void missingRequiredFlagProducesAUsageError(@TempDir File tempDir) {
		String[] args = { "--lat", "45.0" };

		assertThrows(CliUsageException.class, () -> new WatchCommand().buildDaemon(args));
	}
}
