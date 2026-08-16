package me.qbert.skywatch.camera.cli;

import java.io.File;
import java.util.Collections;

import me.qbert.skywatch.camera.batch.LiveWatchDaemon;
import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.render.FrameCompositor;

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

// Task 6.3's live-watch daemon as a CLI invocation: watches one Live+recorded camera's "latest"
// file and re-saves the overlay whenever it changes, forever, until interrupted (Ctrl+C).
//
// Split into buildDaemon(...) (argument parsing + wiring, testable without entering the blocking
// loop) and run(...) (the thin part that actually blocks) - mirroring how LiveWatchDaemon itself
// splits tick() from run(), so argument-handling mistakes are testable the same way the daemon's
// own logic already is, without a test needing to interrupt a real background thread.
final class WatchCommand {
	static final String USAGE = "Usage: watch --config <path> | (--name <camera> --lat <deg> "
			+ "--lon <deg> --alt <deg> --az <deg> [--roll <deg>] --focal-length <mm> "
			+ "[--lens rectilinear|fisheye] [--fisheye-max-angle <deg>] "
			+ "[--barrel-a <n>] [--barrel-b <n>] [--barrel-c <n>] [--barrel-d <n>] --latest <path> "
			+ "--archive-template <template> [--timezone <zoneId>|system]) "
			+ "[--stars main|named|all|visible] [--min-radius <px>] " + CameraConfigArgs.RENDER_FLAGS_USAGE
			+ " --output <path> [--interval-seconds <n>]";

	static final class DaemonSetup {
		final LiveWatchDaemon daemon;
		final long intervalMillis;

		DaemonSetup(LiveWatchDaemon daemon, long intervalMillis) {
			this.daemon = daemon;
			this.intervalMillis = intervalMillis;
		}
	}

	int run(String[] args) throws Exception {
		DaemonSetup setup = buildDaemon(args);

		System.out.println("Watching every " + (setup.intervalMillis / 1000.0) + "s - Ctrl+C to stop.");
		setup.daemon.run(setup.intervalMillis, () -> Thread.currentThread().isInterrupted());

		return 0;
	}

	DaemonSetup buildDaemon(String[] args) throws Exception {
		ArgScanner scanner = new ArgScanner(args);
		if (!scanner.positionals().isEmpty())
			throw new CliUsageException(USAGE);

		CameraConfig camera = CameraConfigArgs.buildRealCamera(scanner, RealCaptureMode.LIVE_AND_RECORDED);
		FrameCompositor.Options options = CameraConfigArgs.buildOptions(scanner, WallClock.SYSTEM);
		File outputFile = new File(scanner.requireOption("output"));
		long intervalMillis = (long) (scanner.doubleOption("interval-seconds", 60.0) * 1000.0);

		LiveWatchDaemon.Watch watch = new LiveWatchDaemon.Watch(camera, outputFile);
		LiveWatchDaemon daemon = new LiveWatchDaemon(Collections.singletonList(watch), options, WallClock.SYSTEM);

		return new DaemonSetup(daemon, intervalMillis);
	}
}
