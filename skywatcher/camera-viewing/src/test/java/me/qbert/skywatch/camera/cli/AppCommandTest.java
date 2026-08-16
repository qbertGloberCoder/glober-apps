package me.qbert.skywatch.camera.cli;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.ui.AppController;

// Only exercises buildController(...) - the argument-parsing/wiring half of AppCommand - not
// run()'s window-showing half, which constructs a ControlPanel (a JFrame, throwing
// HeadlessException in this module's own display-less test environment - see ControlPanel's own
// class comment).
class AppCommandTest {

	@Test
	void buildsAControllerWithNoActiveCamera(@TempDir File tempDir) throws Exception {
		String[] args = { "--library-dir", new File(tempDir, "library").getPath() };

		AppController controller = new AppCommand().buildController(args);

		assertTrue(controller.listCameraNames().isEmpty());
		assertTrue(!controller.hasActiveCamera());
	}

	@Test
	void rejectsPositionalArguments(@TempDir File tempDir) {
		String[] args = { "unexpected-positional" };

		assertThrows(CliUsageException.class, () -> new AppCommand().buildController(args));
	}

	@Test
	void runRequiresADisplay(@TempDir File tempDir) throws Exception {
		assertTrue(GraphicsEnvironment.isHeadless(), "expected this test environment to be headless");

		String[] args = { "--library-dir", new File(tempDir, "library").getPath() };

		assertThrows(CliUsageException.class, () -> new AppCommand().run(args));
	}
}
