package me.qbert.skywatch.camera.cli;

import java.io.File;

import me.qbert.skywatch.camera.batch.LiveCameraSaver;
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

// Task 6.4's "save-cameras" as a single one-shot CLI invocation: render the latest overlaid frame
// for a Live+recorded camera once and write it out.
final class SaveLatestCommand {
	static final String USAGE = "Usage: save-latest --config <path> | (--name <camera> --lat <deg> "
			+ "--lon <deg> --alt <deg> --az <deg> [--roll <deg>] --focal-length <mm> "
			+ "[--lens rectilinear|fisheye] [--fisheye-max-angle <deg>] "
			+ "[--barrel-a <n>] [--barrel-b <n>] [--barrel-c <n>] [--barrel-d <n>] --latest <path> "
			+ "--archive-template <template> [--timezone <zoneId>|system]) "
			+ "[--stars main|named|all|visible] [--min-radius <px>] " + CameraConfigArgs.RENDER_FLAGS_USAGE
			+ " --output <path>";

	int run(String[] args) throws Exception {
		ArgScanner scanner = new ArgScanner(args);
		if (!scanner.positionals().isEmpty())
			throw new CliUsageException(USAGE);

		CameraConfig camera = CameraConfigArgs.buildRealCamera(scanner, RealCaptureMode.LIVE_AND_RECORDED);
		FrameCompositor.Options options = CameraConfigArgs.buildOptions(scanner, WallClock.SYSTEM);
		File outputFile = new File(scanner.requireOption("output"));

		LiveCameraSaver.saveLatest(camera, options, WallClock.SYSTEM, outputFile);

		System.out.println("Wrote " + outputFile);
		return 0;
	}
}
