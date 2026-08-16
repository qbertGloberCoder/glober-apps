package me.qbert.skywatch.camera.cli;

import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.concurrent.CountDownLatch;

import javax.swing.SwingUtilities;

import me.qbert.skywatch.camera.clock.SimulatedClock;
import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.source.DirectoryCache;
import me.qbert.skywatch.camera.ui.PreviewController;
import me.qbert.skywatch.camera.ui.PreviewWindow;

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

// Task 8.1's launchable entry point for the interactive preview window. Real, Pre-recorded-only
// cameras only for now - matches ReprocessCommand's own camera-type choice; the window's step-back/
// step-forward scrubbing needs an archive to scrub through, the same reasoning task 4.7's
// RealCameraScrubber was built around.
//
// Split into buildController(...) (argument parsing/wiring, testable) and run(...) (the thin part
// that actually shows a window) - matching WatchCommand's own buildDaemon()/run() split, for the
// same underlying reason: constructing a PreviewWindow throws HeadlessException in a display-less
// environment, so keeping the argument-handling half separately testable is what lets any of this
// be verified by this module's own automated test suite at all.
//
// run(...) BLOCKS until the window closes, matching WatchCommand's own "block until interrupted"
// shape - necessary, not just a style choice: cli.Main calls System.exit(...) immediately after
// run(...) returns, and SwingUtilities.invokeLater(...) is asynchronous, so returning right after
// scheduling the window (a first draft's bug, caught by the user actually running this) let
// System.exit(0) fire before the EDT ever got to create it - the window never appeared, the process
// just exited. A CountDownLatch released by a windowClosed listener is what fixes that.
final class PreviewCommand {
	static final String USAGE = "Usage: preview --config <path> | (--name <camera> --lat <deg> "
			+ "--lon <deg> --alt <deg> --az <deg> [--roll <deg>] --focal-length <mm> "
			+ "[--lens rectilinear|fisheye] [--fisheye-max-angle <deg>] "
			+ "[--barrel-a <n>] [--barrel-b <n>] [--barrel-c <n>] [--barrel-d <n>] --archive-template <template> "
			+ "[--timezone <zoneId>|system]) "
			+ "[--stars main|named|all|visible] [--min-radius <px>] " + CameraConfigArgs.RENDER_FLAGS_USAGE
			+ " [--width <px>] [--height <px>] [--cache-dir <dir>] [--cache-scan-limit <n>] "
			+ "[--cache-refresh-interval-seconds <n>]";

	int run(String[] args) throws Exception {
		if (GraphicsEnvironment.isHeadless())
			throw new CliUsageException("preview requires a display - this environment is headless");

		PreviewController controller = buildController(args);
		CountDownLatch windowClosed = new CountDownLatch(1);

		SwingUtilities.invokeLater(() -> {
			PreviewWindow window = new PreviewWindow(controller);
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

	PreviewController buildController(String[] args) throws Exception {
		ArgScanner scanner = new ArgScanner(args);
		if (!scanner.positionals().isEmpty())
			throw new CliUsageException(USAGE);

		CameraConfig camera = CameraConfigArgs.buildRealCamera(scanner, RealCaptureMode.PRE_RECORDED_ONLY);
		FrameCompositor.Options options = CameraConfigArgs.buildOptions(scanner, WallClock.SYSTEM);

		int width = (int) scanner.doubleOption("width", 900.0);
		int height = (int) scanner.doubleOption("height", 700.0);

		File cacheDir = CameraConfigArgs.cacheDirectory(scanner);
		DirectoryCache cache = new DirectoryCache(cacheDir, CameraConfigArgs.cacheScanLimit(scanner));

		SimulatedClock clock = new SimulatedClock();

		return new PreviewController(camera, cache, options, clock, width, height,
				CameraConfigArgs.cacheRefreshIntervalMillis(scanner));
	}
}
