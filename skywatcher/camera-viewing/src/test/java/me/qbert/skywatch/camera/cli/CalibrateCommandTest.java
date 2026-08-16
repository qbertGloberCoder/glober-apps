package me.qbert.skywatch.camera.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.ui.CalibrationController;

// Only exercises buildController(...) - the argument-parsing/wiring half of CalibrateCommand - not
// run()'s window-showing half, which constructs a CalibrationWindow (a JFrame, throwing
// HeadlessException in this module's own display-less test environment - see CalibrationWindow's
// own class comment).
class CalibrateCommandTest {

	@Test
	void buildsAControllerFromFlagsWithCalibrationFileDefaultedFromConfig(@TempDir File tempDir) throws Exception {
		File configFile = new File(tempDir, "backyard.properties");
		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--calibration-file", configFile.getPath()
		};

		CalibrationController controller = new CalibrateCommand().buildController(args);

		assertEquals("backyard", controller.getSession().getCameraConfig().getName());
	}

	@Test
	void calibrationFileDefaultsToConfigWhenNotGivenSeparately(@TempDir File tempDir) throws Exception {
		// Build and save a profile first (matching the "load my saved camera, tweak it" workflow).
		File configFile = new File(tempDir, "backyard.properties");
		String[] buildArgs = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--calibration-file", configFile.getPath()
		};
		new CalibrateCommand().buildController(buildArgs).save(0L);

		String[] args = { "--config", configFile.getPath() };

		CalibrationController controller = new CalibrateCommand().buildController(args);

		assertEquals("backyard", controller.getSession().getCameraConfig().getName());
	}

	@Test
	void rejectsMissingCalibrationFileWhenConfigIsAbsent(@TempDir File tempDir) {
		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg"
		};

		assertThrows(CliUsageException.class, () -> new CalibrateCommand().buildController(args));
	}

	@Test
	void rejectsPositionalArguments() {
		assertThrows(CliUsageException.class, () -> new CalibrateCommand().buildController(new String[] { "oops" }));
	}

	@Test
	void runRequiresADisplay(@TempDir File tempDir) throws Exception {
		assertTrue(GraphicsEnvironment.isHeadless(), "expected this test environment to be headless");

		File configFile = new File(tempDir, "backyard.properties");
		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--calibration-file", configFile.getPath()
		};

		assertThrows(CliUsageException.class, () -> new CalibrateCommand().run(args));
	}
}
