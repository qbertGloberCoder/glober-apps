package me.qbert.skywatch.camera.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.ui.PreviewController;

// Only exercises buildController(...) - the argument-parsing/wiring half of PreviewCommand - not
// run()'s actual window-showing half, which constructs a PreviewWindow (a JFrame, throwing
// HeadlessException in this module's own display-less test environment - see PreviewWindow's own
// class comment). That headlessness is exploited directly below instead of worked around: this
// sandbox genuinely has no display, so run()'s early GraphicsEnvironment.isHeadless() check is
// itself exercised for real here, not simulated.
class PreviewCommandTest {

	@Test
	void buildsAControllerFromValidArguments(@TempDir File tempDir) throws Exception {
		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg"
		};

		PreviewController controller = new PreviewCommand().buildController(args);

		assertEquals("backyard", controller.getCameraConfig().getName());
		assertEquals(900, controller.getCanvasWidthPixels(), "default width");
		assertEquals(700, controller.getCanvasHeightPixels(), "default height");
		assertTrue(controller.isImageShown());
		assertTrue(controller.getClock().isPlaying(), "play is the live-preview default");
	}

	@Test
	void widthAndHeightFlagsOverrideTheDefaults(@TempDir File tempDir) throws Exception {
		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--width", "1280", "--height", "720"
		};

		PreviewController controller = new PreviewCommand().buildController(args);

		assertEquals(1280, controller.getCanvasWidthPixels());
		assertEquals(720, controller.getCanvasHeightPixels());
	}

	@Test
	void rejectsPositionalArguments(@TempDir File tempDir) {
		String[] args = { "unexpected-positional" };

		assertThrows(CliUsageException.class, () -> new PreviewCommand().buildController(args));
	}

	@Test
	void runRequiresADisplay(@TempDir File tempDir) throws Exception {
		// This module's own test environment genuinely has no display (confirmed while building
		// this round) - so this isn't a simulated/mocked headless check, it's the real thing.
		assertTrue(GraphicsEnvironment.isHeadless(), "expected this test environment to be headless");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg"
		};

		assertThrows(CliUsageException.class, () -> new PreviewCommand().run(args));
	}
}
