package me.qbert.skywatch.camera.cli;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CommandDispatcherTest {

	@Test
	void emptyArgsLaunchesAppModeNotAUsageError() {
		// No longer "a usage error" (direct user instruction: empty args is now app mode's own
		// entry point) - this environment is genuinely headless, so it fails on AppCommand's own
		// "requires a display" check, not "unknown subcommand" or a missing-flag complaint.
		CliUsageException exception = assertThrows(CliUsageException.class, () -> new CommandDispatcher().run(new String[0]));

		org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("display"),
				"expected the headless/display complaint, got: " + exception.getMessage());
	}

	@Test
	void unknownSubcommandIsAUsageError() {
		assertThrows(CliUsageException.class, () -> new CommandDispatcher().run(new String[] { "frobnicate" }));
	}

	@Test
	void aKnownSubcommandWithBadArgumentsStillReachesItsOwnUsageError() {
		// Confirms dispatch actually reaches SaveLatestCommand (a missing --name there produces its
		// own usage complaint, not "unknown subcommand").
		CliUsageException exception = assertThrows(CliUsageException.class,
				() -> new CommandDispatcher().run(new String[] { "save-latest" }));

		org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("--name"),
				"expected the missing --name complaint, got: " + exception.getMessage());
	}

	@Test
	void previewSubcommandReachesPreviewCommand() {
		// Confirms dispatch actually reaches PreviewCommand: with valid camera flags but no display
		// (this test environment is genuinely headless - see PreviewCommandTest), it fails on the
		// "requires a display" check rather than "unknown subcommand" or a missing-flag complaint.
		CliUsageException exception = assertThrows(CliUsageException.class, () -> new CommandDispatcher().run(
				new String[] { "preview", "--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0",
						"--az", "90.0", "--focal-length", "50.0", "--archive-template", "/tmp/**/YYYYmmdd_HHMMSS*.jpg" }));

		org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("display"),
				"expected the headless/display complaint, got: " + exception.getMessage());
	}

	@Test
	void cacheUpdateSubcommandReachesCacheUpdateCommand() {
		// cache-update is headless-friendly (no window), so a missing --camera surfaces
		// CacheUpdateCommand's own usage complaint rather than "unknown subcommand" or a
		// headless/display complaint.
		CliUsageException exception = assertThrows(CliUsageException.class,
				() -> new CommandDispatcher().run(new String[] { "cache-update" }));

		org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("--camera"),
				"expected the missing --camera complaint, got: " + exception.getMessage());
	}

	@Test
	void calibrateSubcommandReachesCalibrateCommand() {
		// Same reasoning as previewSubcommandReachesPreviewCommand: this environment is genuinely
		// headless, so a valid-but-headless invocation surfaces CalibrateCommand's own "requires a
		// display" complaint rather than "unknown subcommand".
		CliUsageException exception = assertThrows(CliUsageException.class, () -> new CommandDispatcher().run(
				new String[] { "calibrate", "--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0",
						"--az", "90.0", "--focal-length", "50.0", "--archive-template", "/tmp/**/YYYYmmdd_HHMMSS*.jpg",
						"--calibration-file", "/tmp/backyard.properties" }));

		org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("display"),
				"expected the headless/display complaint, got: " + exception.getMessage());
	}
}
