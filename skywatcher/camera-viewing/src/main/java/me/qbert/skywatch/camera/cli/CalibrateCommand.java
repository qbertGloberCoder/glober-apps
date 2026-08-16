package me.qbert.skywatch.camera.cli;

import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;

import javax.swing.SwingUtilities;

import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.plate.PlateSolveSession;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.source.DirectoryCache;
import me.qbert.skywatch.camera.ui.CalibrationController;
import me.qbert.skywatch.camera.ui.CalibrationWindow;

/*
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

// Task 8.2's launchable entry point for the calibration UI (technique 1's manual plate-solve dial-
// in). Real, Pre-recorded-only, Fixed cameras only - plate.PlateSolveSession itself rejects PTZ at
// construction (see CLAUDE.md's "Orientation editing": PTZ has no Save/Revert concept at all).
//
// --calibration-file is where PlateSolveSession.save()/revert() read and write - it defaults to
// --config when that's given (the common "load my saved camera, tweak it, save back to the same
// file" case needs only one path typed once), but must be given explicitly when building a brand
// new camera from flags (--config absent), since there is nothing to default it from. --target-time
// (an ISO-8601 instant, e.g. 2026-08-09T12:07:00Z) picks which archived frame to preview against -
// technique 1 is a static, one-frame visual comparison, not a live scrub (see CalibrationWindow's
// own class comment) - and defaults to wall-clock "now" if omitted.
//
// Split into buildController(...) (argument parsing/wiring, testable) and run(...) (the thin part
// that shows a window) - matching every other GUI-launching subcommand's own split, for the
// identical HeadlessException reason.
//
// run(...) BLOCKS until the window closes - see PreviewCommand's own class comment for exactly why
// this is required, not optional: cli.Main calls System.exit(...) immediately after run(...)
// returns, and invokeLater(...) alone would let that fire before the EDT ever creates the window.
final class CalibrateCommand {
	static final String USAGE = "Usage: calibrate --config <path> | (--name <camera> --lat <deg> "
			+ "--lon <deg> --alt <deg> --az <deg> [--roll <deg>] --focal-length <mm> "
			+ "[--lens rectilinear|fisheye] [--fisheye-max-angle <deg>] "
			+ "[--barrel-a <n>] [--barrel-b <n>] [--barrel-c <n>] [--barrel-d <n>] --archive-template <template> "
			+ "[--timezone <zoneId>|system]) "
			+ "[--calibration-file <path>] [--target-time <ISO-8601 instant>] "
			+ "[--stars main|named|all|visible] [--min-radius <px>] " + CameraConfigArgs.RENDER_FLAGS_USAGE
			+ " [--cache-dir <dir>] [--cache-scan-limit <n>] [--cache-refresh-interval-seconds <n>]";

	int run(String[] args) throws Exception {
		if (GraphicsEnvironment.isHeadless())
			throw new CliUsageException("calibrate requires a display - this environment is headless");

		CalibrationController controller = buildController(args);
		long targetEpochMillis = targetEpochMillis(new ArgScanner(args));
		CountDownLatch windowClosed = new CountDownLatch(1);

		SwingUtilities.invokeLater(() -> {
			CalibrationWindow window = new CalibrationWindow(controller, targetEpochMillis);
			window.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosed(WindowEvent e) {
					windowClosed.countDown();
				}
			});
			window.setVisible(true);
		});

		windowClosed.await();
		return 0;
	}

	CalibrationController buildController(String[] args) throws Exception {
		ArgScanner scanner = new ArgScanner(args);
		if (!scanner.positionals().isEmpty())
			throw new CliUsageException(USAGE);

		java.util.Optional<String> configPath = scanner.option("config");
		String calibrationFilePath = scanner.option("calibration-file").orElseGet(() -> configPath
				.orElseThrow(() -> new CliUsageException("--calibration-file is required when --config is not given")));

		CameraConfig camera = CameraConfigArgs.buildRealCamera(scanner, RealCaptureMode.PRE_RECORDED_ONLY);
		FrameCompositor.Options options = CameraConfigArgs.buildOptions(scanner, WallClock.SYSTEM);

		PlateSolveSession session = new PlateSolveSession(camera, new File(calibrationFilePath));

		File cacheDir = CameraConfigArgs.cacheDirectory(scanner);
		DirectoryCache cache = new DirectoryCache(cacheDir, CameraConfigArgs.cacheScanLimit(scanner));

		return new CalibrationController(session, cache, options, CameraConfigArgs.cacheRefreshIntervalMillis(scanner));
	}

	private long targetEpochMillis(ArgScanner scanner) {
		java.util.Optional<String> targetTime = scanner.option("target-time");
		if (!targetTime.isPresent())
			return WallClock.SYSTEM.currentTimeMillis();
		try {
			return Instant.parse(targetTime.get()).toEpochMilli();
		} catch (java.time.format.DateTimeParseException e) {
			throw new CliUsageException("invalid --target-time \"" + targetTime.get() + "\" - expected an ISO-8601 instant, e.g. 2026-08-09T12:07:00Z");
		}
	}
}
